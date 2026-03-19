package io.apptolast.paparcar.domain.coordinator

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
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
 * **Confirmation paths and reliability:**
 * 1. User taps "Sí" → immediate, [ParkingDetectionConfig.reliabilityUserConfirmed] (1.0).
 * 2. IN_VEHICLE→EXIT observed + [ParkingDetectionConfig.vehicleExitObservationWindowMs]
 *    elapsed without the vehicle driving away → [ParkingDetectionConfig.reliabilityVehicleExit] (~0.90).
 * 3. Slow path only + [ParkingDetectionConfig.confirmationObservationWindowMs] elapsed →
 *    [ParkingDetectionConfig.reliabilitySlowPath] (~0.75).
 *
 * A notification is **always** shown when [ParkingConfidence.High] is first reached,
 * so the user can override the auto-confirmation or dismiss it.
 *
 * **Lifecycle:** Stateful Koin `single`. State is fully reset at the start of each
 * [invoke] call, so the same instance can be reused across multiple driving sessions
 * without leaking data from a previous run.
 *
 * **Thread-safety:** All mutable state is held in a single [MutableStateFlow]
 * of [ParkingDetectionState] and updated atomically via [MutableStateFlow.update].
 * External signals ([onVehicleExit] etc.) may be called from any thread.
 */
@OptIn(ExperimentalTime::class)
class ParkingDetectionCoordinator(
    private val calculateParkingConfidence: CalculateParkingConfidenceUseCase,
    private val confirmParking: ConfirmParkingUseCase,
    private val notifyParkingConfirmation: NotifyParkingConfirmationUseCase,
    private val notificationPort: AppNotificationManager,
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
        /** `true` once GPS speed has reached [ParkingDetectionConfig.minimumTripSpeedMps] AND
         *  the device has moved at least [ParkingDetectionConfig.minimumTripDistanceMeters] from
         *  [sessionOrigin]. Both conditions must hold simultaneously to prevent a brief GPS-noise
         *  spike from being mistaken for real driving. */
        val hasEverMoved: Boolean = false,
        /** First GPS fix received in this session. Captured once and never overwritten. Used as
         *  the displacement reference for the [hasEverMoved] distance check. */
        val sessionOrigin: GpsPoint? = null,
        /** Best (lowest accuracy value) GPS fix recorded while the vehicle was stopped (speed < 1 m/s).
         *  Updated continuously during stopped intervals. Cleared when the vehicle drives away at
         *  [ParkingDetectionConfig.clearBestStopSpeedMps] to prevent stale false-positive locations
         *  from polluting the next genuine parking confirmation. */
        val bestStopLocation: GpsPoint? = null,
        // ── CANDIDATE PHASE ───────────────────────────────────────────────────
        /** Epoch-ms when [ParkingConfidence.High] was first reached in the current stop.
         *  `null` outside the CANDIDATE phase. Cleared when the vehicle drives away. */
        val highConfidenceReachedAt: Long? = null,
        /** Snapshot of [vehicleExitConfirmed] at the moment the CANDIDATE phase was entered.
         *  Determines which observation window applies for auto-confirmation. */
        val highCandidateHadVehicleExit: Boolean = false,
    ) {
        /** Returns the most GPS-accurate fix collected at the moment of stopping, or [fallback]. */
        fun bestFix(fallback: GpsPoint): GpsPoint =
            stoppedFixes.minByOrNull { it.accuracy } ?: fallback
    }

    private val _detectionState = MutableStateFlow(ParkingDetectionState())

    /**
     * True once the coordinator has observed GPS movement meeting the trip thresholds
     * ([ParkingDetectionConfig.minimumTripSpeedMps] AND [ParkingDetectionConfig.minimumTripDistanceMeters]).
     *
     * Used by [ParkingDetectionService] to decide whether a new [ACTION_START_TRACKING]
     * command should restart the session or be treated as a spurious/duplicate event.
     */
    val hasDetectedMovement: Boolean get() = _detectionState.value.hasEverMoved

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
        val sessionStartMs = Clock.System.now().toEpochMilliseconds()

        locations
            .takeWhile { !completed }
            .catch { /* upstream error — detection ends gracefully */ }
            .collectLatest { location ->
                val now = Clock.System.now().toEpochMilliseconds()
                val stoppedDuration = updateStopTracking(location, now)

                _detectionState.update { s ->
                    val origin = s.sessionOrigin ?: location
                    val hasJustMoved = !s.hasEverMoved &&
                            location.speed >= config.minimumTripSpeedMps &&
                            io.apptolast.paparcar.domain.util.haversineMeters(
                                origin.latitude, origin.longitude,
                                location.latitude, location.longitude,
                            ) >= config.minimumTripDistanceMeters
                    s.copy(
                        sessionOrigin = s.sessionOrigin ?: location,
                        hasEverMoved = s.hasEverMoved || hasJustMoved,
                    )
                }

                val state = _detectionState.value

                // Guard: if real driving movement never appeared within maxNoMovementMs,
                // this session was started by a spurious/batched IN_VEHICLE_ENTER while
                // the user was already stationary. End detection silently.
                if (!state.hasEverMoved && (now - sessionStartMs) > config.maxNoMovementMs) {
                    completed = true
                    return@collectLatest
                }

                // ── User-confirmed path ───────────────────────────────────────────────
                // Immediate confirmation at reliability 1.0 — user provided ground truth.
                if (state.userConfirmedParking) {
                    val locationToConfirm = state.bestStopLocation ?: state.bestFix(location)
                    completed = true
                    withContext(NonCancellable) {
                        confirmParking(locationToConfirm, config.reliabilityUserConfirmed)
                    }
                    return@collectLatest
                }

                if (!state.hasEverMoved) return@collectLatest

                // ── CANDIDATE phase: observation window ───────────────────────────────
                // Once High confidence is reached the CANDIDATE phase runs an observation
                // window before auto-confirming. If the vehicle drives away at
                // clearBestStopSpeedMps, updateStopTracking clears highConfidenceReachedAt
                // and bestStopLocation, cancelling the candidate automatically.
                if (state.highConfidenceReachedAt != null) {
                    val window = if (state.highCandidateHadVehicleExit)
                        config.vehicleExitObservationWindowMs
                    else
                        config.confirmationObservationWindowMs

                    if (now - state.highConfidenceReachedAt >= window) {
                        // Observation window elapsed with no vehicle movement → auto-confirm.
                        val reliability = if (state.highCandidateHadVehicleExit)
                            config.reliabilityVehicleExit
                        else
                            config.reliabilitySlowPath
                        val locationToConfirm = state.bestStopLocation ?: state.bestFix(location)
                        completed = true
                        withContext(NonCancellable) { confirmParking(locationToConfirm, reliability) }
                    }
                    // Still within observation window — keep waiting.
                    return@collectLatest
                }

                // ── Normal confidence scoring ─────────────────────────────────────────
                evaluateConfidence(location, stoppedDuration, state, now)
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
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        _detectionState.update { it.copy(userConfirmedParking = true) }
    }

    /** User dismissed the confirmation ("Keep driving"). Resets all heuristics. Thread-safe. */
    fun onUserDeniedParking() {
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        // Preserve hasEverMoved: sessionStartMs is fixed at invoke() entry and never reset,
        // so a full ParkingDetectionState() reset (hasEverMoved=false) would immediately
        // trigger the maxNoMovementMs timeout guard on the next GPS fix and kill the session.
        _detectionState.update { ParkingDetectionState(hasEverMoved = it.hasEverMoved) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun reset() {
        _detectionState.value = ParkingDetectionState()
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
    }

    /**
     * Updates `stoppedSince` / `stoppedFixes` when the vehicle is stopped, or resets
     * them when it starts moving again. Returns the total stopped duration in ms.
     *
     * At driving speed ([ParkingDetectionConfig.clearBestStopSpeedMps]) the following are
     * also cleared to prevent stale signals from polluting the next genuine stop:
     * - [ParkingDetectionState.bestStopLocation] — stale car position from a false-positive stop.
     * - [ParkingDetectionState.vehicleExitConfirmed] — delayed Activity Recognition delivery
     *   could otherwise trigger the fast path at an unrelated subsequent stop.
     * - [ParkingDetectionState.activityStillDetected] — STILL signal from the previous stop.
     * - [ParkingDetectionState.highConfidenceReachedAt] — cancels the CANDIDATE phase if the
     *   vehicle drives away before the observation window expires.
     */
    private fun updateStopTracking(location: GpsPoint, now: Long): Long {
        return if (location.speed < 1f) {
            _detectionState.update { s ->
                val startedAt = s.stoppedSince ?: now
                val withinInitialWindow = (now - startedAt) < config.initialStopWindowMs
                val newBestStop = when {
                    s.bestStopLocation == null || location.accuracy < s.bestStopLocation.accuracy -> location
                    else -> s.bestStopLocation
                }
                s.copy(
                    stoppedSince = startedAt,
                    stoppedFixes = if (withinInitialWindow && s.stoppedFixes.size < MAX_STOPPED_FIXES)
                        s.stoppedFixes + location else s.stoppedFixes,
                    bestStopLocation = newBestStop,
                )
            }
            now - (_detectionState.value.stoppedSince ?: 0L)
        } else {
            _detectionState.update {
                val isDriving = location.speed >= config.clearBestStopSpeedMps
                it.copy(
                    stoppedSince = null,
                    stoppedFixes = emptyList(),
                    mediumNotificationShown = false,
                    bestStopLocation = if (isDriving) null else it.bestStopLocation,
                    vehicleExitConfirmed = if (isDriving) false else it.vehicleExitConfirmed,
                    activityStillDetected = if (isDriving) false else it.activityStillDetected,
                    highConfidenceReachedAt = if (isDriving) null else it.highConfidenceReachedAt,
                )
            }
            0L
        }
    }

    /**
     * Runs the confidence scorer and handles Medium/High results.
     * On reaching [ParkingConfidence.High] for the first time, enters the CANDIDATE phase
     * and always shows a confirmation notification. Does not confirm immediately — the
     * observation window in [invoke] handles auto-confirmation timing.
     */
    private fun evaluateConfidence(
        location: GpsPoint,
        stoppedDuration: Long,
        state: ParkingDetectionState,
        now: Long,
    ) {
        val signals = ParkingSignals(
            speed = location.speed,
            stoppedDurationMs = stoppedDuration,
            gpsAccuracy = location.accuracy,
            activityExit = state.vehicleExitConfirmed,
            activityStill = state.activityStillDetected,
        )
        when (val confidence = calculateParkingConfidence(signals)) {
            is ParkingConfidence.NotYet -> Unit
            is ParkingConfidence.Low,
            is ParkingConfidence.Medium -> {
                if (!state.mediumNotificationShown) {
                    _detectionState.update { it.copy(mediumNotificationShown = true) }
                    notifyParkingConfirmation(confidence)
                }
            }

            is ParkingConfidence.High -> {
                // Enter CANDIDATE phase. Notification is always shown so the user can
                // confirm early or dismiss if this is a false positive (e.g. double-park).
                _detectionState.update { s ->
                    s.copy(
                        highConfidenceReachedAt = now,
                        highCandidateHadVehicleExit = s.vehicleExitConfirmed,
                    )
                }
                notifyParkingConfirmation(confidence)
            }
        }
    }

    companion object {
        private const val MAX_STOPPED_FIXES = 20
    }
}
