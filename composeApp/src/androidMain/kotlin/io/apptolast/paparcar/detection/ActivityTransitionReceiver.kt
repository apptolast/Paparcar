package io.apptolast.paparcar.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.apptolast.paparcar.detection.services.DrivingTrackingService
import io.apptolast.paparcar.domain.usecase.notification.ShowDebugNotificationUseCase
import org.koin.mp.KoinPlatform.getKoin

class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val showDebugNotificationUseCase: ShowDebugNotificationUseCase = getKoin().get()

        if (ActivityTransitionResult.hasResult(intent)) {
//            startDrivingService(context, DrivingTrackingService.ACTION_START_TRACKING)

            val result = ActivityTransitionResult.extractResult(intent)

            result?.transitionEvents?.forEach { event ->
                val activityType = toActivityString(event.activityType)
                val transitionType = toTransitionTypeString(event.transitionType)
                showDebugNotificationUseCase("Transición real: $activityType ($transitionType)")

                when {
                    event.activityType == DetectedActivity.IN_VEHICLE &&
                            event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                        startDrivingService(context, DrivingTrackingService.ACTION_START_TRACKING)
                    }

                    event.activityType == DetectedActivity.IN_VEHICLE &&
                            event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                        startDrivingService(context, DrivingTrackingService.ACTION_VEHICLE_EXIT)
                    }
                }
            }
        }
    }

    private fun startDrivingService(context: Context, serviceAction: String?) {
        val serviceIntent = Intent(context, DrivingTrackingService::class.java).apply {
            action = serviceAction
        }
        context.startForegroundService(serviceIntent)
    }

    private fun toActivityString(activity: Int): String = when (activity) {
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
        else -> "UNKNOWN"
    }

    private fun toTransitionTypeString(transitionType: Int): String = when (transitionType) {
        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
        else -> "UNKNOWN"
    }

    companion object {
        const val REQUEST_CODE = 101
    }
}
