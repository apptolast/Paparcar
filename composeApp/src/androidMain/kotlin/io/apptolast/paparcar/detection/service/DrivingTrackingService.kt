package io.apptolast.paparcar.detection.service

import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.apptolast.paparcar.domain.notification.NotificationPort
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.DetectAndReportParkingUseCase
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DrivingTrackingService : LifecycleService() {

    private val detectAndReportParking: DetectAndReportParkingUseCase by inject()
    private val observeAdaptiveLocation: ObserveAdaptiveLocationUseCase by inject()
    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val notificationPort: NotificationPort by inject()
    private var detectionJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_TRACKING -> {
                // Always restart: a new IN_VEHICLE_ENTER supersedes any in-progress session
                // (e.g. user parked briefly, got back in car before confirming).
                // DetectAndReportParkingUseCase.invoke() dismisses any pending confirmation
                // notification automatically at the start of each new session.
                detectionJob?.cancel()
                detectionJob = null
                val notification = foregroundNotificationProvider.buildDetectionNotification()
                startForeground(NotificationPort.DETECTION_NOTIFICATION_ID, notification)
                startParkingDetection()
            }
            ACTION_VEHICLE_EXIT -> {
                detectAndReportParking.onVehicleExit()
            }
            ACTION_PARKING_CONFIRMED -> {
                // Notification is dismissed inside onUserConfirmedParking().
                detectAndReportParking.onUserConfirmedParking()
            }
            ACTION_PARKING_DENIED -> {
                // Notification is dismissed inside onUserDeniedParking().
                detectAndReportParking.onUserDeniedParking()
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
                detectAndReportParking(observeAdaptiveLocation())
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