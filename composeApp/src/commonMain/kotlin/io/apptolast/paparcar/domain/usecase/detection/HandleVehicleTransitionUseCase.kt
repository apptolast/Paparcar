package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator
import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.detection.TransitionAction
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Handles a single IN_VEHICLE activity-recognition transition event.
 *
 * Encapsulates business logic previously in ParkingDetectionService.handleVehicleTransition:
 * - Debounce duplicate IN_VEHICLE_ENTER bursts (AR noise on real hardware).
 * - Dispatch the vehicle-enter epoch timestamp to [DepartureEventBus].
 * - Resolve the detection strategy and return the appropriate [TransitionAction].
 * - On EXIT, notify the coordinator and signal the service to stop if idle.
 *
 * Callers must pre-compute [epochMs] from platform clock APIs before invoking, so
 * this class stays free of Android/GMS dependencies.
 *
 * **Statefulness:** tracks [isVehicleIn] across calls — must be a Koin singleton.
 *
 * **Thread-safety [FIX BUG-DET-101].** The read-modify-write on [isVehicleIn] is
 * serialised by [mutex]. The previous `@Volatile` guard only protected visibility,
 * not the compound CAS — concurrent ENTER events from a single AR transition batch
 * (delivered via per-event coroutines in the Service) could both observe
 * `isVehicleIn=false`, both proceed past the debounce, and both spawn
 * [TransitionAction.StartCoordinatorDetection]. A coroutine [Mutex] is the KMP-safe
 * fit (no new dependencies; the function is already suspend).
 */
class HandleVehicleTransitionUseCase(
    private val strategyResolver: ParkingStrategyResolver,
    private val coordinator: ParkingDetectionCoordinator,
    private val departureEventBus: DepartureEventBus,
) {
    // [FIX BUG-DET-101: serialise debounce + side-effects; @Volatile alone was unsafe]
    private val mutex = Mutex()

    /** Mirrors the last seen IN_VEHICLE state; suppresses duplicate ENTER bursts. */
    private var isVehicleIn: Boolean = false

    /**
     * @param isEnter  `true` for ACTIVITY_TRANSITION_ENTER, `false` for EXIT.
     * @param epochMs  Wall-clock ms of the event, pre-computed by the Android caller.
     */
    suspend operator fun invoke(isEnter: Boolean, epochMs: Long): TransitionAction =
        mutex.withLock { if (isEnter) handleEnter(epochMs) else handleExit() }

    private suspend fun handleEnter(epochMs: Long): TransitionAction {
        if (isVehicleIn) {
            PaparcarLogger.d(TAG, "IN_VEHICLE_ENTER ignored — already IN (AR debounce) [BUG-DETECT-ENTER-DEBOUNCE-001]")
            return TransitionAction.Ignore
        }
        isVehicleIn = true
        departureEventBus.onVehicleEntered(epochMs)
        return when (strategyResolver.resolve()) {
            ParkingStrategy.COORDINATOR -> TransitionAction.StartCoordinatorDetection
            ParkingStrategy.BLUETOOTH -> {
                PaparcarLogger.d(TAG, "IN_VEHICLE_ENTER — BT strategy active, Coordinator not started")
                TransitionAction.StopIfIdle
            }
            ParkingStrategy.NONE -> {
                PaparcarLogger.d(TAG, "IN_VEHICLE_ENTER — vehicle type opts out of detection [BUG-SCOOTER-001]")
                TransitionAction.StopIfIdle
            }
        }
    }

    private fun handleExit(): TransitionAction {
        isVehicleIn = false
        coordinator.onVehicleExit()
        return TransitionAction.StopIfIdle
    }

    private companion object {
        const val TAG = "HandleVehicleTransitionUseCase"
    }
}
