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
import io.apptolast.paparcar.domain.detection.DetectionRuntimeState
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.usecase.location.GetLastKnownLocationUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.domain.util.haversineMeters
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Periodic watchdog (every 15 min) that surfaces a **low-confidence** "still parked?" prompt when a
 * geofence EXIT was almost certainly missed. [DET-AR-REARM-001 / WATCHDOG]
 *
 * Detection is a serial geofence-gated loop: after a park is confirmed the loop goes idle and only
 * re-arms on a fresh trigger. If the user drives away but Play Services drops the EXIT (Doze, an
 * aggressive OEM), the spot is never released and the loop stalls — and no automatic signal recovers
 * it. This watchdog is the last-resort recovery.
 *
 * **Why a prompt and never a silent release.** At poll time the departure speed is long gone, so
 * "the phone is far from the car" cannot tell *drove away* from *walked / took a bus / got picked up*
 * (the car may still be parked). Only the user can disambiguate. A silent release here would
 * re-introduce exactly the bus/taxi false positives the geofence was built to kill.
 *
 * **Why it does not nag.** It fires ONLY when ALL hold: an active parked session exists, detection is
 * idle (not mid-trip), the phone is beyond [ParkingDetectionConfig.watchdogFarThresholdMeters] from
 * the nearest parked car, AND an IN_VEHICLE_ENTER was recorded within
 * [ParkingDetectionConfig.vehicleEnterWindowMs]. The vehicle-signal requirement is what prevents the
 * normal "park and walk away" case (no vehicle signal, far all day) from ever prompting, and the
 * 30-min window self-bounds the prompt so it cannot linger all day. When the conditions lapse, any
 * showing prompt is dismissed.
 */
class DetectionHeartbeatWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val db: AppDatabase by inject()
    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val getLastKnownLocation: GetLastKnownLocationUseCase by inject() // passive — no geofence provocation
    private val notificationPort: AppNotificationManager by inject()
    private val detectionRuntime: DetectionRuntimeState by inject()
    private val departureEventBus: DepartureEventBus by inject()
    private val config: ParkingDetectionConfig by inject()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            AppNotificationManager.DETECTION_NOTIFICATION_ID,
            foregroundNotificationProvider.buildDetectionNotification(),
        )

    override suspend fun doWork(): Result {
        // [DET-AR-REARM-001] Watchdog TEMPORARILY DISABLED to isolate the spurious-geofence-exit
        // regression. With this off AND the AR proximity gate on passive last-known, NOTHING polls
        // active GPS while parked → the geofence should stay quiet. Flip [WATCHDOG_ENABLED] back to
        // true to restore the "still parked?" prompt once the geofence is confirmed quiet.
        if (!WATCHDOG_ENABLED) {
            PaparcarLogger.d(TAG, "■ watchdog disabled (isolation) — skipping")
            return Result.success()
        }

        val activeSessions = runCatching { db.parkingSessionDao().getAllActive() }
            .getOrElse {
                PaparcarLogger.e(TAG, "✗ failed to read active sessions", it)
                return Result.success()
            }

        if (activeSessions.isEmpty()) {
            dismissPrompt()
            return Result.success()
        }

        // Mid-trip: the coordinator is actively running, so the loop is not stalled.
        if (detectionRuntime.isRunning.value) {
            PaparcarLogger.d(TAG, "■ detection running — not a stalled loop, skipping")
            return Result.success()
        }

        // No recent vehicle signal → this is the normal "parked and walked away" state, NOT a missed
        // departure. Never prompt on distance alone (it would nag all day). Clear any stale prompt.
        val enteredAt = departureEventBus.lastVehicleEnteredAt
        val now = System.currentTimeMillis()
        val recentVehicleEnter = enteredAt != null && (now - enteredAt) <= config.vehicleEnterWindowMs
        if (!recentVehicleEnter) {
            dismissPrompt()
            return Result.success()
        }

        val fix = getLastKnownLocation() ?: return Result.success()
        val nearest = activeSessions.minByOrNull {
            haversineMeters(fix.latitude, fix.longitude, it.latitude, it.longitude)
        } ?: return Result.success()
        val distance = haversineMeters(fix.latitude, fix.longitude, nearest.latitude, nearest.longitude)

        val geofenceId = nearest.geofenceId
        if (distance > config.watchdogFarThresholdMeters && geofenceId != null) {
            PaparcarLogger.d(TAG, "▶ missed-exit suspected (d=${distance.toInt()}m, recent vehicle enter) — prompt")
            notificationPort.showStillParkedPrompt(
                geofenceId = geofenceId,
                latitude = nearest.latitude,
                longitude = nearest.longitude,
            )
        } else {
            dismissPrompt()
        }
        return Result.success()
    }

    private fun dismissPrompt() =
        notificationPort.dismiss(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID)

    companion object {
        const val TAG = "DetectionHeartbeatWorker"
        // [DET-AR-REARM-001] Isolation toggle. Non-const so the disabled body doesn't compile to
        // unreachable code. Set true to re-enable the "still parked?" watchdog prompt.
        private val WATCHDOG_ENABLED = false
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
