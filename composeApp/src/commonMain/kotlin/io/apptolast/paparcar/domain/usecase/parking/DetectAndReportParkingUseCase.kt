package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.notification.NotificationPort
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Orchestrates the parking-detection loop for a single driving session.
 *
 * Call [invoke] with a location [Flow] to start detection. The use case
 * internally aggregates sensor signals, delegates scoring to
 * [CalculateParkingConfidenceUseCase], and delegates persistence + geofence
 * + notification to [ConfirmParkingUseCase] once confidence is high enough
 * or the user confirms manually.
 *
 * External state updates (vehicle exit, STILL activity, user confirmation)
 * are fed in via [onVehicleExit], [onStillDetected], [onUserConfirmedParking],
 * and [onUserDeniedParking].
 *
 * **Lifecycle:** The use case is stateful and designed as a Koin `single`.
 * State is fully reset at the start of each [invoke] call, so the same
 * instance can be reused across multiple driving sessions without leaking
 * data from a previous run.
 *
 * **Thread-safety:** All mutable state is held in a single [MutableStateFlow]
 * of [ParkingDetectionState] and updated atomically via [MutableStateFlow.update].
 * External signals ([onVehicleExit] etc.) may be called from any thread.
 */
@OptIn(ExperimentalTime::class)
class DetectAndReportParkingUseCase(
    private val calculateParkingConfidence: CalculateParkingConfidenceUseCase,
    private val confirmParking: ConfirmParkingUseCase,
    private val notifyParkingConfirmation: NotifyParkingConfirmationUseCase,
    private val notificationPort: NotificationPort,
    private val config: ParkingDetectionConfig,
) {
    /**
     * Atomic snapshot of all mutable detection variables for a single session.
     * Updated via [MutableStateFlow.update] to ensure thread-safe transitions.
     */
    private data class ParkingDetectionState(
        /** Epoch-ms of the first GPS sample with speed < 1 m/s in the current stop. `null` while moving. */
        val stoppedSince: Long? = null,
        /** GPS fixes collected within [ParkingDetectionConfig.initialStopWindowMs] of the initial stop.
         *  The fix with the lowest [GpsPoint.accuracy] value is used as the saved parking spot. */
        val stoppedFixes: List<GpsPoint> = emptyList(),
        val vehicleExitConfirmed: Boolean = false,
        val activityStillDetected: Boolean = false,
        val userConfirmedParking: Boolean = false,
        val mediumNotificationShown: Boolean = false,
        /** `true` once GPS speed has reached [ParkingDetectionConfig.minimumTripSpeedMps] at least
         *  once. Guards against spurious [IN_VEHICLE_ENTER] events while the user is stationary. */
        val hasEverMoved: Boolean = false,
    ) {
        /** Returns the most GPS-accurate fix collected at the moment of stopping, or [fallback]. */
        fun bestFix(fallback: GpsPoint): GpsPoint = stoppedFixes.minByOrNull { it.accuracy } ?: fallback
    }

    private val _detectionState = MutableStateFlow(ParkingDetectionState())

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs the detection loop until a parking spot is confirmed or [locations] ends.
     * Resets all session state on entry and dismisses any stale confirmation notification.
     */
    suspend operator fun invoke(locations: Flow<GpsPoint>) {
        reset()
        var completed = false

        locations
            .takeWhile { !completed }
            .catch { /* upstream error — detection ends gracefully */ }
            .collectLatest { location ->
                val now = Clock.System.now().toEpochMilliseconds()
                val stoppedDuration = updateStopTracking(location, now)

                if (location.speed >= config.minimumTripSpeedMps) {
                    _detectionState.update { it.copy(hasEverMoved = true) }
                }

                val state = _detectionState.value
                val locationToConfirm = when {
                    state.userConfirmedParking -> state.bestFix(location)
                    !state.hasEverMoved        -> null
                    else                       -> evaluateConfidence(location, stoppedDuration, state)
                }

                if (locationToConfirm != null) {
                    // Set completed BEFORE confirmParking so takeWhile stops the flow immediately.
                    // NonCancellable ensures the write completes even if collectLatest is cancelled
                    // by a new incoming item.
                    completed = true
                    withContext(NonCancellable) { confirmParking(locationToConfirm) }
                }
            }
    }

    /** Signals that the `IN_VEHICLE → EXIT` transition was received. Thread-safe. */
    fun onVehicleExit() {
        _detectionState.update { it.copy(vehicleExitConfirmed = true) }
    }

    /** Signals that the device is now reporting STILL activity. Thread-safe. */
    fun onStillDetected() {
        _detectionState.update { it.copy(activityStillDetected = true) }
    }

    /** User tapped "Yes, I parked". Dismisses the notification and marks confirmation. Thread-safe. */
    fun onUserConfirmedParking() {
        notificationPort.dismiss(NotificationPort.PARKING_CONFIRMATION_NOTIFICATION_ID)
        _detectionState.update { it.copy(userConfirmedParking = true) }
    }

    /** User dismissed the confirmation ("Keep driving"). Resets all heuristics. Thread-safe. */
    fun onUserDeniedParking() {
        notificationPort.dismiss(NotificationPort.PARKING_CONFIRMATION_NOTIFICATION_ID)
        _detectionState.value = ParkingDetectionState()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun reset() {
        _detectionState.value = ParkingDetectionState()
        notificationPort.dismiss(NotificationPort.PARKING_CONFIRMATION_NOTIFICATION_ID)
    }

    /**
     * Updates [stoppedSince] / [stoppedFixes] when the vehicle is stopped, or resets
     * them when it starts moving again. Returns the total stopped duration in ms.
     */
    private fun updateStopTracking(location: GpsPoint, now: Long): Long {
        return if (location.speed < 1f) {
            _detectionState.update { s ->
                val startedAt = s.stoppedSince ?: now
                val withinInitialWindow = (now - startedAt) < config.initialStopWindowMs
                s.copy(
                    stoppedSince = startedAt,
                    stoppedFixes = if (withinInitialWindow && s.stoppedFixes.size < MAX_STOPPED_FIXES)
                        s.stoppedFixes + location else s.stoppedFixes,
                )
            }
            now - (_detectionState.value.stoppedSince ?: 0L)
        } else {
            _detectionState.update {
                it.copy(stoppedSince = null, stoppedFixes = emptyList(), mediumNotificationShown = false)
            }
            0L
        }
    }

    /**
     * Runs the confidence scorer and handles Medium/High results.
     * Returns the [GpsPoint] to save when [ParkingConfidence.High] is reached, null otherwise.
     */
    private fun evaluateConfidence(
        location: GpsPoint,
        stoppedDuration: Long,
        state: ParkingDetectionState,
    ): GpsPoint? {
        val signals = ParkingSignals(
            speed = location.speed,
            stoppedDurationMs = stoppedDuration,
            gpsAccuracy = location.accuracy,
            activityExit = state.vehicleExitConfirmed,
            activityStill = state.activityStillDetected,
        )
        return when (val confidence = calculateParkingConfidence(signals)) {
            is ParkingConfidence.NotYet, is ParkingConfidence.Low -> null
            is ParkingConfidence.Medium -> {
                if (!state.mediumNotificationShown) {
                    _detectionState.update { it.copy(mediumNotificationShown = true) }
                    notifyParkingConfirmation(confidence)
                }
                null
            }
            is ParkingConfidence.High -> state.bestFix(location)
        }
    }

    companion object {
        private const val MAX_STOPPED_FIXES = 20
    }
}
