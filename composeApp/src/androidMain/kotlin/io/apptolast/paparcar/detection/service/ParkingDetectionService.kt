package io.apptolast.paparcar.detection.service

import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ParkingDetectionService : LifecycleService() {

    private val parkingDetectionCoordinator: ParkingDetectionCoordinator by inject()
    private val observeAdaptiveLocation: ObserveAdaptiveLocationUseCase by inject()
    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val notificationPort: AppNotificationManager by inject()
    private var detectionJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_TRACKING -> {
                // If a real trip is already in progress (coordinator has confirmed movement),
                // the incoming IN_VEHICLE_ENTER is likely a spurious batched/delayed duplicate.
                // Restarting would destroy accumulated stop data and leave the service stuck
                // with hasEverMoved=false if the user is already parked.
                // Only restart if no real movement has been detected yet (false start) or if
                // no detection job is running.
                if (detectionJob?.isActive == true && parkingDetectionCoordinator.hasDetectedMovement) {
                    return START_STICKY
                }
                detectionJob?.cancel()
                detectionJob = null
                val notification = foregroundNotificationProvider.buildDetectionNotification()
                startForeground(AppNotificationManager.DETECTION_NOTIFICATION_ID, notification)
                startParkingDetection()
            }
            ACTION_VEHICLE_EXIT -> {
                parkingDetectionCoordinator.onVehicleExit()
            }
            ACTION_PARKING_CONFIRMED -> {
                // Notification is dismissed inside onUserConfirmedParking().
                parkingDetectionCoordinator.onUserConfirmedParking()
            }
            ACTION_PARKING_DENIED -> {
                // Notification is dismissed inside onUserDeniedParking().
                parkingDetectionCoordinator.onUserDeniedParking()
            }
            ACTION_STOP_TRACKING -> {
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startParkingDetection() {
        detectionJob = lifecycleScope.launch {
            try {
                parkingDetectionCoordinator(observeAdaptiveLocation())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notificationPort.showDebug("Detection error: ${e.message}")
            } finally {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        detectionJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_TRACKING = "io.apptolast.paparcar.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "io.apptolast.paparcar.ACTION_STOP_TRACKING"
        const val ACTION_VEHICLE_EXIT = "io.apptolast.paparcar.ACTION_VEHICLE_EXIT"
        const val ACTION_PARKING_CONFIRMED = "io.apptolast.paparcar.ACTION_PARKING_CONFIRMED"
        const val ACTION_PARKING_DENIED = "io.apptolast.paparcar.ACTION_PARKING_DENIED"
    }
}