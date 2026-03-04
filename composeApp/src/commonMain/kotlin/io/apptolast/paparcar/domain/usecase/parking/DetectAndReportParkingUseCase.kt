package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.notification.NotificationPort
import io.apptolast.paparcar.domain.usecase.notification.DismissNotificationUseCase
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
    private val dismissNotification: DismissNotificationUseCase,
    private val config: ParkingDetectionConfig,
) {
    /**
     * Atomic snapshot of all mutable detection variables for a single session.
     * Updated via [MutableStateFlow.update] to ensure thread-safe transitions.
     *
     * @property stoppedSince Epoch-ms timestamp of the first GPS sample whose
     *   speed was below 1 m/s in the current stop event. `null` while moving.
     * @property stoppedAtLocation The [GpsPoint] captured at the moment
     *   the device first stopped (speed < 1 m/s). Preserved as the candidate
     *   save location so the spot is recorded at the actual parking point, not
     *   wherever the GPS is when confidence finally validates (up to ~1 min later).
     *   Reset to `null` when the vehicle starts moving again.
     * @property vehicleExitConfirmed `true` once an `IN_VEHICLE → EXIT` transition
     *   has been received from Activity Recognition.
     * @property activityStillDetected `true` once a `STILL` activity event has been
     *   received, indicating the device (and presumably the driver) is no longer moving.
     * @property userConfirmedParking `true` when the user tapped "Yes, I parked" in the
     *   confirmation notification. Triggers immediate save on the next GPS tick.
     * @property mediumNotificationShown `true` after the [ParkingConfidence.Medium]
     *   confirmation notification has been posted, preventing duplicate notifications.
     */
    private data class ParkingDetectionState(
        val stoppedSince: Long? = null,
        val stoppedAtLocation: GpsPoint? = null,
        val vehicleExitConfirmed: Boolean = false,
        val activityStillDetected: Boolean = false,
        val userConfirmedParking: Boolean = false,
        val mediumNotificationShown: Boolean = false,
        /** `true` once GPS speed has reached [ParkingDetectionConfig.minimumTripSpeedMps] at
         *  least once during this session. Guards against spurious [IN_VEHICLE_ENTER] events
         *  that fire while the user is stationary — a genuine driving session always exceeds
         *  the threshold before stopping to park. Never reset mid-session. */
        val hasEverMoved: Boolean = false,
    )

    private val _detectionState = MutableStateFlow(ParkingDetectionState())

    /**
     * Runs the detection loop until a parking spot is confirmed or the [locations] flow ends.
     *
     * On entry, all session state is reset to defaults and any confirmation notification
     * left over from a previous session (e.g. the user got back in the car before responding)
     * is dismissed so the user is never left with a stale "Did you park?" prompt.
     *
     * The loop terminates when:
     * - [ParkingConfidence.High] is reached (automatic save), or
     * - the user confirms via notification ([onUserConfirmedParking] was called), or
     * - the upstream [locations] flow completes (service stopped).
     *
     * @param locations A [Flow] of GPS fixes emitted by the foreground service.
     */
    suspend operator fun invoke(locations: Flow<GpsPoint>) {
        _detectionState.value = ParkingDetectionState()
        // Dismiss any confirmation notification from a previous session so the user
        // is not left with a stale "¿Has aparcado?" while a fresh trip is in progress.
        dismissNotification(NotificationPort.PARKING_CONFIRMATION_NOTIFICATION_ID)
        var completed = false

        locations
            .takeWhile { !completed }
            .catch { /* upstream error — detection ends gracefully without crashing */ }
            .collectLatest { location ->
                val now = Clock.System.now().toEpochMilliseconds()
                val stoppedDuration = if (location.speed < 1f) {
                    _detectionState.update {
                        it.copy(
                            stoppedSince = it.stoppedSince ?: now,
                            stoppedAtLocation = it.stoppedAtLocation ?: location,
                        )
                    }
                    now - (_detectionState.value.stoppedSince ?: 0L)
                } else {
                    _detectionState.update {
                        it.copy(
                            stoppedSince = null,
                            stoppedAtLocation = null,
                            mediumNotificationShown = false,
                        )
                    }
                    0L
                }

                // Track minimum trip speed: once the vehicle reaches driving speed the
                // flag is latched for the remainder of the session and never reset.
                if (location.speed >= config.minimumTripSpeedMps) {
                    _detectionState.update { it.copy(hasEverMoved = true) }
                }

                // User confirmed via notification action button — use the earliest stopped
                // location so the spot is saved at the actual parking place rather than
                // wherever the user is now.
                if (_detectionState.value.userConfirmedParking) {
                    val locationToSave = _detectionState.value.stoppedAtLocation ?: location
                    // Set completed FIRST so takeWhile drops any subsequent items before
                    // confirmParking finishes. NonCancellable ensures the write completes
                    // even if collectLatest receives a new item concurrently.
                    completed = true
                    withContext(NonCancellable) { confirmParking(locationToSave) }
                    return@collectLatest
                }

                val state = _detectionState.value

                // Gate: skip automatic scoring until the vehicle has reached at least
                // config.minimumTripSpeedMps during this session. A spurious
                // IN_VEHICLE_ENTER while the user is seated will never satisfy this
                // condition, so no notification or false spot will be produced.
                if (!state.hasEverMoved) return@collectLatest

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
                        if (!state.mediumNotificationShown) {
                            _detectionState.update { it.copy(mediumNotificationShown = true) }
                            notifyParkingConfirmation(confidence)
                        }
                    }
                    is ParkingConfidence.High -> {
                        val locationToSave = state.stoppedAtLocation ?: location
                        // Set completed FIRST — same reason as in the user-confirmed branch.
                        completed = true
                        withContext(NonCancellable) { confirmParking(locationToSave) }
                    }
                }
            }
    }

    /**
     * Signals that the `IN_VEHICLE → EXIT` transition was received from Activity Recognition.
     * Thread-safe; may be called from any thread.
     */
    fun onVehicleExit() {
        _detectionState.update { it.copy(vehicleExitConfirmed = true) }
    }

    /**
     * Signals that the device is now reporting STILL activity.
     * Thread-safe; may be called from any thread.
     */
    fun onStillDetected() {
        _detectionState.update { it.copy(activityStillDetected = true) }
    }

    /**
     * Signals that the user tapped the "Yes, I parked" action in the confirmation notification.
     * Dismisses the notification and marks confirmation so the next GPS tick triggers a save.
     * Thread-safe; may be called from any thread.
     */
    fun onUserConfirmedParking() {
        dismissNotification(NotificationPort.PARKING_CONFIRMATION_NOTIFICATION_ID)
        _detectionState.update { it.copy(userConfirmedParking = true) }
    }

    /**
     * Signals that the user dismissed the parking confirmation ("Keep driving").
     * Resets all detection heuristics to their defaults so the loop can re-evaluate
     * from scratch — equivalent to starting a fresh session without re-registering
     * for location updates.
     * Thread-safe; may be called from any thread.
     */
    fun onUserDeniedParking() {
        dismissNotification(NotificationPort.PARKING_CONFIRMATION_NOTIFICATION_ID)
        _detectionState.value = ParkingDetectionState()
    }
}
