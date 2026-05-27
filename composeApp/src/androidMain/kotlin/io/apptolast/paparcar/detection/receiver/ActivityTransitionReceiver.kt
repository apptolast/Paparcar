package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.apptolast.paparcar.detection.activityLabel
import io.apptolast.paparcar.detection.transitionLabel
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
            PaparcarLogger.d(TAG, "  → ${activityLabel(event.activityType)} ${transitionLabel(event.transitionType)}")

            if (event.activityType == DetectedActivity.STILL &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                PaparcarLogger.d(TAG, "  ✓ STILL ENTER — forwarding to coordinator")
                coordinator.onStillDetected()
            }
        }
    }

    private companion object {
        const val TAG = "PARKDIAG/ARReceiver"
    }
}
