package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingSignals
import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.domain.notification.NotificationPort
import io.apptolast.paparcar.domain.usecase.notification.DismissNotificationUseCase
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
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
 * The use case is stateful: call [invoke] once per driving session. The state
 * is reset at the start of each invocation, including dismissal of any
 * pending confirmation notification from a previous session.
 */
@OptIn(ExperimentalTime::class)
class DetectAndReportParkingUseCase(
    private val calculateParkingConfidence: CalculateParkingConfidenceUseCase,
    private val confirmParking: ConfirmParkingUseCase,
    private val notifyParkingConfirmation: NotifyParkingConfirmationUseCase,
    private val dismissNotification: DismissNotificationUseCase,
) {
    /**
     * Atomic snapshot of all mutable detection variables for a single session.
     * Updated via [MutableStateFlow.update] to ensure thread-safe transitions.
     */
    private data class ParkingDetectionState(
        val stoppedSince: Long? = null,
        val vehicleExitConfirmed: Boolean = false,
        val activityStillDetected: Boolean = false,
        val userConfirmedParking: Boolean = false,
        val mediumNotificationShown: Boolean = false,
        /** Location captured at the moment the Medium-confidence notification was shown.
         *  Used when the user confirms parking so the spot is saved at the detection
         *  location, not at wherever the GPS happens to be when the button is pressed. */
        val pendingParkingLocation: SpotLocation? = null,
    )

    private val _detectionState = MutableStateFlow(ParkingDetectionState())

    /**
     * Runs the detection loop until a parking spot is confirmed or the [locations] flow ends.
     *
     * Resets all state on entry, including dismissing any confirmation notification left
     * over from a previous session (e.g. user got back in car before responding).
     */
    suspend operator fun invoke(locations: Flow<SpotLocation>) {
        _detectionState.value = ParkingDetectionState()
        // Dismiss any confirmation notification from a previous session so the user
        // is not left with a stale "¿Has aparcado?" while a fresh trip is in progress.
        dismissNotification(NotificationPort.PARKING_CONFIRMATION_NOTIFICATION_ID)
        var completed = false

        locations
            .takeWhile { !completed }
            .collectLatest { location ->
                val now = Clock.System.now().toEpochMilliseconds()

                val stoppedDuration = if (location.speed < 1f) {
                    _detectionState.update { it.copy(stoppedSince = it.stoppedSince ?: now) }
                    now - (_detectionState.value.stoppedSince ?: 0L)
                } else {
                    _detectionState.update { it.copy(stoppedSince = null, mediumNotificationShown = false) }
                    0L
                }

                // User confirmed via notification action button — prefer the location that
                // was snapshotted when the confirmation notification was shown, so the spot
                // is saved at the actual parking place rather than wherever the user is now.
                if (_detectionState.value.userConfirmedParking) {
                    val locationToSave = _detectionState.value.pendingParkingLocation ?: location
                    confirmParking(locationToSave)
                    completed = true
                    return@collectLatest
                }

                val state = _detectionState.value
                val signals = ParkingSignals(
                    speed = location.speed,
                    stoppedDurationMs = stoppedDuration,
                    gpsAccuracy = location.accuracy,
                    activityExit = state.vehicleExitConfirmed,
                    activityStill = state.activityStillDetected,
                )

                when (val confidence = calculateParkingConfidence(signals)) {
                    is ParkingConfidence.NotYet, is ParkingConfidence.Low -> Unit
                    is ParkingConfidence.Medium -> {
                        if (!_detectionState.value.mediumNotificationShown) {
                            // Snapshot location NOW — this is where detection fired.
                            _detectionState.update {
                                it.copy(mediumNotificationShown = true, pendingParkingLocation = location)
                            }
                            notifyParkingConfirmation(confidence)
                        }
                    }
                    is ParkingConfidence.High -> {
                        confirmParking(location)
                        completed = true
                    }
                }
            }
    }

    /** Signals that the IN_VEHICLE→EXIT transition was received from Activity Recognition. */
    fun onVehicleExit() {
        _detectionState.update { it.copy(vehicleExitConfirmed = true) }
    }

    /** Signals that the device is now reporting STILL activity. */
    fun onStillDetected() {
        _detectionState.update { it.copy(activityStillDetected = true) }
    }

    /** Signals that the user tapped the "Yes, I parked" action in the confirmation notification. */
    fun onUserConfirmedParking() {
        dismissNotification(NotificationPort.PARKING_CONFIRMATION_NOTIFICATION_ID)
        _detectionState.update { it.copy(userConfirmedParking = true) }
    }

    /** Signals that the user dismissed the parking confirmation. Resets detection heuristics. */
    fun onUserDeniedParking() {
        dismissNotification(NotificationPort.PARKING_CONFIRMATION_NOTIFICATION_ID)
        _detectionState.update {
            it.copy(
                vehicleExitConfirmed = false,
                activityStillDetected = false,
                stoppedSince = null,
                mediumNotificationShown = false,
                userConfirmedParking = false,
                pendingParkingLocation = null,
            )
        }
    }
}