package com.rndeveloper.paparcar.detection

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService
import com.rndeveloper.paparcar.domain.service.GeofenceEvent
import com.rndeveloper.paparcar.domain.service.GeofenceEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class GeofenceManagerImpl(
    private val context: Context,
    private val geofencingClient: GeofencingClient,
    private val geofenceEventBus: GeofenceEventBus,
) : GeofenceManager {

    @SuppressLint("MissingPermission")
    override suspend fun createGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit> = runCatching {
        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(latitude, longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(LOITERING_DELAY_MS)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(NO_INITIAL_TRIGGER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(request, buildPendingIntent()).await()

        // [DET-FENCE-REREGISTER-BY-CAUSE-001 §A] Record it HERE, where every registration path
        // converges — the initial create on park, the location edit, the vehicle swap, and both
        // restoration lanes. Recording at the call sites instead would mean four places to forget,
        // and a fence created seconds ago would keep getting re-registered by the janitor (the
        // 2026-07-12 Glorieta failure mode, where the blind window opened right before drive-off).
        // Only reached when addGeofences actually resolved: a failure throws past this line.
        FenceRegistrationLedger.recordSuccess(geofenceId, System.currentTimeMillis())

        registerAuxiliaryFences(geofenceId, latitude, longitude, radiusMeters)
    }

    /**
     * Best-effort registration of the two auxiliary fences. Only the REAL EXIT fence above
     * decides the [createGeofence] Result: failing the whole registration because the
     * return-anchor twin or the diagnostics-only witness was rejected would tell every caller
     * "geofence creation failed" while the departure trigger is in fact armed. Each failure is
     * logged; the periodic safety-net cure retries them on the next re-registration.
     */
    @SuppressLint("MissingPermission")
    private suspend fun registerAuxiliaryFences(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ) {
        // [DET-RETURN-ANCHOR-001] Twin ENTER fence over the same region. The typical parking
        // session is park → walk away → return HOURS later → drive off: by then the position
        // anchor has long expired and the EXIT fence's internal state may sit poisoned OUTSIDE
        // (consumed by the earlier walking-EXIT, the brief re-entry unobserved in Doze) — the
        // drive-away then produces NO signal at all (field 2026-07-07, both devices: the beach
        // spot was never released). The user's walk BACK into the fence is the one moment that
        // fixes both: this ENTER rides a PendingIntent (fires even process-dead) → cheap gated
        // safety-net check → fresh fix inside → fence cured INSIDE + anchor re-sealed with fresh
        // steps. Delivered to a broadcast receiver, NOT the detection service: an ENTER must
        // never arm anything or flash the FGS notification — it only re-seals state.
        val enterFence = Geofence.Builder()
            .setRequestId(ENTER_ID_PREFIX + geofenceId)
            .setCircularRegion(latitude, longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val enterRequest = GeofencingRequest.Builder()
            // No initial trigger: the cure re-registers while standing INSIDE — an initial ENTER
            // would fire a check that cures again, re-registering forever.
            .setInitialTrigger(NO_INITIAL_TRIGGER)
            .addGeofence(enterFence)
            .build()

        runCatching { geofencingClient.addGeofences(enterRequest, buildEnterPendingIntent()).await() }
            .onFailure { PaparcarLogger.w(TAG, "⚠ ENTER twin fence registration failed for $geofenceId (${it.message}) — return-anchor re-seal degraded until the next cure") }

        // [DET-EXIT-WITNESS-001] Witness EXIT fence: identical region + transition, delivered to
        // a pure-logging broadcast receiver. The main EXIT rides getForegroundService — if the OS
        // ever swallows that privileged start, the event vanishes with NO trace, and we cannot
        // distinguish "Play Services never emitted" (field 2026-07-09, Oppo 12:55 trip) from
        // "emitted but the service start was rejected" (BUG-FGS-001's 24 field crashes were this
        // lane, unwrapped). The witness is the discriminator: witness line without a Service
        // onStartCommand within seconds = delivery swallowed → the receiver-first refactor earns
        // its cost; no witness line = GMS never fired and no app-side plumbing would have helped.
        // Decision-free by contract: it must never arm, cure, or publish anything.
        val witnessFence = Geofence.Builder()
            .setRequestId(WITNESS_ID_PREFIX + geofenceId)
            .setCircularRegion(latitude, longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(LOITERING_DELAY_MS)
            .build()

        val witnessRequest = GeofencingRequest.Builder()
            .setInitialTrigger(NO_INITIAL_TRIGGER)
            .addGeofence(witnessFence)
            .build()

        runCatching { geofencingClient.addGeofences(witnessRequest, buildWitnessPendingIntent()).await() }
            .onFailure { PaparcarLogger.w(TAG, "⚠ witness fence registration failed for $geofenceId (${it.message}) — diagnostics-only, EXIT trigger unaffected") }
    }

    override suspend fun removeGeofence(geofenceId: String): Result<Unit> = runCatching<Unit> {
        // Forget it FIRST: if the removal throws we would rather re-register a fence that is
        // already gone than skip one because of a stale ledger entry. [DET-FENCE-REREGISTER-BY-CAUSE-001]
        FenceRegistrationLedger.forget(geofenceId)
        geofencingClient.removeGeofences(
            listOf(geofenceId, ENTER_ID_PREFIX + geofenceId, WITNESS_ID_PREFIX + geofenceId),
        ).await()
    }
        // [DET-A-RELEASED-PIN-TAKES-ITS-FENCES-WITH-IT-001] The witness lives HERE, in the one
        // place all eight call sites converge, because half of them discarded the Result in
        // silence: field 2026-08-31 21:22 (Oppo) released pin d194668c with no removal trace at
        // all, and its NEVER_EXPIRE enter_ twin was still firing 12 minutes later — with no way
        // to tell "the removal failed" from "it never ran". A removal that fails must say so:
        // the fence outlives the pin until its next trigger cleans it up (the ENTER-lane orphan
        // sweep), and that is a fact a field trace has to be able to read.
        .onSuccess { PaparcarLogger.d(TAG, "✓ removed fences for ${geofenceId.take(8)} (exit + enter + witness)") }
        .onFailure {
            PaparcarLogger.w(
                TAG,
                "⚠ removeGeofence(${geofenceId.take(8)}) FAILED (${it.message}) — the three fences " +
                    "outlive the released pin until their next trigger cleans them " +
                    "[DET-A-RELEASED-PIN-TAKES-ITS-FENCES-WITH-IT-001]",
            )
        }

    override suspend fun removeAllGeofences(): Result<Unit> = runCatching {
        FenceRegistrationLedger.reset()
        // removeGeofences(PendingIntent) drops every geofence registered against this app's
        // PendingIntent — no need to enumerate ids (GMS exposes no list API). The same builder
        // resolves to the existing PendingIntent because it matches on action + request code.
        geofencingClient.removeGeofences(buildPendingIntent()).await()
        geofencingClient.removeGeofences(buildEnterPendingIntent()).await()
        geofencingClient.removeGeofences(buildWitnessPendingIntent()).await()
    }

    override fun getGeofenceEvents(): Flow<GeofenceEvent> = geofenceEventBus.events

    private fun buildPendingIntent(): PendingIntent {
        // [DET-G-01] Deliver the geofence transition DIRECTLY to the detection service via
        // getForegroundService, so Play Services grants the privileged FGS start (the same mechanism
        // the AR IN_VEHICLE path uses — see ActivityRecognitionManagerImpl / BUG-FGS-001). This is
        // what lets the geofence exit BOTH dispatch departure AND arm the next detection: a
        // background BroadcastReceiver/Worker cannot legally start an FGS on Android 12+.
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            action = CoordinatorDetectionService.ACTION_GEOFENCE_EXIT
        }
        // FLAG_MUTABLE is required: Play Services fills GeofencingEvent extras into the intent at
        // delivery time. FLAG_IMMUTABLE blocks this on Android 12+ — triggeringGeofences would be null.
        return PendingIntent.getForegroundService(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** [DET-RETURN-ANCHOR-001] ENTER events go to a plain broadcast (→ WorkManager check), never
     *  to the detection service — re-entering your own fence must not start an FGS. */
    private fun buildEnterPendingIntent(): PendingIntent {
        val intent = Intent(context, com.rndeveloper.paparcar.detection.receiver.GeofenceEnterReceiver::class.java)
        // FLAG_MUTABLE: Play Services fills GeofencingEvent extras at delivery time.
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ENTER,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** [DET-EXIT-WITNESS-001] The witness EXIT goes to a pure-logging broadcast — it must not
     *  (and legally could not, BUG-FGS-001) start anything; its only job is existing in the log. */
    private fun buildWitnessPendingIntent(): PendingIntent {
        val intent = Intent(context, com.rndeveloper.paparcar.detection.receiver.GeofenceExitWitnessReceiver::class.java)
        // FLAG_MUTABLE: Play Services fills GeofencingEvent extras at delivery time.
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_WITNESS,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val TAG = "GeofenceManager"
        private const val REQUEST_CODE = 9100
        private const val REQUEST_CODE_ENTER = 9101
        private const val REQUEST_CODE_WITNESS = 9102
        private const val LOITERING_DELAY_MS = 60_000
        /** [DET-RETURN-ANCHOR-001] Request-id prefix of the twin ENTER fence. */
        const val ENTER_ID_PREFIX = "enter_"
        /** [DET-EXIT-WITNESS-001] Request-id prefix of the witness EXIT fence. */
        const val WITNESS_ID_PREFIX = "witness_"
        /** Suppress the initial dwell trigger when registering a geofence. */
        private const val NO_INITIAL_TRIGGER = 0
        // Geofences use NEVER_EXPIRE: a car can stay parked for days, and a TTL would silently drop
        // exit detection if WorkManager (the re-registering Janitor) is throttled by the OEM before
        // it expires. Orphan prevention no longer relies on expiry — it relies on explicit removal:
        // session-end paths (revert / confirmed-departure / location-move), removeAllGeofences() on
        // sign-out, and the OS clearing all geofences on uninstall and reboot. [GEOF-001]
    }
}
