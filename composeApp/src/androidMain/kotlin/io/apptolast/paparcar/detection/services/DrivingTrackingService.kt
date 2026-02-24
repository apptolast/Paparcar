package io.apptolast.paparcar.detection.services

import android.app.Notification
import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.apptolast.paparcar.data.notification.AppNotificationManager
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.notification.BuildSpotDetectionNotificationUseCase
import io.apptolast.paparcar.domain.usecase.parking.DetectAndReportParkingUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DrivingTrackingService : LifecycleService() {

    private val detectAndReportParking: DetectAndReportParkingUseCase by inject()
    private val observeAdaptiveLocation: ObserveAdaptiveLocationUseCase by inject()
    private val buildSpotDetectionNotification: BuildSpotDetectionNotificationUseCase by inject()
    private var detectionJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_TRACKING -> {
                if (detectionJob == null) {
                    val notification = buildSpotDetectionNotification() as Notification
                    startForeground(AppNotificationManager.DETECTION_NOTIFICATION_ID, notification)
                    startParkingDetection()
                }
            }
            ACTION_VEHICLE_EXIT -> {
                detectAndReportParking.onVehicleExit()
            }
            ACTION_PARKING_CONFIRMED -> {
                detectAndReportParking.onUserConfirmedParking()
            }
            ACTION_PARKING_DENIED -> {
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
            detectAndReportParking(observeAdaptiveLocation())
            stopSelf()
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