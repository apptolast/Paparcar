@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.ActivityRecognitionManager
import com.rndeveloper.paparcar.domain.ActivityTransitionEvent
import com.rndeveloper.paparcar.domain.detection.CoordinatorParkingDetector
import com.rndeveloper.paparcar.domain.detection.coordinator.ingestion.TraceEvent
import com.rndeveloper.paparcar.domain.service.DepartureEventBus
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMMotionActivity
import platform.CoreMotion.CMMotionActivityConfidenceLow
import platform.CoreMotion.CMMotionActivityManager
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of [ActivityRecognitionManager] backed by [CMMotionActivityManager].
 *
 * **Important difference vs Android's Activity Transitions API**: iOS fires
 * `CMMotionActivity` *snapshots* — the current activity state with a confidence
 * level — not explicit ENTER/EXIT transitions. We synthesise transitions by
 * comparing each snapshot against the previous one, mirroring the three
 * Android signals registered in [ActivityRecognitionManagerImpl]:
 *
 *  - `automotive` false → true   ≡ IN_VEHICLE / ENTER  → [DepartureEventBus.onVehicleEntered]
 *  - `automotive` true  → false  ≡ IN_VEHICLE / EXIT   → [CoordinatorParkingDetector.onVehicleExit]
 *
 * `stationary` (STILL) is intentionally not synthesised: STILL was dropped as a detection signal
 * (redundant with the egress gate, fires in traffic jams). [DET-D-03]
 *
 * Low-confidence snapshots are ignored. The Android side uses the system's
 * confidence threshold implicitly; on iOS we filter [CMMotionActivityConfidenceLow]
 * explicitly to avoid jitter.
 *
 * The Android pipeline also starts [CoordinatorDetectionService] from the receiver when
 * IN_VEHICLE/ENTER fires and BT strategy isn't owning the session. iOS has no
 * foreground-service equivalent — the loop that calls
 * [CoordinatorParkingDetector.invoke] with a GPS stream is a separate concern
 * (see IOS_PLAN.md). The signals here are still useful in the meantime: they
 * keep the singleton coordinator's state primed so that whichever component
 * eventually starts the session sees an up-to-date vehicleExit/still flag.
 *
 * Requires Motion & Fitness authorisation, already prompted by
 * [com.rndeveloper.paparcar.ios.permissions.IosPermissionRequester.requestStep1].
 */
class IosActivityRecognitionManagerImpl(
    private val departureEventBus: DepartureEventBus,
    private val coordinator: CoordinatorParkingDetector,
) : ActivityRecognitionManager {

    private val manager = CMMotionActivityManager()
    private var lastActivity: CMMotionActivity? = null
    private var running = false

    override fun registerTransitions() {
        if (running) return
        if (!CMMotionActivityManager.isActivityAvailable()) {
            PaparcarLogger.w(TAG, "registerTransitions skipped — CMMotionActivity not available on device")
            return
        }
        if (CMMotionActivityManager.authorizationStatus() != CMAuthorizationStatusAuthorized) {
            PaparcarLogger.w(TAG, "registerTransitions skipped — Motion & Fitness authorization not granted")
            return
        }

        manager.startActivityUpdatesToQueue(NSOperationQueue.mainQueue) { activity ->
            handleUpdate(activity)
        }
        running = true
    }

    override fun unregisterTransitions() {
        if (!running) return
        manager.stopActivityUpdates()
        lastActivity = null
        running = false
    }

    /**
     * [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001] The pull lane: `CMMotionActivityManager` keeps up
     * to 7 days of recorded samples with the app DEAD — the property the whole wake-and-query
     * model rests on (plan §2.3). Historical snapshots get the SAME edge synthesis as the live
     * lane above (automotive false→true ≡ ENTER, true→false ≡ EXIT, low-confidence filtered),
     * plus the cycling edge as BICYCLE_ENTER — in a reconstruction the human-powered veto matters
     * as much as the drive. Each transition is stamped with the SAMPLE's own start time, never
     * the query's. Unavailable/unauthorized/error → empty: absence of evidence, not evidence.
     */
    override suspend fun queryTransitions(fromMs: Long, toMs: Long): List<ActivityTransitionEvent> {
        if (!CMMotionActivityManager.isActivityAvailable()) return emptyList()
        if (CMMotionActivityManager.authorizationStatus() != CMAuthorizationStatusAuthorized) return emptyList()
        if (toMs <= fromMs) return emptyList()
        val samples = suspendCancellableCoroutine<List<CMMotionActivity>?> { cont ->
            manager.queryActivityStartingFromDate(
                NSDate.dateWithTimeIntervalSince1970(fromMs / MILLIS_PER_SECOND),
                NSDate.dateWithTimeIntervalSince1970(toMs / MILLIS_PER_SECOND),
                NSOperationQueue.mainQueue,
            ) { activities, error ->
                if (!cont.isActive) return@queryActivityStartingFromDate
                if (error != null) {
                    PaparcarLogger.w(TAG, "activity history query failed: ${error.localizedDescription}")
                    cont.resume(null)
                } else {
                    cont.resume(activities?.filterIsInstance<CMMotionActivity>())
                }
            }
        } ?: return emptyList()

        val transitions = mutableListOf<ActivityTransitionEvent>()
        var previous: CMMotionActivity? = null
        for (sample in samples) {
            if (sample.confidence == CMMotionActivityConfidenceLow) continue
            val prev = previous
            previous = sample
            if (prev == null) continue
            val tMs = (sample.startDate.timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()
            if (!prev.automotive && sample.automotive) {
                transitions += ActivityTransitionEvent(tMs, TraceEvent.Activity.VEHICLE_ENTER)
            }
            if (prev.automotive && !sample.automotive) {
                transitions += ActivityTransitionEvent(tMs, TraceEvent.Activity.VEHICLE_EXIT)
            }
            if (!prev.cycling && sample.cycling) {
                transitions += ActivityTransitionEvent(tMs, TraceEvent.Activity.BICYCLE_ENTER)
            }
        }
        return transitions
    }

    private fun handleUpdate(activity: CMMotionActivity?) {
        if (activity == null) return
        if (activity.confidence == CMMotionActivityConfidenceLow) return

        val previous = lastActivity
        lastActivity = activity

        // First snapshot: nothing to compare against — adopt as baseline.
        if (previous == null) return

        if (!previous.automotive && activity.automotive) {
            val nowMs = Clock.System.now().toEpochMilliseconds()
            PaparcarLogger.d(TAG, "Transition: IN_VEHICLE / ENTER — t=$nowMs")
            departureEventBus.onVehicleEntered(nowMs)
        }
        if (previous.automotive && !activity.automotive) {
            PaparcarLogger.d(TAG, "Transition: IN_VEHICLE / EXIT")
            coordinator.onVehicleExit()
        }
    }

    private companion object {
        const val TAG = "IosActivityRecognitionManager"
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
