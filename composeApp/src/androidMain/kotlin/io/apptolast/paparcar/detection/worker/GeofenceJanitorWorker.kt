package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that re-registers geofences for all active parking sessions.
 *
 * Geofences in [GeofenceManagerImpl] are created with a 24-hour TTL to avoid
 * orphan accumulation after process kills. This worker runs every 12 hours and
 * re-registers them before they expire, ensuring exit-transition detection stays
 * active as long as the session is live in Room. [GEOF-001]
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

        var failures = 0
        activeSessions.forEach { session ->
            val geofenceId = session.geofenceId ?: return@forEach
            val result = geofenceManager.createGeofence(
                geofenceId = geofenceId,
                latitude = session.latitude,
                longitude = session.longitude,
                radiusMeters = config.geofenceRadiusMeters,
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
    }
}
