package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ActivityTransitionReceiver : BroadcastReceiver(), KoinComponent {

    private val coordinator: ParkingDetectionCoordinator by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        result.transitionEvents.forEach { event ->
            if (event.activityType == DetectedActivity.STILL &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                coordinator.onStillDetected()
            }
        }
    }

    companion object {
        const val REQUEST_CODE = 101
    }
}
