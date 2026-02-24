package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSession
import io.apptolast.paparcar.domain.model.ParkingSignals
import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.domain.service.GeofenceService
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingSpotSavedUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Orchestrates the parking-detection loop for a single driving session.
 *
 * Call [invoke] with a location [Flow] to start detection. The use case
 * internally aggregates sensor signals, delegates scoring to
 * [CalculateParkingConfidenceUseCase], and persists the parking session
 * once confidence is high enough or the user confirms manually.
 *
 * External state updates (vehicle exit, STILL activity, user confirmation)
 * are fed in via [onVehicleExit], [onStillDetected], [onUserConfirmedParking],
 * and [onUserDeniedParking].
 *
 * The use case is stateful: call [invoke] once per driving session. The state
 * is reset at the start of each invocation.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class DetectAndReportParkingUseCase(
    private val calculateParkingConfidence: CalculateParkingConfidenceUseCase,
    private val saveUserParking: SaveUserParkingUseCase,
    private val geofenceService: GeofenceService,
    private val notifyParkingConfirmation: NotifyParkingConfirmationUseCase,
    private val notifyParkingSpotSaved: NotifyParkingSpotSavedUseCase,
    private val config: ParkingDetectionConfig,
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
    )

    private val _detectionState = MutableStateFlow(ParkingDetectionState())

    /**
     * Runs the detection loop until a parking spot is confirmed or the [locations] flow ends.
     *
     * Suspends until the session completes. Safe to call from a coroutine tied to the
     * foreground service lifecycle; upstream flow errors are caught and logged so the
     * loop does not silently terminate on transient GPS failures.
     */
    suspend operator fun invoke(locations: Flow<SpotLocation>) {
        _detectionState.value = ParkingDetectionState()
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

                // User confirmed via notification action button
                if (_detectionState.value.userConfirmedParking) {
                    confirmParking(location)
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
                            _detectionState.update { it.copy(mediumNotificationShown = true) }
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

    /** Persists the detected parking session and registers a geofence for departure detection. */
    private suspend fun confirmParking(location: SpotLocation) {
        val sessionId = Uuid.random().toString()
        val session = ParkingSession(
            id = sessionId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            geofenceId = sessionId,
            isActive = true,
        )
        saveUserParking(session)
        geofenceService.createGeofence(sessionId, location.latitude, location.longitude, config.geofenceRadiusMeters)
        notifyParkingSpotSaved(location.latitude, location.longitude)
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
        _detectionState.update { it.copy(userConfirmedParking = true) }
    }

    /** Signals that the user dismissed the parking confirmation. Resets detection heuristics. */
    fun onUserDeniedParking() {
        _detectionState.update {
            it.copy(
                vehicleExitConfirmed = false,
                activityStillDetected = false,
                stoppedSince = null,
                mediumNotificationShown = false,
                userConfirmedParking = false,
            )
        }
    }
}
