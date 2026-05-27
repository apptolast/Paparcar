package io.apptolast.paparcar.domain.coordinator

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
    private val vehicleRepository: VehicleRepository,
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
        /** Number of consecutive GPS fixes observed with speed ≥
         *  [ParkingDetectionConfig.repositionSpeedMps] and accuracy ≤
         *  [ParkingDetectionConfig.minGpsAccuracyForDriving] in the current moving streak.
         *  Reset to 0 on any stopped fix or on any moving fix that falls below the
         *  reposition threshold. Used to differentiate a brief vehicle-reposition burst
         *  (a maneuver into the actual parking spot after waiting nearby) from sustained
         *  walking, which never crosses the reposition speed. [PARKING-001] */
        val consecutiveRepositionFixes: Int = 0,
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
        PaparcarLogger.d(DIAG, "▶ coordinator.invoke() entry — calling reset()")
        reset()

        val activeVehicleId = vehicleRepository.observeDefaultVehicle().first()?.id
        if (activeVehicleId == null) {
            PaparcarLogger.w(DIAG, "  ✗ no default vehicle — aborting coordinator session")
            return
        }
        PaparcarLogger.d(DIAG, "  ✓ active vehicleId=$activeVehicleId")

        var completed = false
        val sessionStartMs = Clock.System.now().toEpochMilliseconds()
        var locationCount = 0

        locations
            .takeWhile {
                val keep = !completed
                if (!keep) PaparcarLogger.d(DIAG, "  takeWhile=false — flow will end")
                keep
            }
            .catch { e -> PaparcarLogger.e(DIAG, "✗ upstream flow error", e) }
            .collectLatest { location ->
                locationCount++
                val now = Clock.System.now().toEpochMilliseconds()
                val sessionAgeMs = now - sessionStartMs
                PaparcarLogger.d(
                    DIAG,
                    "─ loc#$locationCount speed=${location.speed}m/s acc=${location.accuracy}m sessionAge=${sessionAgeMs}ms"
                )
                val stoppedDuration = updateStopTracking(location, now)

                _detectionState.update { s ->
                    val origin = s.sessionOrigin ?: location
                    val distFromOrigin = io.apptolast.paparcar.domain.util.haversineMeters(
                        origin.latitude, origin.longitude,
                        location.latitude, location.longitude,
                    )
                    val hasJustMoved = !s.hasEverMoved &&
                            location.speed >= config.minimumTripSpeedMps &&
                            distFromOrigin >= config.minimumTripDistanceMeters
                    if (hasJustMoved) {
                        PaparcarLogger.d(DIAG, "  ✓ hasEverMoved → true (speed≥${config.minimumTripSpeedMps}, dist≥${config.minimumTripDistanceMeters}m, actual=${distFromOrigin}m)")
                    }
                    s.copy(
                        sessionOrigin = s.sessionOrigin ?: location,
                        hasEverMoved = s.hasEverMoved || hasJustMoved,
                    )
                }

                val state = _detectionState.value
                PaparcarLogger.d(
                    DIAG,
                    "  state hasEverMoved=${state.hasEverMoved} userConfirmed=${state.userConfirmedParking} " +
                            "vehicleExit=${state.vehicleExitConfirmed} stoppedSince=${state.stoppedSince} " +
                            "stoppedDur=${stoppedDuration}ms highReachedAt=${state.highConfidenceReachedAt} " +
                            "mediumShown=${state.mediumNotificationShown}"
                )

                if (!state.hasEverMoved && (now - sessionStartMs) > config.maxNoMovementMs) {
                    PaparcarLogger.d(DIAG, "  ⚑ maxNoMovementMs guard hit → completed=true (spurious IN_VEHICLE_ENTER)")
                    completed = true
                    return@collectLatest
                }

                if (state.userConfirmedParking) {
                    PaparcarLogger.d(DIAG, "  ▶ USER-CONFIRMED path — entering confirmParking")
                    val locationToConfirm = state.bestStopLocation ?: state.bestFix(location)
                    completed = true
                    withContext(NonCancellable) {
                        PaparcarLogger.d(DIAG, "    → confirmParking(reliability=user) START")
                        confirmParking(locationToConfirm, config.reliabilityUserConfirmed, vehicleId = activeVehicleId)
                            .onFailure { PaparcarLogger.e(TAG, "Failed to confirm parking", it) }
                        PaparcarLogger.d(DIAG, "    ← confirmParking(reliability=user) END")
                    }
                    PaparcarLogger.d(DIAG, "  ◀ USER-CONFIRMED path done — returning from collectLatest")
                    return@collectLatest
                }

                if (!state.hasEverMoved) {
                    PaparcarLogger.d(DIAG, "  ⏸ skipping: !hasEverMoved")
                    return@collectLatest
                }

                if (state.highConfidenceReachedAt != null) {
                    val window = if (state.highCandidateHadVehicleExit)
                        config.vehicleExitObservationWindowMs
                    else
                        config.confirmationObservationWindowMs
                    val elapsed = now - state.highConfidenceReachedAt
                    PaparcarLogger.d(DIAG, "  ⏳ CANDIDATE phase — elapsed=${elapsed}ms window=${window}ms")

                    if (elapsed >= window) {
                        val reliability = if (state.highCandidateHadVehicleExit)
                            config.reliabilityVehicleExit
                        else
                            config.reliabilitySlowPath
                        PaparcarLogger.d(DIAG, "  ▶ CANDIDATE window expired — entering confirmParking(reliability=$reliability)")
                        val locationToConfirm = state.bestStopLocation ?: state.bestFix(location)
                        completed = true
                        withContext(NonCancellable) {
                            PaparcarLogger.d(DIAG, "    → confirmParking(reliability=$reliability) START")
                            confirmParking(locationToConfirm, reliability, vehicleId = activeVehicleId)
                                .onFailure { PaparcarLogger.e(TAG, "Failed to confirm parking", it) }
                            PaparcarLogger.d(DIAG, "    ← confirmParking(reliability=$reliability) END")
                        }
                        PaparcarLogger.d(DIAG, "  ◀ CANDIDATE confirm done — returning from collectLatest")
                    }
                    return@collectLatest
                }

                evaluateConfidence(location, stoppedDuration, state, now)
            }
        PaparcarLogger.d(DIAG, "■ coordinator.invoke() EXITED — locationCount=$locationCount completed=$completed")
    }

    /** Signals that the `IN_VEHICLE → EXIT` transition was received. Thread-safe. */
    fun onVehicleExit() {
        PaparcarLogger.d(DIAG, "✱ onVehicleExit() called")
        _detectionState.update { it.copy(vehicleExitConfirmed = true) }
    }

    /** Signals that the device is now reporting STILL activity. Thread-safe. */
    fun onStillDetected() {
        PaparcarLogger.d(DIAG, "✱ onStillDetected() called")
        _detectionState.update { it.copy(activityStillDetected = true) }
    }

    /** User tapped "Yes, I parked". Dismisses the notification and marks confirmation. Thread-safe. */
    fun onUserConfirmedParking() {
        PaparcarLogger.d(DIAG, "✱ onUserConfirmedParking() called")
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        _detectionState.update { it.copy(userConfirmedParking = true) }
    }

    /** User dismissed the confirmation ("Keep driving"). Resets all heuristics. Thread-safe. */
    fun onUserDeniedParking() {
        PaparcarLogger.d(DIAG, "✱ onUserDeniedParking() called")
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
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
        return if (location.speed < STOPPED_SPEED_THRESHOLD_MPS) {
            _detectionState.update { s ->
                val startedAt = s.stoppedSince ?: now
                val withinInitialWindow = (now - startedAt) < config.initialStopWindowMs
                // Freeze bestStopLocation after the initial-stop window (default 30 s).
                // Without this, a user walking from their parked car towards a destination
                // — at speeds well below clearBestStopSpeedMps (2.5 m/s) — keeps producing
                // GPS fixes whose accuracy may beat the parked-car fix, overwriting the
                // saved spot with the user's walking destination. [LOC-001]
                val newBestStop = when {
                    !withinInitialWindow -> s.bestStopLocation
                    s.bestStopLocation == null || location.accuracy < s.bestStopLocation.accuracy -> location
                    else -> s.bestStopLocation
                }
                s.copy(
                    stoppedSince = startedAt,
                    stoppedFixes = if (withinInitialWindow && s.stoppedFixes.size < MAX_STOPPED_FIXES)
                        s.stoppedFixes + location else s.stoppedFixes,
                    bestStopLocation = newBestStop,
                    // Reset the reposition counter on every stopped fix so a counter built up
                    // during one moving streak never carries across a stop into the next. [PARKING-001]
                    consecutiveRepositionFixes = 0,
                )
            }
            now - (_detectionState.value.stoppedSince ?: 0L)
        } else {
            // A high-speed fix only counts as "the vehicle drove away" when GPS accuracy is
            // good enough to trust the speed reading. On noisy hardware (Redmi Note 11) a
            // single fix can spike to speed≈3 m/s with accuracy≈85 m while the user is
            // actually stationary on foot; without this gate, the bad fix would clear the
            // parked-car bestStopLocation mid-CANDIDATE and the spot would be re-captured
            // wherever the user eventually sits down (home, café). [LOC-002]
            val isDriving = location.speed >= config.clearBestStopSpeedMps &&
                    location.accuracy <= config.minGpsAccuracyForDriving
            // A fix counts as a reposition candidate when it crosses the lower
            // [repositionSpeedMps] threshold (between sustained walking and clearBestStop)
            // with good accuracy. Two or more consecutive candidates clear bestStopLocation
            // — that distinguishes a brief vehicle maneuver from a single GPS spike (single
            // fix, LOC-002 territory) and from sustained walking (never crosses 1.7 m/s).
            // The accuracy gate is the same one LOC-002 uses so the two guards are
            // co-extensive: a noisy fix can never trigger a reposition burst. [PARKING-001]
            val isRepositionCandidate = location.speed >= config.repositionSpeedMps &&
                    location.accuracy <= config.minGpsAccuracyForDriving
            if (location.speed >= config.clearBestStopSpeedMps && !isDriving) {
                PaparcarLogger.d(
                    DIAG,
                    "  ⊘ ignoring driving-speed fix with poor accuracy " +
                            "(speed=${location.speed} acc=${location.accuracy} > " +
                            "minGpsAccuracyForDriving=${config.minGpsAccuracyForDriving})"
                )
            }
            _detectionState.update {
                val newConsecutive = if (isRepositionCandidate) it.consecutiveRepositionFixes + 1 else 0
                val isRepositionBurst = newConsecutive >= config.repositionFixCount
                val shouldClearBestStop = isDriving || isRepositionBurst
                if (isRepositionBurst && !isDriving) {
                    PaparcarLogger.d(
                        DIAG,
                        "  ⟲ reposition-burst detected " +
                                "(consecutive=$newConsecutive speed=${location.speed} acc=${location.accuracy}) " +
                                "— clearing bestStopLocation [PARKING-001]"
                    )
                }
                it.copy(
                    stoppedSince = null,
                    stoppedFixes = emptyList(),
                    mediumNotificationShown = false,
                    bestStopLocation = if (shouldClearBestStop) null else it.bestStopLocation,
                    vehicleExitConfirmed = if (isDriving) false else it.vehicleExitConfirmed,
                    activityStillDetected = if (isDriving) false else it.activityStillDetected,
                    highConfidenceReachedAt = if (isDriving) null else it.highConfidenceReachedAt,
                    consecutiveRepositionFixes = newConsecutive,
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
    private suspend fun evaluateConfidence(
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
        val confidence = calculateParkingConfidence(signals)
        PaparcarLogger.d(DIAG, "  ⚖ scoring=$confidence (signals: speed=${signals.speed} stopped=${signals.stoppedDurationMs}ms accuracy=${signals.gpsAccuracy} exit=${signals.activityExit} still=${signals.activityStill})")
        when (confidence) {
            is ParkingConfidence.NotYet -> Unit
            is ParkingConfidence.Low,
            is ParkingConfidence.Medium -> {
                // Only show the notification if an activity-exit or STILL signal has
                // arrived. Without one, this is almost certainly a traffic stop — no
                // exit transition means the user is still in a moving vehicle. [BUG-3]
                if (!state.mediumNotificationShown &&
                    (state.vehicleExitConfirmed || state.activityStillDetected)
                ) {
                    PaparcarLogger.d(DIAG, "  → showing parking-confirmation notif (Low/Medium, exit=${state.vehicleExitConfirmed} still=${state.activityStillDetected})")
                    _detectionState.update { it.copy(mediumNotificationShown = true) }
                    PaparcarLogger.d(DIAG, "    ↳ calling notifyParkingConfirmation BEFORE")
                    notifyParkingConfirmation(confidence)
                    PaparcarLogger.d(DIAG, "    ↳ notifyParkingConfirmation AFTER")
                } else if (!state.mediumNotificationShown) {
                    PaparcarLogger.d(DIAG, "  ⊘ Low/Medium notif suppressed — no vehicleExit/STILL signal yet [BUG-3]")
                }
            }

            is ParkingConfidence.High -> {
                PaparcarLogger.d(DIAG, "  ▶ HIGH reached — entering CANDIDATE phase, vehicleExit=${state.vehicleExitConfirmed}")
                _detectionState.update { s ->
                    s.copy(
                        highConfidenceReachedAt = now,
                        highCandidateHadVehicleExit = s.vehicleExitConfirmed,
                    )
                }
                PaparcarLogger.d(DIAG, "    ↳ calling notifyParkingConfirmation BEFORE")
                notifyParkingConfirmation(confidence)
                PaparcarLogger.d(DIAG, "    ↳ notifyParkingConfirmation AFTER")
            }
        }
    }

    private companion object {
        const val TAG = "ParkingDetectionCoordinator"
        const val DIAG = "PARKDIAG/Coord"
        const val MAX_STOPPED_FIXES = 20
        const val STOPPED_SPEED_THRESHOLD_MPS = 1f
    }
}
