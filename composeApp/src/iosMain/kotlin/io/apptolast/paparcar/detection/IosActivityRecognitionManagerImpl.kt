@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.util.PaparcarLogger
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMMotionActivity
import platform.CoreMotion.CMMotionActivityConfidenceLow
import platform.CoreMotion.CMMotionActivityManager
import platform.Foundation.NSOperationQueue

/**
 * iOS implementation of [ActivityRecognitionManager] backed by [CMMotionActivityManager].
 *
 * **Important difference vs Android's Activity Transitions API**: iOS fires
 * `CMMotionActivity` *snapshots* — the current activity state with a confidence
 * level — not explicit ENTER/EXIT transitions. We synthesise transitions by
 * comparing each snapshot against the previous one, mirroring the three
 * Android signals registered in [ActivityRecognitionManagerImpl]:
 *
 *  - `automotive` false → true   ≡ IN_VEHICLE / ENTER
 *  - `automotive` true  → false  ≡ IN_VEHICLE / EXIT
 *  - `stationary` false → true   ≡ STILL / ENTER
 *
 * Low-confidence snapshots are ignored. The Android side uses the system's
 * confidence threshold implicitly; on iOS we filter [CMMotionActivityConfidenceLow]
 * explicitly to avoid jitter.
 *
 * **Wiring to the detection pipeline is intentionally deferred.** On Android,
 * `ActivityTransitionReceiver` fans the events out to `DepartureEventBus`,
 * `ParkingDetectionCoordinator`, and `ParkingDetectionService`. iOS has no
 * foreground service equivalent, and the coordinator-on-iOS lifecycle is its
 * own design problem. Hooking the synthesised transitions into the domain
 * pipeline belongs with that later task; for now this class verifies that
 * the platform plumbing works and logs the transitions.
 *
 * Requires Motion & Fitness authorisation, already prompted by
 * [io.apptolast.paparcar.ios.permissions.IosPermissionRequester.requestStep1].
 */
class IosActivityRecognitionManagerImpl : ActivityRecognitionManager {

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

    private fun handleUpdate(activity: CMMotionActivity?) {
        if (activity == null) return
        if (activity.confidence == CMMotionActivityConfidenceLow) return

        val previous = lastActivity
        lastActivity = activity

        // First snapshot: nothing to compare against — adopt as baseline.
        if (previous == null) return

        if (!previous.automotive && activity.automotive) {
            PaparcarLogger.d(TAG, "Transition: IN_VEHICLE / ENTER")
            // TODO(IOS-DETECTION-PIPELINE): departureEventBus.onVehicleEntered(now)
            //   + ParkingStrategyResolver gate + start coordinator equivalent.
        }
        if (previous.automotive && !activity.automotive) {
            PaparcarLogger.d(TAG, "Transition: IN_VEHICLE / EXIT")
            // TODO(IOS-DETECTION-PIPELINE): equivalent of ACTION_VEHICLE_EXIT.
        }
        if (!previous.stationary && activity.stationary) {
            PaparcarLogger.d(TAG, "Transition: STILL / ENTER")
            // TODO(IOS-DETECTION-PIPELINE): coordinator.onStillDetected()
        }
    }

    private companion object {
        const val TAG = "IosActivityRecognitionManager"
    }
}
