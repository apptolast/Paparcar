package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.apptolast.paparcar.detection.activityLabel
import io.apptolast.paparcar.detection.transitionLabel
import io.apptolast.paparcar.domain.coordinator.CoordinatorParkingDetector
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receives the always-on IN_VEHICLE **EXIT** transition and forwards it as a **non-decisive hint**
 * to a *running* coordinator. [DET-G-01]
 *
 * AR does not arm the coordinator from here. Two arming paths exist instead: the GEOFENCE_EXIT
 * (decisive, anchored) and the scoped IN_VEHICLE **ENTER** that goes DIRECTLY to
 * `CoordinatorDetectionService.handleArVehicleEnter` via a privileged getForegroundService start
 * (proximity-gated). [DET-AR-REARM-001] ENTER is no longer delivered to this receiver — it would
 * need a foreground-service start a background receiver cannot legally do on Android 12+, and the
 * service is where the ENTER timestamp + proximity gate now live.
 *
 * Delivered via `getBroadcast` (no foreground service), so forwarding EXIT never flashes the
 * detection notification on a bus ride. STILL is not consumed — it was redundant with the egress
 * gate and fired in traffic jams. [DET-D-03]
 */
class ActivityTransitionReceiver : BroadcastReceiver(), KoinComponent {

    private val coordinator: CoordinatorParkingDetector by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        result.transitionEvents.forEach { event ->
            PaparcarLogger.d(TAG, "  → ${activityLabel(event.activityType)} ${transitionLabel(event.transitionType)}")
            val isExit = event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT
            if (event.activityType == DetectedActivity.IN_VEHICLE && isExit) {
                // EXIT: a hint for a running coordinator; non-decisive (needs egress to confirm).
                coordinator.onVehicleExit()
            }
        }
    }

    private companion object {
        const val TAG = "PARKDIAG/ARReceiver"
    }
}
