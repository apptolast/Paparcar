package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ActivityTransitionReceiver : BroadcastReceiver(), KoinComponent {

    private val coordinator: ParkingDetectionCoordinator by inject()

    override fun onReceive(context: Context, intent: Intent) {
        PaparcarLogger.d(TAG, "▶ onReceive hasResult=${ActivityTransitionResult.hasResult(intent)}")
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: run {
            PaparcarLogger.w(TAG, "  extractResult returned null")
            return
        }

        PaparcarLogger.d(TAG, "  events=${result.transitionEvents.size}")
        result.transitionEvents.forEach { event ->
            val activity = activityLabel(event.activityType)
            val transition = transitionLabel(event.transitionType)
            PaparcarLogger.d(TAG, "  → $activity $transition")

            if (event.activityType == DetectedActivity.STILL &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                PaparcarLogger.d(TAG, "  ✓ STILL ENTER — forwarding to coordinator")
                coordinator.onStillDetected()
            }
        }
    }

    private fun activityLabel(type: Int) = when (type) {
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
        DetectedActivity.ON_FOOT -> "ON_FOOT"
        else -> "UNKNOWN($type)"
    }

    private fun transitionLabel(type: Int) = when (type) {
        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
        else -> "UNKNOWN($type)"
    }

    companion object {
        const val REQUEST_CODE = 101
        private const val TAG = "PARKDIAG/ARReceiver"
    }
}
