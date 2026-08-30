@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.detection.worker

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rndeveloper.paparcar.isDebugBuild
import com.rndeveloper.paparcar.detection.ExactHeartbeatScheduler
import com.rndeveloper.paparcar.detection.FenceRegistrationLedger
import com.rndeveloper.paparcar.detection.geofenceFailureDetail
import com.rndeveloper.paparcar.detection.toGeofenceRegistrationFailure
import com.rndeveloper.paparcar.detection.SentryResidenceStore
import com.rndeveloper.paparcar.detection.SignificantMotionMonitor
import com.rndeveloper.paparcar.detection.PendingDetectionStore
import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.sentry.SentryKillVerdict
import com.rndeveloper.paparcar.domain.detection.sentry.resolveSentryKillVerdict
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.bluetooth.BtConnectionStore
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.sensor.StepCounterSource
import com.rndeveloper.paparcar.domain.service.DepartureEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.usecase.location.GetOneLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateSafetyNetCheckUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.SafetyNetAction
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.util.haversineMeters
import com.rndeveloper.paparcar.notification.ForegroundNotificationProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import androidx.core.content.edit
import com.rndeveloper.paparcar.domain.usecase.parking.StillParkedReason

/**
 * The parked-session safety net: the one departure guarantee that does not depend on Play
 * Services delivering anything. [DET-SAFETY-NET-001]
 *
 * Runs every 15 min while the app is installed (WorkManager survives process death and OEM kills
 * of the app process) and on demand from the significant-motion hardware trigger
 * ([SignificantMotionMonitor] → [enqueueCheckNow]). When a session is parked and detection is
 * idle it samples ONE active balanced-priority fix and lets the pure evaluator decide
 * ([EvaluateSafetyNetCheckUseCase]):
 *
 *  - **The fix itself is half the job.** Play Services' geofencing engine only updates its
 *    INSIDE/OUTSIDE state when a fix reaches the fused provider; with the phone still and no app
 *    requesting location it starves for tens of minutes. Every tick feeds it.
 *  - **Inside the fence → cure.** Re-register the geofence (idempotent, `FLAG_UPDATE_CURRENT`,
 *    no initial trigger) so a state poisoned OUTSIDE by a false walking-EXIT is rebuilt INSIDE —
 *    without this, the next real drive-away produces no EXIT transition at all (field incident
 *    2026-07-04, Calle Gavia).
 *  - **Far + vehicle evidence → dispatch** the normal [DepartureDetectionWorker] pipeline (its
 *    verdict/retry/publish/clear machinery is the single brain for departures).
 *  - **Far, no evidence → prompt.** Distance alone NEVER releases (walked / bus / friend's car —
 *    BUG-WALK-DEPART-001); the "still parked?" notification lets the user disambiguate, throttled
 *    so it cannot nag.
 *
 * Each tick also mirrors the parked-idle state onto the significant-motion trigger, which is the
 * immediacy layer of the same net (the sensor listener dies with the process; this worker is what
 * resurrects it). Replaces the old `DetectionHeartbeatWorker` watchdog, whose passive-fix
 * isolation predates DET-SOLID-001 — false EXITs are now dismissed cleanly by the worker chain,
 * so provoking the geofence engine with an active fix is exactly what we want.
 */
class ParkingSafetyNetWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val userParkingRepository: UserParkingRepository by inject()
    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val getOneLocation: GetOneLocationUseCase by inject()
    private val notificationPort: AppNotificationManager by inject()
    private val detectionRuntime: DetectionRuntimeState by inject()
    private val departureEventBus: DepartureEventBus by inject()
    private val evaluateSafetyNetCheck: EvaluateSafetyNetCheckUseCase by inject()
    private val geofenceManager: GeofenceManager by inject()
    private val significantMotionMonitor: SignificantMotionMonitor by inject()
    private val detectionEventLogger: DetectionEventLogger by inject()
    private val stepCounterSource: StepCounterSource by inject()
    private val config: ParkingDetectionConfig by inject()
    // [DET-HANDOFF-NOT-MANUAL-001] The handoff has its own port; the "I'm driving" one belongs to the
    // user's button and to nobody else.
    private val arrivalHandoffDetection: com.rndeveloper.paparcar.domain.detection.ports.ArrivalHandoffDetection by inject()
    // [DET-BT-IDENTITY-GATE-001] Per-session BT-identity inputs for the evaluator's release veto.
    private val vehicleRepository: VehicleRepository by inject()
    private val bluetoothScanner: BluetoothScanner by inject()

    /**
     * [DET-SAFETY-NET-FGS-IS-TYPED-DATA-SYNC-001] The expedited path promotes this worker to a
     * foreground service, and the TYPE is part of that promotion — not decoration.
     *
     * It used to use the two-argument constructor, which leaves the type to WorkManager's default;
     * the permission the app declared to cover that default was `FOREGROUND_SERVICE_DATA_SYNC`. So
     * the service that carries the safety net announced itself to the system as data
     * synchronisation while it took a GPS fix ([getOneLocation]) to decide whether the car is still
     * where we left it.
     *
     * Two reasons that is worth fixing rather than tolerating. `dataSync` carries a duration cap from
     * Android 15 and `location` does not, so the wrong label leans on a type the system expires; and
     * a refused promotion here loses the LAST net — the one that reconciles departures the OS never
     * delivered — with nothing in the trace to say so.
     *
     * Below API 29 the type is ignored, so `minSdk 26` needs no guard. The manifest must ALSO allow
     * it: the type passed here has to be declared on WorkManager's own `SystemForegroundService`,
     * which ships without one — see the `tools:node="merge"` entry in `AndroidManifest.xml`.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            AppNotificationManager.DETECTION_NOTIFICATION_ID,
            foregroundNotificationProvider.buildDetectionNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

    override suspend fun doWork(): Result {
        // What woke this check up — rides into the debug notification and the DEPARTURE_VERDICT
        // telemetry so a field trace shows WHICH layer (periodic / sensor / teardown) saw what.
        val source = inputData.getString(KEY_SOURCE) ?: SOURCE_PERIODIC

        val sessions = runCatching { userParkingRepository.observeActiveSessions().firstOrNull().orEmpty() }
            .getOrElse {
                PaparcarLogger.e(DIAG, "✗ failed to read active sessions", it)
                return Result.success()
            }

        // [OEM-KILL-001] Heartbeat: measure the gap since the previous safety-net run BEFORE
        // stamping the new one. An hours-long gap with a session active means the OEM froze
        // background execution for that whole window — surface it (telemetry + contextual fix ask).
        // [DET-RESIDENT-FGS-001 · F2] The sentry-kill check reads the SAME pre-stamp heartbeat, so
        // it must run before detectBackgroundKill re-stamps it.
        detectSentryKill(source)
        detectBackgroundKill(sessions)
        detectConfirmedForceStop(sessions)

        // [DET-NEVER-SILENT-001] Recover a park lost to process death BEFORE the active-session gate:
        // a session that armed and was killed mid-trip never confirmed, so it has no active session.
        // A pending whose heartbeat went stale is exactly that — nudge (real trips only) and clear.
        // [DET-FGS-REAPER-001] Same signal also reaps the ghost detection FGS notification.
        checkStalePendingDetections(source)

        // [DET-SIGMOTION-001] Mirror the parked-idle state onto the hardware trigger on EVERY
        // tick — this is also what re-arms it after a process kill or after it fired one-shot.
        val parkedAndIdle = sessions.isNotEmpty() && !detectionRuntime.isRunning.value
        runCatching { significantMotionMonitor.sync(shouldBeArmed = parkedAndIdle) }
        // [DET-EXACT-HEARTBEAT-001] Same mirror for the exact-alarm polling net: every tick arms /
        // re-arms / disarms it from this ONE place, so the chain self-heals through process kills
        // and dies with the session. The existing check-now mesh (detection-end, app-start, boot)
        // is what seeds the first arm after a park — no extra call sites.
        runCatching { ExactHeartbeatScheduler.sync(appContext, shouldBeArmed = parkedAndIdle, config = config) }

        if (sessions.isEmpty()) {
            dismissPrompt()
            // No debug notif on the eternal periodic no-op — it would live in the shade forever.
            if (source != SOURCE_PERIODIC) debugNotify("Red de seguridad [$source]: no hay plaza aparcada → nada que vigilar")
            return Result.success()
        }
        // Mid-trip: a live coordinator session owns the situation; a repark also self-heals the
        // old session (replaceActiveSession per vehicle). Don't second-guess it.
        if (detectionRuntime.isRunning.value) {
            PaparcarLogger.d(DIAG, "■ detection running — skipping check")
            debugNotify("Red de seguridad [$source]: la detección ya está trabajando este viaje → no interfiero")
            return Result.success()
        }
        if (!hasLocationPermission()) {
            PaparcarLogger.w(DIAG, "■ no location permission — skipping check")
            debugNotify("Red de seguridad [$source]: SIN permiso de ubicación → no puedo comprobar tu plaza (revisa permisos en Ajustes)")
            return Result.success()
        }

        // ACTIVE fix on purpose (not passive last-known): feeding the fused provider is what keeps
        // the geofencing engine's state machine alive while the phone sits in Doze. FRESH on
        // purpose too: the cache served "inside the fence" after the car had left and a frozen
        // mid-drive position (field 2026-07-07) — a stale fix here poisons the anchor and blinds
        // the whole check, so no fix beats an old fix. [DET-RECONCILE-001]
        val fix = runCatching { getOneLocation(maxAgeMs = config.freshFixMaxAgeMs) }.getOrNull()
        if (fix == null) {
            PaparcarLogger.d(DIAG, "■ no fresh fix within timeout — nothing to evaluate this tick")
            debugNotify("Red de seguridad [$source]: el GPS no dio posición fresca en 15 s → sin datos esta vez; el siguiente tick lo reintenta")
            return Result.success()
        }

        val now = System.currentTimeMillis()
        var anyPromptActive = false
        val debugLines = mutableListOf<String>()

        // [DET-RECONCILE-001] Cumulative hardware step counter: read once per tick; the delta
        // against the value stored with the anchor is the step budget that separates "walked
        // away" from "was driven away" even when the whole trip happened while we slept.
        val cumulativeSteps = runCatching { stepCounterSource.currentCumulativeSteps() }.getOrNull()

        // [DET-BT-IDENTITY-GATE-001] Fleet + BT-adapter state, read once per tick: the release veto
        // needs, per session, whether its vehicle is BT-paired AND Bluetooth is currently on — the
        // BLUETOOTH strategy owns that car's identity, so a real drive of it connects to its MAC.
        val vehicles = runCatching { vehicleRepository.observeVehicles().firstOrNull().orEmpty() }
            .getOrDefault(emptyList())
        val btEnabled = runCatching { bluetoothScanner.isBluetoothEnabled() }.getOrDefault(false)

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // [DET-UNWITNESSED-DISPLACEMENT-001] This fresh check fix is an independent witness of
        // where the body is; the honest close holds the next abort fix to spatio-temporal
        // coherence against it, so a teleporting GPS mirage can no longer "prove" a trip.
        // [DET-DEPARTURE-IS-NOT-ARRIVAL-001] The ARRIVAL budget: how much the body has walked since
        // that same witness was stamped. Read BEFORE the slot is overwritten below — this tick's
        // own reading is the new zero point, not the measure. Negative = reboot → unknown.
        val witnessSteps = prefs.getLong(KEY_LAST_WITNESSED_STEPS, -1L).takeIf { it >= 0L }
        val stepsSinceLastWitness = if (cumulativeSteps != null && witnessSteps != null &&
            cumulativeSteps >= witnessSteps
        ) {
            cumulativeSteps - witnessSteps
        } else {
            null
        }
        prefs.edit {
            putString(KEY_LAST_WITNESSED_POS, "${fix.latitude},${fix.longitude}")
            putFloat(KEY_LAST_WITNESSED_ACC, fix.accuracy)
            putLong(KEY_LAST_WITNESSED_AT, now)
            // The counter sample belongs to the SAME seal as the position: a fresh position paired
            // with a stale step count would invent a walk that never happened. Unreadable counter →
            // drop the slot so the next tick honestly reports "no arrival budget".
            if (cumulativeSteps != null) putLong(KEY_LAST_WITNESSED_STEPS, cumulativeSteps)
            else remove(KEY_LAST_WITNESSED_STEPS)
        }
        // Drop anchors of geofences that no longer have an active session (departed / reverted).
        pruneStaleAnchors(prefs, sessions.mapNotNullTo(mutableSetOf()) { it.geofenceId })

        for (session in sessions) {
            val anchorSteps = session.geofenceId?.let { readAnchorSteps(prefs, it) }
            // Negative delta = reboot reset the counter → budget unknown, never a verdict.
            val stepsSinceAnchor = if (cumulativeSteps != null && anchorSteps != null && cumulativeSteps >= anchorSteps) {
                cumulativeSteps - anchorSteps
            } else {
                null
            }
            // [DET-BT-IDENTITY-GATE-001] Identity inputs for THIS session's vehicle: BT-gated only
            // when it is BT-paired AND the adapter is on; last connection stamped by the BT receiver.
            val vehicleBtGated = btEnabled &&
                session.vehicleId != null &&
                vehicles.firstOrNull { it.id == session.vehicleId }?.bluetoothDeviceId != null
            val lastBtConnectedAtMs = session.vehicleId?.let { BtConnectionStore.lastConnectedAt(appContext, it) }
            val action = evaluateSafetyNetCheck(
                session = session,
                fix = fix,
                lastSeenNearCarAtMs = session.geofenceId?.let { readAnchor(prefs, it) },
                nowMs = now,
                stepsSinceAnchor = stepsSinceAnchor,
                // [DET-DEPARTURE-IS-NOT-ARRIVAL-001] The arrival budget — the only one that may
                // bound a NEW pin's position; the anchor budget above is already spent on the ride.
                stepsSinceLastWitness = stepsSinceLastWitness,
                // AR boarding stamp: the brain's ride proof for mute-counter devices.
                // [DET-EXIT-TRUST-001]
                lastVehicleEnteredAtMs = departureEventBus.lastVehicleEnteredAt,
                // The fact "the OS delivered an EXIT for this fence" (recorded by the trust
                // triage when delivery came too far away to act on directly) — half of the
                // exit∧enter conjunction proof. [DET-CONJUNCTION-001]
                exitDeliveredAtMs = session.geofenceId?.let { readExitDeliveredAt(prefs, it) },
                // App-start tick = the user is LOOKING at the app right now — the zero-cost
                // moment for the ask-when-blind prompt. [DET-ANCHOR-FREEZE-001]
                userPresent = source == SOURCE_APP_START,
                // [DET-BT-IDENTITY-GATE-001] BT-owned vehicle with no connection since it parked →
                // the reconstructed release degrades to the "still parked?" ask, not an auto-release.
                vehicleBtGated = vehicleBtGated,
                lastBtConnectedAtMs = lastBtConnectedAtMs,
            )
            val distanceM = haversineMeters(
                fix.latitude, fix.longitude,
                session.location.latitude, session.location.longitude,
            ).toInt()
            val geofTag = session.geofenceId?.take(8) ?: "sin-geof"
            when (action) {
                is SafetyNetAction.CureGeofence -> {
                    // Position anchor: the phone is provably AT the car right now — this is what
                    // authorises a later far+evidence auto-dispatch (movement started at the car).
                    // Persisted to disk (NOT in-memory): an aggressive OEM kills the process between
                    // parking and driving away, so the anchor MUST survive process death or a real
                    // drive-away wakes in a fresh process with no anchor and only prompts — the spot
                    // is then lost while the user drives (field incident 2026-07-05, Oppo: 69 km/h
                    // departure degraded to prompt because the in-memory anchor was empty). [ANCHOR-PERSIST-001]
                    writeAnchor(prefs, action.geofenceId, now)
                    // The counter value AT the anchor moment — the step budget's zero point.
                    // Time and steps are an ATOMIC PAIR: refreshing the time while keeping an old
                    // zero-point makes the delta count steps walked BEFORE the anchor (field
                    // 2026-07-07, Oppo 12:17 — 365 phantom steps vetoed a real short-hop verdict).
                    // When the read fails, CLEAR the zero-point: delta=null falls back to the
                    // pedestrian-physics check, which only needs the time anchor. [DET-RECONCILE-001]
                    if (cumulativeSteps != null) {
                        // The cure fires with the phone provably AT the car, so the fix is the
                        // honest "where the body was when the counter was read" — stored with the
                        // zero-point so the honest close can compare same-origin after a cure
                        // re-seal too. [DET-STEP-BUDGET-ORIGIN-001]
                        writeAnchorSteps(prefs, action.geofenceId, cumulativeSteps, fix)
                    } else {
                        removeAnchorSteps(prefs, action.geofenceId)
                    }
                    // [DET-ANCHOR-FREEZE-001 F4] Re-registering RESETS Play Services' internal
                    // INSIDE/OUTSIDE state to "unknown" until its next initial evaluation — a
                    // blind window in which a drive-away produces NO EXIT transition. Curing on
                    // EVERY tick inside meant every parked-at-home day re-opened that window
                    // dozens of times; on 2026-07-11 a cure landed ~40 s before drive-off and the
                    // departure was silent. The GMS registration is therefore throttled: once per
                    // process start (fences are wiped by force-stop/app-update — the case the
                    // cure exists for), then at most every [ParkingDetectionConfig.cureReregisterMinIntervalMs].
                    // The ANCHOR write above is NOT throttled — its freshness is what authorises
                    // far+evidence departures.
                    //
                    // [DET-FENCE-REREGISTER-BY-CAUSE-001 §B] What changed: a POISONED stamp is
                    // evidence, and evidence outranks every clock here. A dismissed false EXIT left
                    // Play Services believing we are outside a fence it still holds, so that fence
                    // exists and is useless — curing then is not a risk, it is the job. Everything
                    // else in this block is the blind floor for the one poisoning we get no signal
                    // for (GMS ate the walking EXIT and then missed the return ENTER in Doze).
                    val poisonedAt = prefs.getLong(POISONED_KEY_PREFIX + action.geofenceId, 0L)
                    val statePoisoned = poisonedAt > 0L
                    val lastCureAt = prefs.getLong(CURE_KEY_PREFIX + action.geofenceId, 0L)
                    val firstCureThisProcess = curedFencesThisProcess.add(action.geofenceId)
                    val mustReregister = evaluateSafetyNetCheck.shouldReregisterCure(
                        alreadyCuredThisProcess = !firstCureThisProcess,
                        lastCureAtMs = lastCureAt,
                        nowMs = now,
                        // [DET-CURE-FRESH-001] Age of the parked session: a fresh fence (manual pin
                        // seconds ago) must not re-register and open the blind window before drive-off.
                        sessionAgeMs = now - session.location.timestamp,
                        statePoisoned = statePoisoned,
                    )
                    // …and the shared ledger has the last word on redundancy, so the cure cannot
                    // re-register a fence the janitor registered seconds ago — unless the state is
                    // poisoned, in which case "it was registered a minute ago" is no argument: the
                    // fence being there is exactly what is NOT the problem.
                    val ledgerAgrees = FenceRegistrationLedger.shouldRegister(
                        geofenceId = action.geofenceId,
                        nowMs = now,
                        dedupWindowMs = config.fenceRegisterDedupWindowMs,
                        hasKnownCause = statePoisoned,
                    )
                    if (!mustReregister || !ledgerAgrees) {
                        // [DET-THE-EVIDENCE-MUST-REACH-THE-TRACE-001] This line used to claim ONE
                        // cause for every no-op, and invent a number to back it: `lastCureAt`
                        // defaults to 0 when the fence was never cured, so a brand-new pin printed
                        // «la valla se re-registró hace 29797878min» — 56 years, from the epoch —
                        // as the reason for not curing. The real reason in that case is the
                        // opposite one: the parking is too FRESH to cure (field 2026-08-28).
                        // `shouldReregisterCure` has three ways to say no; the trace now says which.
                        val sessionAgeMs = now - session.location.timestamp
                        val why = when {
                            mustReregister -> "otra vía acaba de registrarla, no repito"
                            sessionAgeMs < config.cureSkipFreshSessionMs ->
                                "el aparcamiento es de hace ${sessionAgeMs / 60_000}min y aún no ha " +
                                    "podido quedarse ciego, no toca"
                            lastCureAt <= 0L ->
                                "esta valla no se ha re-registrado nunca y este proceso ya la miró, no repito"
                            else ->
                                "la valla se re-registró hace ${(now - lastCureAt) / 60_000}min, no toca aún"
                        }
                        debugLines += "geof=$geofTag: sigues junto al coche (d=${distanceM}m, radio ${action.radiusMeters.toInt()}m) → resello la referencia de pasos; $why"
                    } else {
                        val cureReason = if (statePoisoned) "poisoned ${(now - poisonedAt) / 1000}s ago" else "blind floor"
                        PaparcarLogger.d(DIAG, "▶ inside fence — re-registering geofence=${action.geofenceId} ($cureReason, steps@anchor=${cumulativeSteps ?: "?"})")
                        val result = geofenceManager.createGeofence(
                            geofenceId = action.geofenceId,
                            latitude = session.location.latitude,
                            longitude = session.location.longitude,
                            radiusMeters = action.radiusMeters,
                        )
                        // [DET-FENCE-REREGISTER-BY-CAUSE-001 §D] The throwable used to die right
                        // here: the log said ✗ and the remote event said `false`, and the Play
                        // Services status code — which is the whole answer — went in the bin.
                        val failure = result.exceptionOrNull()?.also { e ->
                            PaparcarLogger.w(DIAG, "  ⚠ cure re-register FAILED geof=${action.geofenceId}: ${e.geofenceFailureDetail()}", e)
                        }
                        // [DET-FENCE-REREGISTER-BY-CAUSE-001 §B/§C] The floor counts SUCCESSES, not
                        // attempts. The stamp used to be written before the call and never rolled
                        // back, so a failed cure bought itself six hours of silence — the lane whose
                        // entire job is restoring a fence went quiet precisely because it had just
                        // failed to restore one. A failed registration changed nothing in Play
                        // Services: it opened no blind window, so it earns no quiet period.
                        // The poison stamp is consumed only on success too, for the same reason:
                        // one poisoning buys one REPAIR, not one attempt.
                        if (failure == null) {
                            prefs.edit {
                                putLong(CURE_KEY_PREFIX + action.geofenceId, now)
                                if (statePoisoned) remove(POISONED_KEY_PREFIX + action.geofenceId)
                            }
                        } else {
                            // Let the next tick try again instead of inheriting a turn we never took.
                            curedFencesThisProcess.remove(action.geofenceId)
                        }
                        runCatching {
                            detectionEventLogger.log(
                                DetectionEvent.GeofenceRegistration(
                                    sessionId = action.geofenceId,
                                    timestampMs = now,
                                    success = result.isSuccess,
                                    radiusMeters = action.radiusMeters,
                                    location = fix,
                                    source = REGISTRATION_SOURCE_CURE,
                                    failure = failure?.toGeofenceRegistrationFailure(),
                                )
                            )
                        }
                        debugLines += "geof=$geofTag: sigues junto al coche (d=${distanceM}m, radio ${action.radiusMeters.toInt()}m) → re-registro la valla por si el sistema la borró" +
                            (failure?.let { " ✗FALLÓ: ${it.toGeofenceRegistrationFailure().label}" } ?: "")
                    }
                }

                is SafetyNetAction.DispatchDeparture -> {
                    // [DET-RECONCILE-001] preconfirmed = the trip already ENDED — the evaluator
                    // dates it (anchor seal, or the AR boarding when that was the proof) so the
                    // freshness gate measures the real age of the freed spot, not the age of
                    // this wake-up. [DET-EXIT-TRUST-001]
                    val exitAtMs = action.tripStartedAtMs ?: now
                    PaparcarLogger.d(
                        DIAG,
                        "▶ far with vehicle evidence — dispatching departure geofence=${action.geofenceId} " +
                            "(preconfirmed=${action.preconfirmed} steps=${stepsSinceAnchor ?: "?"} d=${distanceM}m)"
                    )
                    val departureChain = WorkManager.getInstance(appContext).beginUniqueWork(
                        "${DepartureDetectionWorker.TAG}_${action.geofenceId}",
                        ExistingWorkPolicy.REPLACE,
                        DepartureDetectionWorker.buildRequest(
                            geofenceId = action.geofenceId,
                            exitTimestampMs = exitAtMs,
                            preconfirmed = action.preconfirmed,
                        ),
                    )
                    // [DET-RECONCILE-001] Backfill the NEW parking only when its position is
                    // BOUNDED — decided by the PURE evaluator ([SafetyNetAction.DispatchDeparture
                    // .backfillBounded]: trusted step budget within the boarding cap + pin-grade
                    // fix accuracy), so the phantom-pin class of go/no-go is unit-tested, not
                    // worker folklore. Anything weaker must NOT guess a position: arrival
                    // placement is the live coordinator's job. Chained AFTER the departure so the
                    // old session resolves (publish+clear) before the confirm replaces the
                    // vehicle's active session.
                    if (action.preconfirmed && action.backfillBounded) {
                        PaparcarLogger.d(DIAG, "  → chaining parking backfill at wake-up fix (steps=${action.trustedStepsSinceAnchor} acc=${fix.accuracy}, arrivalWalk=${stepsSinceLastWitness ?: "?"} steps)")
                        departureChain.then(
                            ParkingBackfillWorker.buildRequest(
                                fix = fix,
                                vehicleId = session.vehicleId,
                                reliability = config.reliabilityUnattendedSave,
                            )
                        ).enqueue()
                    } else {
                        departureChain.enqueue()
                        // [DET-DEPARTURE-IS-NOT-ARRIVAL-001] Say WHY nothing was placed. Without
                        // this the trace cannot tell "the net refused to guess" from "the net never
                        // ran" — the same reason [DET-BACKFILL-TAINT-001] logs its own deferral.
                        if (action.preconfirmed) {
                            PaparcarLogger.d(
                                DIAG,
                                "  ⊘ arrival NOT placed — the ride was proven with the anchor budget, " +
                                    "so it cannot also bound the new pin (arrivalWalk=${stepsSinceLastWitness ?: "unknown"} steps, " +
                                    "acc=${fix.accuracy}m) [DET-DEPARTURE-IS-NOT-ARRIVAL-001]"
                            )
                            runCatching {
                                detectionEventLogger.log(
                                    DetectionEvent.Decision(
                                        sessionId = action.geofenceId,
                                        timestampMs = now,
                                        outcome = OUTCOME_ARRIVAL_UNWITNESSED,
                                        pathLabel = PATH_SAFETY_NET_BACKFILL,
                                        location = fix,
                                    ),
                                )
                            }
                        }
                    }
                    // [DET-ARRIVAL-HANDOFF-001] A dispatched departure must end in exactly one of:
                    // a backfilled session (position bounded, trip provably over) or LIVE
                    // detection following the rest of the trip so the NEW parking is captured at
                    // full quality. NEVER neither — that orphans the arrival: the evaluator
                    // detected the Oppo's return trip mid-drive (2026-07-08 20:41), cleared the
                    // session, and nobody was listening when the user parked 5 min later.
                    // Background FGS-start may be denied (Android 12+/OEM); then the still-parked
                    // prompt asks the user to place it — a notification beats silence.
                    val backfillChained = action.preconfirmed && action.backfillBounded
                    if (!backfillChained) {
                        // [DET-HANDOFF-NOT-MANUAL-001] Through the handoff's OWN port: this arm is
                        // the net's deduction, not the user's word, and every downstream reader
                        // (strategy gate, evidence strength, diagnostics, debug copy) must be able
                        // to tell the two apart. It used to call the "I'm driving" port.
                        runCatching { arrivalHandoffDetection.start() }
                            .onSuccess { PaparcarLogger.d(DIAG, "  → departure dispatched without backfill — arrival handoff started for the rest of the trip") }
                            .onFailure { e ->
                                PaparcarLogger.w(DIAG, "  ⊘ tracking service start denied (${e.message}) — asking the user via still-parked prompt")
                                // This prompt must survive the end-of-run cleanup: without the
                                // flag, `if (!anyPromptActive) dismissPrompt()` below erased it
                                // milliseconds after showing (field 2026-07-09 13:55, Redmi:
                                // the user saw NO notification for the whole ride home).
                                // [DET-RIDE-PROOF-001]
                                anyPromptActive = true
                                runCatching {
                                    notificationPort.showStillParkedPrompt(
                                        geofenceId = action.geofenceId,
                                        latitude = session.location.latitude,
                                        longitude = session.location.longitude,
                                    )
                                }
                            }
                    }
                    logVerdict(
                        action.geofenceId,
                        verdict = if (action.preconfirmed) "safety_net_dispatch_stepbudget" else "safety_net_dispatch",
                        source = source,
                        fixSpeedKmh = fix.speed * KMH_PER_MPS,
                        now = now,
                    )
                    debugLines += "geof=$geofTag: LEJOS del coche (d=${distanceM}m) y CON pruebas de viaje → proceso tu salida, la plaza se libera" +
                        if (action.preconfirmed) " (avalada por ${stepsSinceAnchor ?: "?"} pasos desde el coche)" else ""
                }

                is SafetyNetAction.PromptStillParked -> {
                    anyPromptActive = true
                    // Throttle persisted to disk (NOT in-memory): the OEM kills the process, so an
                    // in-memory throttle is empty on every app-start and re-nags the same prompt each
                    // time (field incident 2026-07-05). [ANCHOR-PERSIST-001]
                    val lastPromptAt = prefs.getLong(PROMPT_KEY_PREFIX + action.geofenceId, 0L)
                    val throttled = now - lastPromptAt < PROMPT_THROTTLE_MS
                    if (!throttled) {
                        PaparcarLogger.d(DIAG, "▶ moving far without anchor — still-parked prompt geofence=${action.geofenceId}")
                        prefs.edit { putLong(PROMPT_KEY_PREFIX + action.geofenceId, now) }
                        notificationPort.showStillParkedPrompt(
                            geofenceId = action.geofenceId,
                            latitude = session.location.latitude,
                            longitude = session.location.longitude,
                        )
                        logVerdict(action.geofenceId, verdict = "safety_net_prompt", source = source, fixSpeedKmh = fix.speed * KMH_PER_MPS, now = now)
                    }
                    // [DET-DETECTION-PATH-IS-A-TYPE-001] La causa la DECIDE el evaluador, no la
                    // adivina esta línea. Antes decía «SIN pruebas de viaje» para las cuatro
                    // situaciones que llegan aquí — y para una de ellas es lo contrario: sí se midió
                    // conducción, lo que falta es que empezara en el coche.
                    val porque = when (action.reason) {
                        StillParkedReason.BT_IDENTITY_MISSING ->
                            "este coche se vigila por Bluetooth y no hay conexión que avale este trayecto"
                        StillParkedReason.BOARDED_AWAY_FROM_CAR ->
                            "vas a velocidad de coche pero el movimiento no empezó junto al tuyo (bus/taxi/te llevan)"
                        StillParkedReason.UNEXPLAINED_EXIT ->
                            "hubo una salida de la valla que los pasos contados no explican"
                        StillParkedReason.USER_PRESENT_AND_BLIND ->
                            "tienes la app abierta y ninguna prueba explica cómo has llegado hasta aquí"
                    }
                    debugLines += "geof=$geofTag: LEJOS del coche (d=${distanceM}m) y $porque → te pregunto '¿sigues aparcado?' en vez de liberar${if (throttled) " (pregunta reciente, no repito)" else ""}"
                }

                SafetyNetAction.None -> {
                    debugLines += "geof=$geofTag: distancia ambigua (d=${distanceM}m, ni junto al coche ni claramente lejos) → solo apunto la posición, no decido nada"
                }
            }
        }

        // Back near the car (or ambiguity resolved) → any lingering prompt is stale.
        if (!anyPromptActive) dismissPrompt()

        // File-visible mirror of the debug notification: the notification shade rotates, the
        // parkdiag capture is what field forensics actually reads.
        PaparcarLogger.d(DIAG, "[$source] ${debugLines.joinToString(" · ")}")
        debugNotify("Red de seguridad [$source]: ${debugLines.joinToString(" · ")}")

        return Result.success()
    }

    /** DEBUG-build breadcrumb of what each safety-net wake-up saw and did. [DET-SAFETY-NET-001] */
    private fun debugNotify(message: String) {
        if (isDebugBuild) notificationPort.showDebug(message)
    }

    /**
     * [DET-NEVER-SILENT-001] A pending detection whose heartbeat went stale means the OS killed the
     * process before the session could confirm or abort — a park silently lost. Nudge "where did you
     * park?" once (only for real trips: GEOFENCE_EXIT / MANUAL always, AR_VEHICLE_ENTER if it drove)
     * and clear every stale pending so it fires at most once. Clearing on-nudge is the throttle.
     */
    private fun checkStalePendingDetections(source: String) {
        val now = System.currentTimeMillis()
        val stale = PendingDetectionStore.scanStale(appContext, now, config.pendingDetectionDeadMs)
        if (stale.isEmpty()) return

        // [DET-FGS-REAPER-001] A stale pending means a session armed and its process died before the
        // service's finally could tear down — EXACTLY when the foreground service leaves its detection
        // notification glued to a dead/frozen process (ColorOS freezes rather than kills; field
        // 2026-07-21, Oppo: a false-enter FGS hung ~2 h). This periodic tick is the first CPU the app
        // reliably gets back, so reap the ghost — never killing a process, only dismissing an orphan
        // notification. The pure decision's two locks guarantee a LIVE session is never touched.
        if (com.rndeveloper.paparcar.domain.detection.sentry.shouldReapGhostDetectionFgs(
                isPeriodicTick = source == SOURCE_PERIODIC,
                isDetectionRunning = detectionRuntime.isRunning.value,
                hasStalePending = true, // stale is non-empty here (early-returned above otherwise)
            )
        ) {
            notificationPort.dismiss(AppNotificationManager.DETECTION_NOTIFICATION_ID)
            PaparcarLogger.d(DIAG, "▶ [fgs-reaper] ${stale.size} stale pending(s), detection idle → reaped ghost FGS notification")
        }

        val shouldNudge = stale.any {
            com.rndeveloper.paparcar.domain.detection.sentry.shouldNudgeForStalePending(it.trigger, it.sawDriving)
        }
        if (shouldNudge) {
            PaparcarLogger.d(DIAG, "▶ [never-silent] ${stale.size} stale pending(s), heartbeat dead → mark-parking nudge")
            notificationPort.showMarkParkingNudge(source = "stale_pending_watchdog")
        } else {
            PaparcarLogger.d(DIAG, "  [never-silent] ${stale.size} stale pending(s), no drive evidence → clearing silently")
        }
        stale.forEach { PendingDetectionStore.clear(appContext, it.armId) }
    }

    /**
     * [OEM-KILL-001] Compares "now" against the previous heartbeat and logs a
     * [DetectionEvent.BackgroundKillSuspected] when the gap exceeds [KILL_GAP_THRESHOLD_MS] with a
     * session active — SILENT telemetry only, so we can measure per-OEM background freezing.
     *
     * It does NOT notify the user. A heartbeat gap cannot tell a harmful OEM hard-kill from
     * ordinary Doze (a phone idle/charging overnight legitimately defers a 15-min periodic for
     * hours) — and even a real freeze, while the car sits parked, causes NO harm. Warning here
     * cried wolf (field incident 2026-07-05: "your phone is blocking Paparcar" fired at 02:00 after
     * a night at home, no departure missed). Per the "earn the ask" rule, the battery/settings
     * prompt must be surfaced only after a freeze DEMONSTRABLY degrades an outcome — that will be
     * wired to a real harm signal, not to this gap. A reboot (elapsedRealtime went backwards)
     * explains the gap innocently — skip. Always re-stamps the heartbeat. [BATTERY-ASK-001]
     */
    /**
     * [DET-RESIDENT-FGS-001 · F2] Periodic lane of the sentry-kill detector: the service stamps a
     * durable residency marker on enterSentry and clears it on every deliberate exit, so a stamp
     * found here while the service is NOT resident means the OS killed the resident watcher.
     * [resolveSentryKillVerdict] (pure, shared with the service's own re-arm lane) decides; a
     * reboot explains the stamp innocently, same as [detectBackgroundKill]. Runs BEFORE the
     * heartbeat re-stamp so `gapMs` measures the true dark window since the last safety-net run.
     * SILENT telemetry — same "earn the ask" rule as the other kill signals.
     */
    private suspend fun detectSentryKill(source: String) {
        runCatching {
            val residency = SentryResidenceStore.read(appContext) ?: return
            val rebootedSince = SystemClock.elapsedRealtime() < residency.enteredElapsedMs
            when (
                resolveSentryKillVerdict(
                    residencyExpected = true,
                    presence = detectionRuntime.presence.value,
                    rebootedSince = rebootedSince,
                )
            ) {
                SentryKillVerdict.None -> Unit
                SentryKillVerdict.ClearStamp -> SentryResidenceStore.clear(appContext)
                SentryKillVerdict.Killed -> {
                    SentryResidenceStore.clear(appContext)
                    val now = System.currentTimeMillis()
                    val lastAliveAt = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getLong(KEY_LAST_ALIVE_AT, 0L)
                    val gapMs = (now - lastAliveAt).takeIf { lastAliveAt > 0L }
                    PaparcarLogger.w(DIAG, "⚠ sentry residency stamp outlived the process (gap ${gapMs?.div(60_000)} min) — resident watcher killed [DET-RESIDENT-FGS-001]")
                    detectionEventLogger.log(
                        DetectionEvent.Sentry(
                            sessionId = residency.geofenceId,
                            timestampMs = now,
                            event = DetectionEvent.Sentry.KILLED,
                            signal = "safety-net:$source",
                            gapMs = gapMs,
                            residencyMs = now - residency.enteredAtMs,
                        )
                    )
                }
            }
        }
    }

    private suspend fun detectBackgroundKill(sessions: List<UserParking>) {
        runCatching {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val elapsedNow = SystemClock.elapsedRealtime()
            val lastAliveAt = prefs.getLong(KEY_LAST_ALIVE_AT, 0L)
            val lastAliveElapsed = prefs.getLong(KEY_LAST_ALIVE_ELAPSED, 0L)
            val rebootedSince = elapsedNow < lastAliveElapsed
            val gapMs = now - lastAliveAt

            if (lastAliveAt > 0L && !rebootedSince && sessions.isNotEmpty() && gapMs > KILL_GAP_THRESHOLD_MS) {
                PaparcarLogger.w(DIAG, "⚠ background gap ${gapMs / 60_000} min with session active — logging (silent) [OEM-KILL-001]")
                runCatching {
                    detectionEventLogger.log(
                        DetectionEvent.BackgroundKillSuspected(
                            sessionId = sessions.firstNotNullOfOrNull { it.geofenceId } ?: "system",
                            timestampMs = now,
                            gapMs = gapMs,
                        )
                    )
                }
            }

            prefs.edit {
                putLong(KEY_LAST_ALIVE_AT, now)
                    .putLong(KEY_LAST_ALIVE_ELAPSED, elapsedNow)
            }
        }
    }

    /**
     * [OEM-KILL-001] Deterministic complement of the heartbeat heuristic: on Android 16+ the
     * platform records whether the app was force-stopped before the current process start
     * (`ApplicationStartInfo.wasForceStopped()`), which is exactly what an OEM "deep optimization"
     * kill amounts to (they invoke `forceStopPackage`). Unlike the gap heuristic this cannot
     * confuse deep Doze with a kill — and a force-stop is the harmful case: it WIPES registered
     * geofences, alarms and pending intents, so a departure in that window was undetectable.
     * Checked once per process start, logged only while a session is active (when a kill can
     * actually cost a spot). SILENT telemetry, same "earn the ask" rule as the heuristic.
     */
    private suspend fun detectConfirmedForceStop(sessions: List<UserParking>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        if (forceStopCheckedThisProcess || sessions.isEmpty()) return
        forceStopCheckedThisProcess = true
        runCatching {
            val activityManager = appContext.getSystemService(ActivityManager::class.java)
            val wasForceStopped = activityManager
                ?.getHistoricalProcessStartReasons(1)
                ?.firstOrNull()
                ?.wasForceStopped() == true
            if (wasForceStopped) {
                PaparcarLogger.w(DIAG, "⚠ platform confirms a FORCE-STOP before this process start [OEM-KILL-001]")
                detectionEventLogger.log(
                    DetectionEvent.ForceStopConfirmed(
                        sessionId = sessions.firstNotNullOfOrNull { it.geofenceId } ?: "system",
                        timestampMs = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    private suspend fun logVerdict(geofenceId: String, verdict: String, source: String, fixSpeedKmh: Float, now: Long) {
        runCatching {
            detectionEventLogger.log(
                DetectionEvent.DepartureVerdict(
                    sessionId = geofenceId,
                    timestampMs = now,
                    verdict = verdict,
                    source = "safety-net:$source",
                    speedKmh = fixSpeedKmh,
                    enterAgeMs = departureEventBus.lastVehicleEnteredAt?.let { now - it },
                )
            )
        }
    }

    // ── Position anchor persistence [ANCHOR-PERSIST-001] ──────────────────────────
    // Disk-backed so it survives the OEM process kills that are the norm on the very devices
    // the safety net exists for. Keyed by geofenceId under the same prefs file as the heartbeat.

    private fun readAnchor(prefs: android.content.SharedPreferences, geofenceId: String): Long? =
        prefs.getLong(ANCHOR_KEY_PREFIX + geofenceId, 0L).takeIf { it > 0L }

    private fun writeAnchor(prefs: android.content.SharedPreferences, geofenceId: String, atMs: Long) {
        prefs.edit { putLong(ANCHOR_KEY_PREFIX + geofenceId, atMs) }
    }

    /** Cumulative step-counter value at the anchor moment — the step budget's zero point.
     *  [DET-RECONCILE-001] */
    private fun readAnchorSteps(prefs: android.content.SharedPreferences, geofenceId: String): Long? =
        prefs.getLong(ANCHOR_STEPS_KEY_PREFIX + geofenceId, -1L).takeIf { it >= 0L }

    private fun writeAnchorSteps(
        prefs: android.content.SharedPreferences,
        geofenceId: String,
        steps: Long,
        sealFix: com.rndeveloper.paparcar.domain.model.GpsPoint,
    ) {
        prefs.edit {
            putLong(ANCHOR_STEPS_KEY_PREFIX + geofenceId, steps)
            // Zero-point + its position + its timestamp are one seal — same-origin contract
            // shared with AndroidDetectionStepAnchors. [DET-STEP-BUDGET-ORIGIN-001][DET-TRIP-WITNESS-001]
            putString(ANCHOR_SEAL_POS_KEY_PREFIX + geofenceId, "${sealFix.latitude},${sealFix.longitude}")
            putLong(ANCHOR_SEAL_AT_KEY_PREFIX + geofenceId, System.currentTimeMillis())
        }
    }

    /** Keeps the anchor pair coherent when a cure could not read the counter: a stale zero-point
     *  under a fresh time counts pre-anchor steps into the budget. [DET-RECONCILE-001] */
    private fun removeAnchorSteps(prefs: android.content.SharedPreferences, geofenceId: String) {
        prefs.edit {
            remove(ANCHOR_STEPS_KEY_PREFIX + geofenceId)
            remove(ANCHOR_SEAL_POS_KEY_PREFIX + geofenceId)
            remove(ANCHOR_SEAL_AT_KEY_PREFIX + geofenceId)
        }
    }

    /** When the OS delivered an EXIT for this fence too far away to act on directly
     *  (trust triage recorded the FACT instead) — half of the conjunction proof.
     *  [DET-CONJUNCTION-001] */
    private fun readExitDeliveredAt(prefs: android.content.SharedPreferences, geofenceId: String): Long? =
        prefs.getLong(EXIT_KEY_PREFIX + geofenceId, 0L).takeIf { it > 0L }

    /** Removes per-geofence anchor + prompt-throttle + exit-evidence keys for geofences with no
     *  active session left (departed / reverted). */
    private fun pruneStaleAnchors(prefs: android.content.SharedPreferences, liveGeofenceIds: Set<String>) {
        val stale = prefs.all.keys.filter { key ->
            when {
                key.startsWith(ANCHOR_SEAL_POS_KEY_PREFIX) -> key.removePrefix(ANCHOR_SEAL_POS_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(ANCHOR_SEAL_AT_KEY_PREFIX) -> key.removePrefix(ANCHOR_SEAL_AT_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(ANCHOR_STEPS_KEY_PREFIX) -> key.removePrefix(ANCHOR_STEPS_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(ANCHOR_KEY_PREFIX) -> key.removePrefix(ANCHOR_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(PROMPT_KEY_PREFIX) -> key.removePrefix(PROMPT_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(EXIT_KEY_PREFIX) -> key.removePrefix(EXIT_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(CURE_KEY_PREFIX) -> key.removePrefix(CURE_KEY_PREFIX) !in liveGeofenceIds
                // [DET-FENCE-REREGISTER-BY-CAUSE-001 §B] Its own branch: "cure_poisoned_" is NOT
                // matched by the "cure_registered_" prefix above, so without this the stamp of a
                // released session would outlive it forever.
                key.startsWith(POISONED_KEY_PREFIX) -> key.removePrefix(POISONED_KEY_PREFIX) !in liveGeofenceIds
                else -> false
            }
        }
        if (stale.isNotEmpty()) {
            prefs.edit { stale.forEach { remove(it) } }
        }
    }

    private fun dismissPrompt() =
        notificationPort.dismiss(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID)

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        /** WorkManager unique-work name — NEVER rename (a rename orphans the installed periodic). */
        const val TAG = "ParkingSafetyNetWorker"
        /** File-log tag: FileAntilog only persists PARKDIAG-prefixed tags, and this worker was
         *  invisible in field captures without it (2026-07-06). */
        private const val DIAG = "PARKDIAG/SafetyNet"
        /** Unique-work name of the pre-[DET-SAFETY-NET-001] watchdog — cancelled on enqueue so the
         *  renamed class never leaves a stale periodic pointing at a missing worker. */
        private const val LEGACY_TAG = "DetectionHeartbeatWorker"
        private const val INTERVAL_MINUTES = 15L
        private const val KMH_PER_MPS = 3.6f

        /** What woke the check up — debug + telemetry breadcrumb (`DEPARTURE_VERDICT.source`). */
        private const val KEY_SOURCE = "source"
        const val SOURCE_PERIODIC = "periodic"
        const val SOURCE_SIG_MOTION = "sig-motion"
        const val SOURCE_APP_START = "app-start"
        const val SOURCE_DETECTION_END = "detection-end"
        /** IN_VEHICLE ENTER accelerator — AR rides a PendingIntent and wakes a dead process,
         *  landing the check MID-DRIVE while the ColorOS geofence EXIT is still minutes away.
         *  [DET-RECONCILE-001] */
        const val SOURCE_AR_ENTER = "ar-enter"
        /** IN_VEHICLE EXIT accelerator — "the user just left a vehicle": the trip is over and a
         *  missed departure is at its most decidable. Receivers keep firing through the OEM
         *  freezes that starve WorkManager (field 2026-07-08, cinema arrivals on both devices).
         *  [DET-CONJUNCTION-001] */
        const val SOURCE_AR_EXIT = "ar-exit"
        /** Twin ENTER fence — the user walked back to the parked car; the check re-seals the
         *  anchor and cures the EXIT fence state for the upcoming drive-away. [DET-RETURN-ANCHOR-001] */
        const val SOURCE_GEOFENCE_ENTER = "geofence-enter"
        /** A geofence EXIT delivered FAR from its own fence (OEM batching held it past the whole
         *  trip). Its trust premise — "fired at the boundary of YOUR fence" — is void, so it gets
         *  no direct departure authority: it is just a wake-up for the evaluator, which demands
         *  the anchor + a ride proof like every other reconcile source. [DET-EXIT-TRUST-001] */
        const val SOURCE_GEOFENCE_EXIT_STALE = "exit-stale"
        /** BT connected to the vehicle's paired MAC — deterministic "back at my car", fires with
         *  the engine and needs no Doze luck. Same re-seal job as the ENTER fence. [DET-RETURN-ANCHOR-001] */
        const val SOURCE_BT_CONNECT = "bt-connect"
        /** A BT-path park just confirmed — seal the fresh session's anchor immediately (the
         *  coordinator path gets this from its detection-end check). [DET-RETURN-ANCHOR-001] */
        const val SOURCE_BT_PARK = "bt-park"
        /** Exact-alarm polling net tick (~5 min while parked) — pierces Doze where the 15-min
         *  WorkManager periodic gets batched; catches departures whose AR/geofence/sig-motion
         *  events were never delivered to a dead process. [DET-EXACT-HEARTBEAT-001] */
        const val SOURCE_EXACT_ALARM = "exact-alarm"

        /** Min interval between "still parked?" prompts per geofence. Persisted to disk (see the
         *  prompt branch) so an OEM process kill can't reset it and re-nag on every app-start. */
        private const val PROMPT_THROTTLE_MS = 6 * 60 * 60 * 1_000L
        /** Per-geofence prompt-throttle timestamp keys, in the same prefs file as the anchor. */
        private const val PROMPT_KEY_PREFIX = "prompt_"

        // [OEM-KILL-001] Heartbeat persistence (must survive process death — SharedPreferences).
        // [DET-HONEST-CLOSE-001] PREFS_NAME + ANCHOR_STEPS_KEY_PREFIX are `internal` so the
        // confirm-time step-anchor sealer (AndroidDetectionStepAnchors) writes/reads the SAME slot —
        // one storage contract, not a duplicated key string.
        internal const val PREFS_NAME = "parking_safety_net"
        private const val KEY_LAST_ALIVE_AT = "last_alive_at"
        private const val KEY_LAST_ALIVE_ELAPSED = "last_alive_elapsed"
        /** [ANCHOR-PERSIST-001] Per-geofence position anchor keys live in the SAME prefs file. */
        private const val ANCHOR_KEY_PREFIX = "anchor_"
        /** [DET-RECONCILE-001] Cumulative step-counter value stored alongside each anchor.
         *  MUST prune before ANCHOR_KEY_PREFIX checks (it shares the prefix). */
        internal const val ANCHOR_STEPS_KEY_PREFIX = "anchor_steps_"
        /** [DET-STEP-BUDGET-ORIGIN-001] "lat,lon" of the body when the steps zero-point was
         *  sealed — the origin any walked-vs-rode displacement must be measured from. Written
         *  atomically with ANCHOR_STEPS; also shares the `anchor_` prefix → prune first. */
        internal const val ANCHOR_SEAL_POS_KEY_PREFIX = "anchor_seal_pos_"
        /** [DET-TRIP-WITNESS-001] Epoch ms when the steps zero-point was sealed — the budget
         *  expires with age (honestCloseMaxSealAgeMs). Written atomically with ANCHOR_STEPS;
         *  also shares the `anchor_` prefix → prune first. */
        internal const val ANCHOR_SEAL_AT_KEY_PREFIX = "anchor_seal_at_"
        /** [DET-CONJUNCTION-001] Delivery timestamp of a far-delivered geofence EXIT, keyed by
         *  geofenceId. Disk-backed like the anchor: the conjunction may only be decidable ticks
         *  (or a process death) later. */
        private const val EXIT_KEY_PREFIX = "exit_delivered_"
        /** [DET-BACKFILL-TAINT-001] The coordinator's latest NUDGE-ONLY arrival resolution
         *  (gap-anchor abort): WHEN it was stamped + the arrival's last fix ("lat,lon"). One
         *  slot, latest wins — it describes THE arrival in flight, not a geofence. Written by
         *  `CoordinatorDetectionService` at the abort; read by [ParkingBackfillWorker], which
         *  defers its placement to the nudge while the stamp is fresh and near. Expires by age
         *  (`arrivalResolutionWindowMs`) — no per-geofence pruning. */
        internal const val KEY_ARRIVAL_RESOLUTION_AT = "arrival_resolution_at"
        internal const val KEY_ARRIVAL_RESOLUTION_POS = "arrival_resolution_pos"
        /** [DET-UNWITNESSED-DISPLACEMENT-001] Last independently witnessed position of the BODY
         *  ("lat,lon" + accuracy + epoch ms): the end fix of every detection session and every
         *  safety-net check fix, latest wins. The honest close holds the next abort fix to
         *  spatio-temporal coherence against it — an indoor-multipath fix that teleports 950 m in
         *  32 s between two stationary observations can no longer "prove" a trip (field
         *  2026-08-19 03:26). One slot, no per-geofence pruning: it describes the body, not a
         *  fence. Disk-backed so an OEM kill between wakes cannot blind the check. */
        internal const val KEY_LAST_WITNESSED_POS = "last_witnessed_pos"
        internal const val KEY_LAST_WITNESSED_ACC = "last_witnessed_acc"
        internal const val KEY_LAST_WITNESSED_AT = "last_witnessed_at"
        /** [DET-DEPARTURE-IS-NOT-ARRIVAL-001] Cumulative step-counter sample belonging to that same
         *  witness seal. Its delta against the next reading is the ARRIVAL budget — the walk the
         *  body made SINCE we last saw it — and it is the only budget allowed to bound a backfilled
         *  pin. The anchor budget cannot: every branch that reconstructs a departure has already
         *  spent it proving the ride (field 2026-08-24 19:34, Xiaomi: the same 97 steps released
         *  the spot AND "bounded" a pin 2 976 m away, at a red light). Removed rather than left
         *  stale when the counter cannot be read. */
        internal const val KEY_LAST_WITNESSED_STEPS = "last_witnessed_steps"

        /** [DET-DEPARTURE-IS-NOT-ARRIVAL-001] Telemetry outcome when the departure was dispatched
         *  but the arrival was deliberately NOT placed: the step budget that proved the ride cannot
         *  also bound the new pin, and no independent arrival walk was measured. Distinguishes "the
         *  net refused to guess" from "the net never ran". */
        private const val OUTCOME_ARRIVAL_UNWITNESSED = "BACKFILL_ARRIVAL_UNWITNESSED"
        /** Provenance path the refused placement WOULD have carried, so the two show up in the same
         *  bucket as [ParkingBackfillWorker]'s own traces. */
        private const val PATH_SAFETY_NET_BACKFILL = "safety_net_backfill"
        /** [DET-ANCHOR-FREEZE-001 F4] Last GMS re-registration per fence — the cure throttle's
         *  disk half (the in-process half is [curedFencesThisProcess]). */
        private const val CURE_KEY_PREFIX = "cure_registered_"

        /** [DET-FENCE-REREGISTER-BY-CAUSE-001 §B] Epoch ms when a dismissed false EXIT left this
         *  fence's INSIDE/OUTSIDE state poisoned. Written by [markFenceStatePoisoned], consumed by
         *  the cure that repairs it — a cause, stamped, not a clock deleted. Pruned by its OWN
         *  branch in [pruneStaleAnchors]: `cure_registered_` does not match it. */
        private const val POISONED_KEY_PREFIX = "cure_poisoned_"

        /** [DET-FENCE-REREGISTER-BY-CAUSE-001 §D] Which lane asked for a registration, so a
         *  remote trace can tell the gated cure from the ungated janitor. */
        internal const val REGISTRATION_SOURCE_CURE = "cure"
        /** Fences already re-registered by THIS process — a process start means force-stop/app
         *  update may have wiped GMS registrations, so the first cure after it always runs. */
        private val curedFencesThisProcess: MutableSet<String> =
            java.util.concurrent.ConcurrentHashMap.newKeySet()

        /**
         * [DET-FENCE-REREGISTER-BY-CAUSE-001 §B] Records that Play Services' INSIDE/OUTSIDE state
         * for [geofenceId] is POISONED: a delivered EXIT was judged false (walking / GPS drift), so
         * GMS now believes the phone is outside a fence it is still holding, and the next real
         * drive-away will produce nothing at all (field 2026-07-04, Calle Gavia).
         *
         * This replaces the old `clearCureThrottle`, which said the same thing by DELETING the
         * throttle key. That was ambiguous by construction — an absent key means "poisoned" and
         * "never cured" and "just installed", three situations that deserve different answers — and
         * it expressed a cause as the absence of a clock. A stamp says it out loud, survives process
         * death, and is CONSUMED by the cure that acts on it, so one poisoning buys exactly one
         * re-registration.
         */
        fun markFenceStatePoisoned(context: Context, geofenceId: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(POISONED_KEY_PREFIX + geofenceId, System.currentTimeMillis())
                .apply()
        }

        /**
         * Records the FACT that the OS delivered a geofence EXIT for [geofenceId] — called by the
         * trust triage when delivery lands too far from the fence to grant departure authority
         * ([DET-EXIT-TRUST-001]). The evaluator pairs it with an independent AR boarding: the two
         * agreeing within [ParkingDetectionConfig.exitEnterPairWindowMs] prove the drive-away that
         * neither could prove alone (field 2026-07-08, cinema trips on BOTH devices).
         * [DET-CONJUNCTION-001]
         */
        fun recordStaleExitDelivery(context: Context, geofenceId: String, deliveredAtMs: Long) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(EXIT_KEY_PREFIX + geofenceId, deliveredAtMs)
                .apply()
        }

        /**
         * Whether ANY session's fence reported a far-delivered EXIT within [maxAgeMs] of [nowMs].
         * Cheap synchronous read for the AR receiver: a fresh IN_VEHICLE event paired with a
         * recently-broken fence is the live half of the conjunction, and the AR broadcast is the
         * only moment the OS exempts a background foreground-service start — the escalation must
         * happen THERE or not at all (a worker's start is denied — field 2026-07-09 13:55).
         * [DET-RIDE-PROOF-001]
         */
        fun hasRecentStaleExit(context: Context, nowMs: Long, maxAgeMs: Long): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all.any { (key, value) ->
                key.startsWith(EXIT_KEY_PREFIX) && value is Long && (nowMs - value) in 0..maxAgeMs
            }

        /** Heartbeat gap above which a background freeze is logged (SILENT telemetry). Deep Doze
         *  legitimately defers 15-min periodics for hours, so this cannot distinguish a harmful
         *  OEM kill from ordinary idle — hence telemetry only, never a user warning. */
        private const val KILL_GAP_THRESHOLD_MS = 3 * 60 * 60 * 1_000L

        /** [OEM-KILL-001] `wasForceStopped()` describes the CURRENT process start — check it once
         *  per process, not on every 15-min tick of a long-lived process. */
        @Volatile
        private var forceStopCheckedThisProcess = false

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<ParkingSafetyNetWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

        fun enqueueKeep(workManager: WorkManager) {
            workManager.cancelUniqueWork(LEGACY_TAG)
            workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                buildPeriodicRequest(),
            )
        }

        /**
         * Immediate one-shot check, distinct from the 15-min periodic: enqueued by the
         * significant-motion trigger [DET-SIGMOTION-001] and by detection teardown (so the sensor
         * is re-armed seconds after a park instead of waiting for the next periodic tick).
         * Expedited where quota allows — a sensor callback cannot legally start an FGS on
         * Android 12+, and expedited work is the sanctioned fast lane.
         */
        fun enqueueCheckNow(workManager: WorkManager, source: String) {
            workManager.enqueueUniqueWork(
                "${TAG}_now",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ParkingSafetyNetWorker>()
                    .setInputData(workDataOf(KEY_SOURCE to source))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(TAG)
                    .build(),
            )
        }
    }
}
