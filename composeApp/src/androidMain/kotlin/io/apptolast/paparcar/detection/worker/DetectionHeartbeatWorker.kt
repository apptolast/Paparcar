package io.apptolast.paparcar.detection.worker

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.detection.service.ParkingDetectionService
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that restarts [ParkingDetectionService] if it was killed by the OS
 * while an active parking session exists in Room.
 *
 * Runs every 15 minutes. On Doze-aggressive OEMs (MIUI, ColorOS, EMUI) the foreground
 * service can be killed despite START_STICKY. This worker acts as a safety net:
 * it fires unconditionally but [ParkingDetectionService] deduplicates the start
 * (early-return if `detectionJob` is active and coordinator has detected movement).
 *
 * Starting a foreground service from a WorkManager context is allowed as long as the
 * app is not in a background-restricted state. On Android 12+ the system may throw
 * [android.app.ForegroundServiceStartNotAllowedException]; we catch and log it rather
 * than crashing — the next heartbeat will retry. [DOZE-001]
 */
class DetectionHeartbeatWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val db: AppDatabase by inject()

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

        PaparcarLogger.d(TAG, "▶ ${activeSessions.size} active session(s) — (re)starting detection service")
        try {
            val intent = Intent(appContext, ParkingDetectionService::class.java).apply {
                action = ParkingDetectionService.ACTION_START_TRACKING
            }
            appContext.startForegroundService(intent)
        } catch (e: Exception) {
            PaparcarLogger.w(TAG, "⚠ startForegroundService denied — next heartbeat will retry", e)
        }
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
