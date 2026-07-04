@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.coordinator.CoordinatorParkingDetector
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock
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
 * [io.apptolast.paparcar.ios.permissions.IosPermissionRequester.requestStep1].
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
    }
}
