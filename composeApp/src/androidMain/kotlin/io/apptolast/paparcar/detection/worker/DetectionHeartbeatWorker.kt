package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Periodic heartbeat that verifies detection state every 15 minutes.
 *
 * When confirmed parking sessions exist in Room the user is parked — no coordinator restart
 * is needed. Departure detection resumes automatically: IN_VEHICLE_ENTER is delivered via
 * [PendingIntent.getForegroundService] directly from Play Services, which starts
 * [ParkingDetectionService] even if the process is dead. [HEARTBEAT-001]
 *
 * When no sessions exist the user may be mid-drive with no confirmed parking yet; the
 * coordinator is already running (or will be restarted by START_STICKY), so we also skip.
 *
 * The worker is kept enrolled so that OEM Doze restrictions do not silently cancel it.
 */
class DetectionHeartbeatWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val db: AppDatabase by inject()
    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
// FIXME: Analizar bien esta clase
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            AppNotificationManager.DETECTION_NOTIFICATION_ID,
            foregroundNotificationProvider.buildDetectionNotification(),
        )

    override suspend fun doWork(): Result {
        val activeSessions = runCatching { db.parkingSessionDao().getAllActive() }
            .getOrElse {
                PaparcarLogger.e(TAG, "✗ failed to read active sessions", it)
                return Result.success()
            }

        if (activeSessions.isEmpty()) {
            PaparcarLogger.d(TAG, "■ no active sessions — skipping service restart")
            return Result.success()
        }

        // Confirmed parking sessions exist → user is parked, not driving. The coordinator must NOT
        // run here: it would start GPS scanning and show a foreground notification needlessly.
        // When the user drives away, IN_VEHICLE_ENTER is delivered via PendingIntent.getForegroundService()
        // directly from Play Services, which starts the service even if it is dead. [HEARTBEAT-001]
        PaparcarLogger.d(TAG, "■ ${activeSessions.size} confirmed session(s) — skipping restart (AR handles departure)")
        return Result.success()
    }

    companion object {
        const val TAG = "DetectionHeartbeatWorker"
        private const val INTERVAL_MINUTES = 15L

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<DetectionHeartbeatWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
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
