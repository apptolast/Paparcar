package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.detection.FenceRegistrationLedger
import io.apptolast.paparcar.detection.geofenceFailureDetail
import io.apptolast.paparcar.detection.toGeofenceRegistrationFailure
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.detection.fence.VehicleFenceOwnershipPolicy
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that re-registers geofences for all active parking sessions.
 *
 * Geofences in [GeofenceManagerImpl] are now created with `NEVER_EXPIRE`, so this worker is
 * no longer a TTL refresher — it is the **restoration** path. Play Services drops every
 * registered geofence on device reboot and on reinstall; after either event the geofences in
 * GMS no longer match the active sessions still present in Room (reboot) or freshly synced from
 * Firestore (reinstall). [BootCompletedReceiver] and app start re-enqueue this worker, which
 * reads the active sessions and re-registers their geofences. Running periodically also
 * self-heals any registration that was lost while the process was dead. [GEOF-001]
 *
 * Re-adding an existing geofence via [GeofenceManager.createGeofence] is idempotent
 * because [android.app.PendingIntent.FLAG_UPDATE_CURRENT] replaces the existing entry.
 * [GeofencingRequest] uses [setInitialTrigger(0)] so no spurious exit event fires.
 */
class GeofenceJanitorWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val db: AppDatabase by inject()
    private val geofenceManager: GeofenceManager by inject()
    private val config: ParkingDetectionConfig by inject()
    private val detectionEventLogger: DetectionEventLogger by inject()

    override suspend fun doWork(): Result {
        // [DET-JANITOR-LANE-TELLS-ONCE-FROM-PERIODIC-001] Which clock asked for this run. The
        // periodic request carries no input data (installed periodics survive with KEEP and are
        // never re-created), so ABSENCE honestly means "periodic" for old and new installs alike.
        val source = registrationSource(inputData.getString(KEY_TRIGGER))
        PaparcarLogger.d(TAG, "▶ GeofenceJanitorWorker.doWork attempt=$runAttemptCount source=$source")

        val activeSessions = runCatching { db.parkingSessionDao().getAllActive() }
            .getOrElse {
                PaparcarLogger.e(TAG, "✗ failed to read active sessions", it)
                return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            }

        PaparcarLogger.d(TAG, "  active sessions=${activeSessions.size}")

        // [DET-SOLID-001] Self-repair sweep: replaceActiveSession's transaction should make
        // duplicate actives impossible; if any exist anyway (legacy rows, sync races), keep the
        // newest per vehicle and deactivate the rest — loud, because it is an invariant violation.
        val duplicates = runCatching { db.parkingSessionDao().getActiveDuplicates() }.getOrDefault(emptyList())
        if (duplicates.isNotEmpty()) {
            PaparcarLogger.w(TAG, "  ⚠ ${duplicates.size} duplicate ACTIVE sessions detected — repairing [DET-SOLID-001]")
            val now = System.currentTimeMillis()
            duplicates.groupBy { it.vehicleId }.forEach { (_, rows) ->
                rows.sortedByDescending { it.timestamp }.drop(1).forEach { stale ->
                    // Stamp the dedup deactivation pending so it propagates to Firestore too — the
                    // single enforcement point of the one-active-per-vehicle invariant.
                    // [SYNC-RECONCILE-USERPARKING-001]
                    // A dedup close is a supersede: it ends the stale row now and published
                    // nothing. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
                    runCatching {
                        db.parkingSessionDao().clearActiveById(stale.id, endedAtMs = now, publishedSpot = false, now = now)
                    }
                }
            }
        }

        var failures = 0
        activeSessions.forEach { session ->
            val geofenceId = session.geofenceId ?: return@forEach
            // [VEH-ACTIVE-FENCE-001] Only re-register fences the active (or BT-paired) vehicle owns.
            // An inactive vehicle's session is deliberately fenceless (confirm skipped it, 2a); the
            // cure must NOT resurrect it, or the spurious-FGS noise comes back on every janitor run.
            val vehicle = session.vehicleId?.let {
                runCatching { db.vehicleDao().getById(it, session.userId) }.getOrNull()
            }
            if (vehicle == null ||
                !VehicleFenceOwnershipPolicy.shouldOwnFence(vehicle.isActive, vehicle.bluetoothDeviceId != null)
            ) {
                PaparcarLogger.d(TAG, "  ⊘ skip re-register geof=$geofenceId — vehicle not active/BT [VEH-ACTIVE-FENCE-001]")
                return@forEach
            }
            // Re-register with the SAME size/accuracy-aware radius the session was first created with
            // (ConfirmParkingUseCase.geofenceRadiusFor), not the flat default — otherwise a restored
            // geofence drifts to a different exit sensitivity than the original. [SESSION-RESTORE-001]
            val size = session.sizeCategory?.let { runCatching { VehicleSize.valueOf(it) }.getOrNull() }
            // [DET-FENCE-REREGISTER-BY-CAUSE-001 §A] Skip only what we can PROVE is redundant: a
            // registration this same process already performed moments ago. Measured 2026-08-20:
            // `PaparcarApp` and the post-sync scheduler both enqueue this worker on app start, and
            // it ran twice 4.3 s apart — two INSIDE/OUTSIDE blind windows for one restoration.
            // A fresh process always registers: that is the force-stop case and we cannot detect it.
            val nowMs = System.currentTimeMillis()
            if (!FenceRegistrationLedger.shouldRegister(geofenceId, nowMs, config.fenceRegisterDedupWindowMs)) {
                PaparcarLogger.d(TAG, "  ⊘ skip re-register geof=$geofenceId — this process registered it moments ago [DET-FENCE-REREGISTER-BY-CAUSE-001]")
                return@forEach
            }
            val result = geofenceManager.createGeofence(
                geofenceId = geofenceId,
                latitude = session.latitude,
                longitude = session.longitude,
                radiusMeters = config.geofenceRadiusFor(size, session.accuracy),
            )
            val cause = result.exceptionOrNull()
            if (cause != null) {
                PaparcarLogger.w(TAG, "  ⚠ failed to re-register geofence=$geofenceId: ${cause.geofenceFailureDetail()}", cause)
                failures++
            } else {
                // The ledger entry is written inside GeofenceManagerImpl, where every registration
                // path converges — a failed attempt never reaches it, which is right: it left no
                // fence behind and opened no blind window, so it must not block the next attempt.
                PaparcarLogger.d(TAG, "  ✓ re-registered geofence=$geofenceId")
            }
            // [DET-FENCE-REREGISTER-BY-CAUSE-001 §D] This lane was invisible in remote telemetry —
            // and it is the UNGATED one (no distance check, no fresh-fix check, no throttle), so it
            // is the likelier source of the INSIDE/OUTSIDE blind window. Instrumenting it is the
            // whole point of doing §D before touching the policy: we need to see how often it fires
            // and on whose behalf before deciding what to keep.
            runCatching {
                detectionEventLogger.log(
                    DetectionEvent.GeofenceRegistration(
                        sessionId = geofenceId,
                        timestampMs = System.currentTimeMillis(),
                        success = cause == null,
                        radiusMeters = config.geofenceRadiusFor(size, session.accuracy),
                        source = source,
                        failure = cause?.toGeofenceRegistrationFailure(),
                    )
                )
            }
        }

        return if (failures == 0) {
            PaparcarLogger.d(TAG, "■ GeofenceJanitorWorker SUCCESS")
            Result.success()
        } else if (runAttemptCount < MAX_RETRIES) {
            PaparcarLogger.w(TAG, "⚠ $failures geofences failed — retrying")
            Result.retry()
        } else {
            PaparcarLogger.e(TAG, "✗ giving up after $MAX_RETRIES retries, $failures geofences unregistered")
            Result.failure()
        }
    }

    companion object {
        const val TAG = "GeofenceJanitorWorker"

        /** [DET-FENCE-REREGISTER-BY-CAUSE-001 §D] Lane label for the registration event. Kept as
         *  the PREFIX of every janitor source, so remote queries group the lane with a
         *  starts-with. [DET-JANITOR-LANE-TELLS-ONCE-FROM-PERIODIC-001] */
        internal const val REGISTRATION_SOURCE_JANITOR = "janitor"
        private const val INTERVAL_HOURS = 12L
        private const val MAX_RETRIES = 3

        /** [DET-JANITOR-LANE-TELLS-ONCE-FROM-PERIODIC-001] Which clock enqueued this run — rides
         *  the one-time request's input data; the periodic request stays data-less on purpose. */
        private const val KEY_TRIGGER = "trigger"
        const val TRIGGER_BOOT = "boot"
        const val TRIGGER_APP_UPDATE = "app-update"
        const val TRIGGER_APP_START = "app-start"
        const val TRIGGER_POST_SYNC = "post-sync"
        internal const val TRIGGER_PERIODIC = "periodic"

        /** The registration event's `source`: `janitor:<trigger>`, with absent input data reading
         *  as the periodic (see [KEY_TRIGGER]). Pure so the label contract is unit-testable. */
        internal fun registrationSource(trigger: String?): String =
            "$REGISTRATION_SOURCE_JANITOR:${trigger ?: TRIGGER_PERIODIC}"

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<GeofenceJanitorWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .addTag(TAG)
                .build()

        fun enqueueKeep(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                buildPeriodicRequest(),
            )
        }

        /**
         * [GEOF-001] Immediate one-time restoration pass, distinct from the 12 h periodic. Enqueued
         * right after a post-login `syncFromRemote` repopulates Room, so a reinstall/reboot gets its
         * geofence re-registered within seconds instead of waiting for the periodic's next run.
         * `REPLACE` keeps rapid duplicate enqueues idempotent; no constraints so it runs ASAP.
         *
         * [trigger] names the clock that asked (TRIGGER_*) — it becomes the registration event's
         * `source` so remote telemetry can tell the once lanes from the 12 h periodic, which is
         * the data DET-FENCE-REREGISTER-BY-CAUSE-001 §D still needs before any policy cut.
         * [DET-JANITOR-LANE-TELLS-ONCE-FROM-PERIODIC-001]
         */
        fun enqueueOnce(workManager: WorkManager, trigger: String) {
            workManager.enqueueUniqueWork(
                "${TAG}_once",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<GeofenceJanitorWorker>()
                    .setInputData(workDataOf(KEY_TRIGGER to trigger))
                    .addTag(TAG)
                    .build(),
            )
        }
    }
}
