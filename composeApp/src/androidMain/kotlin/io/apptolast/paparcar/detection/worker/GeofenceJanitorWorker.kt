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
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
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

    override suspend fun doWork(): Result {
        PaparcarLogger.d(TAG, "▶ GeofenceJanitorWorker.doWork attempt=$runAttemptCount")

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
            duplicates.groupBy { it.vehicleId }.forEach { (_, rows) ->
                rows.sortedByDescending { it.timestamp }.drop(1).forEach { stale ->
                    runCatching { db.parkingSessionDao().clearActiveById(stale.id) }
                }
            }
        }

        var failures = 0
        activeSessions.forEach { session ->
            val geofenceId = session.geofenceId ?: return@forEach
            // Re-register with the SAME size/accuracy-aware radius the session was first created with
            // (ConfirmParkingUseCase.geofenceRadiusFor), not the flat default — otherwise a restored
            // geofence drifts to a different exit sensitivity than the original. [SESSION-RESTORE-001]
            val size = session.sizeCategory?.let { runCatching { VehicleSize.valueOf(it) }.getOrNull() }
            val result = geofenceManager.createGeofence(
                geofenceId = geofenceId,
                latitude = session.latitude,
                longitude = session.longitude,
                radiusMeters = config.geofenceRadiusFor(size, session.accuracy),
            )
            if (result.isFailure) {
                PaparcarLogger.w(TAG, "  ⚠ failed to re-register geofence=$geofenceId", result.exceptionOrNull())
                failures++
            } else {
                PaparcarLogger.d(TAG, "  ✓ re-registered geofence=$geofenceId")
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
        private const val INTERVAL_HOURS = 12L
        private const val MAX_RETRIES = 3

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
         */
        fun enqueueOnce(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                "${TAG}_once",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<GeofenceJanitorWorker>()
                    .addTag(TAG)
                    .build(),
            )
        }
    }
}
