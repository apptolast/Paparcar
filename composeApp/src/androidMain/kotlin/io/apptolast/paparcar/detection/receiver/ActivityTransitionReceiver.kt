package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.WorkManager
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.apptolast.paparcar.detection.activityLabel
import io.apptolast.paparcar.detection.transitionLabel
import io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker
import io.apptolast.paparcar.domain.coordinator.CoordinatorParkingDetector
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receives the always-on IN_VEHICLE **ENTER + EXIT** transitions as pure **indicators**. [DET-G-01]
 *
 * AR does not arm anything from here — arming is exclusive to GEOFENCE_EXIT + MANUAL:
 *  - **EXIT** → non-decisive hint forwarded to a *running* coordinator (needs egress to confirm).
 *  - **ENTER** → stamps [DepartureEventBus] with the TRUE transition time, feeding the departure
 *    verifier/worker evidence ("the user boarded a vehicle recently"). [DET-SOLID-001]
 *
 * **True transition time.** AR delivers transitions with latency up to ~2 min. Stamping the bus
 * at delivery time made a boarding-then-quick-exit look like ENTER *after* the geofence exit —
 * which forced the evidence window to use |Δ| (abs) and let a bus boarding AFTER a walking exit
 * falsely verify it. The event's `elapsedRealTimeNanos` is the actual transition moment; convert
 * it to epoch so the strict enter-BEFORE-exit window holds regardless of delivery lag.
 *
 * Delivered via `getBroadcast` (no foreground service), so nothing here flashes the detection
 * notification on a bus ride. STILL is not consumed — it was redundant with the egress gate and
 * fired in traffic jams. [DET-D-03]
 */
class ActivityTransitionReceiver : BroadcastReceiver(), KoinComponent {

    private val coordinator: CoordinatorParkingDetector by inject()
    private val departureEventBus: DepartureEventBus by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        result.transitionEvents.forEach { event ->
            PaparcarLogger.d(TAG, "  → ${activityLabel(event.activityType)} ${transitionLabel(event.transitionType)}")
            if (event.activityType != DetectedActivity.IN_VEHICLE) return@forEach
            when (event.transitionType) {
                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                    // EXIT: a hint for a running coordinator; non-decisive (needs egress to confirm).
                    coordinator.onVehicleExit()
                    // And an ACCELERATOR of the reconcile, like ENTER below: "the user just left a
                    // vehicle" is THE moment a missed departure becomes decidable (the trip is
                    // over, evidence is at its freshest). Receivers keep running through the OEM
                    // background freezes that starve WorkManager for 90+ min (field 2026-07-08:
                    // both devices parked at the cinema announced it via AR EXIT within 2 min and
                    // no check ran for over an hour). Level-triggered and gated, so a bus stop
                    // costs one fix sample, never a false release. [DET-CONJUNCTION-001]
                    ParkingSafetyNetWorker.enqueueCheckNow(
                        WorkManager.getInstance(context),
                        source = ParkingSafetyNetWorker.SOURCE_AR_EXIT,
                    )
                }
                ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    // ENTER: evidence only — never arms. True transition time, not delivery time.
                    val trueEpochMs = elapsedNanosToEpochMs(event.elapsedRealTimeNanos)
                    PaparcarLogger.d(TAG, "  ✓ IN_VEHICLE ENTER → bus stamped (trueTime=$trueEpochMs, lag=${System.currentTimeMillis() - trueEpochMs}ms) [DET-SOLID-001]")
                    departureEventBus.onVehicleEntered(trueEpochMs)
                    // [DET-RECONCILE-001] And an ACCELERATOR of the parked-state reconcile — not a
                    // decision. AR rides a PendingIntent, so this fires even from a dead process
                    // (field 2026-07-06: delivered at 23:57 with the app long killed) — exactly
                    // when the geofence EXIT is still minutes away on ColorOS. The check itself
                    // stays gated (no session → no-op; anchor + step budget decide), so a bus
                    // boarding costs one fix sample, never a false release.
                    ParkingSafetyNetWorker.enqueueCheckNow(
                        WorkManager.getInstance(context),
                        source = ParkingSafetyNetWorker.SOURCE_AR_ENTER,
                    )
                }
            }
        }
    }

    /** Converts an AR event's elapsed-realtime-nanos (actual transition moment) to epoch-ms. */
    private fun elapsedNanosToEpochMs(eventElapsedNanos: Long): Long =
        System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - eventElapsedNanos) / NANOS_PER_MS

    private companion object {
        const val TAG = "PARKDIAG/ARReceiver"
        const val NANOS_PER_MS = 1_000_000L
    }
}
