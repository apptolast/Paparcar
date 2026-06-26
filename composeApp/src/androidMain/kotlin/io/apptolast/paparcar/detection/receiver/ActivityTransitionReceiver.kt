package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.apptolast.paparcar.detection.activityLabel
import io.apptolast.paparcar.detection.transitionLabel
import io.apptolast.paparcar.domain.coordinator.CoordinatorParkingDetector
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receives Activity-Recognition transitions and feeds them in as **non-decisive signals**. [DET-G-01]
 *
 * AR no longer arms the coordinator — that is the GEOFENCE_EXIT's job (see
 * `CoordinatorDetectionService.handleGeofenceExit`). AR is a *fragile* signal: IN_VEHICLE/STILL fire
 * on buses, in traffic jams, in a friend's car. Delivered via `getBroadcast` (no foreground service,
 * so no FGS flash on a bus ride), this receiver only:
 *  - records the **IN_VEHICLE_ENTER timestamp** into [DepartureEventBus] — a *corroborator* of the
 *    departure/release window (not required; the release confirms on speed alone),
 *  - forwards **IN_VEHICLE_EXIT** to a *running* coordinator as a hint (it never confirms a park on
 *    its own — the egress gate is the decisive signal). STILL is no longer consumed at all: it was
 *    redundant with the egress gate and fired in traffic jams (a fragile non-event signal). [DET-D-03]
 *
 * Side effects are synchronous (no suspend, no `goAsync`): [DepartureEventBus.onVehicleEntered] and
 * the coordinator setters are plain in-memory writes. Duplicate ENTER bursts are harmless (idempotent
 * timestamp), so the old debounce use case is gone.
 */
class ActivityTransitionReceiver : BroadcastReceiver(), KoinComponent {

    private val coordinator: CoordinatorParkingDetector by inject()
    private val departureEventBus: DepartureEventBus by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        result.transitionEvents.forEach { event ->
            PaparcarLogger.d(TAG, "  → ${activityLabel(event.activityType)} ${transitionLabel(event.transitionType)}")
            val isEnter = event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
            when (event.activityType) {
                DetectedActivity.IN_VEHICLE ->
                    if (isEnter) {
                        // Record the enter timestamp for the departure window (corroborator). NOT a
                        // trigger — the coordinator is armed by GEOFENCE_EXIT. [DET-G-01]
                        val epochMs = System.currentTimeMillis() - SystemClock.elapsedRealtime() +
                            event.elapsedRealTimeNanos / 1_000_000L
                        departureEventBus.onVehicleEntered(epochMs)
                    } else {
                        // EXIT: a hint for a running coordinator; non-decisive (needs egress to confirm).
                        coordinator.onVehicleExit()
                    }

                else -> Unit
            }
        }
    }

    private companion object {
        const val TAG = "PARKDIAG/ARReceiver"
    }
}
