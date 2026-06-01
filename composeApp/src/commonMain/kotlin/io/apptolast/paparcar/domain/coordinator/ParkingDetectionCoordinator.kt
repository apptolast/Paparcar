package io.apptolast.paparcar.domain.coordinator

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
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
    private val stepDetector: StepDetectorSource,
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
        /** Epoch-ms when [ParkingConfidence.Low] or [ParkingConfidence.Medium] was first reached
         *  in the current stop. Used to implement a fallback notification timeout: if no
         *  `vehicleExit` or `activityStill` signal arrives within [ParkingDetectionConfig.lowNotifTimeoutMs],
         *  the notification fires anyway so the user can confirm manually. Reset together with
         *  [mediumNotificationShown] whenever the vehicle moves again. [BUG-DETECT-310502] */
        val lowFirstReachedAt: Long? = null,
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
        /** Pedestrian steps counted while the car is stopped (`stoppedSince != null`).
         *  Reset to 0 whenever the vehicle drives away. When this reaches
         *  [ParkingDetectionConfig.minStepsToConfirm] during the CANDIDATE phase, the
         *  coordinator auto-confirms with [ParkingDetectionConfig.reliabilityVehicleExit]
         *  reliability — pedestrian steps are unambiguous proof the user got out of the
         *  car, and therefore a stronger signal than the activity-exit transition (which
         *  is noisy on real hardware). [BUG-GARAGE-COLA-001] */
        val stepCount: Int = 0,
        /** Epoch-ms when the coordinator received its first GPS sample in this session.
         *  Used to compute session-duration heuristics (e.g. the scooter/bike mismatch
         *  prompt — a "long trip below 30 km/h with a CAR vehicle" is suspicious).
         *  Set once and never overwritten until [reset]. [BUG-SCOOTER-001] */
        val sessionStartMs: Long? = null,
        /** Maximum GPS speed (m/s) observed in this session. Combined with
         *  [sessionStartMs] to differentiate genuine driving from a scooter trip. */
        val maxSpeedMps: Float = 0f,
    ) {
        /** Returns the most GPS-accurate fix collected at the moment of stopping, or [fallback]. */
        fun bestFix(fallback: GpsPoint): GpsPoint =
            stoppedFixes.minByOrNull { it.accuracy } ?: fallback

        /** Convenience accessor for the mismatch heuristic — km/h is the human-facing unit. */
        val maxSpeedKmh: Float get() = maxSpeedMps * 3.6f

        /** Wall-clock duration since the first GPS fix, in ms; `0` if no fix has arrived yet. */
        fun sessionDurationMs(now: Long): Long = sessionStartMs?.let { now - it } ?: 0L
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
    suspend operator fun invoke(locations: Flow<GpsPoint>) = coroutineScope {
        PaparcarLogger.d(DIAG, "▶ coordinator.invoke() entry — calling reset()")
        reset()

        val activeVehicle = vehicleRepository.observeDefaultVehicle().first()
        if (activeVehicle == null) {
            PaparcarLogger.w(DIAG, "  ✗ no default vehicle — aborting coordinator session")
            return@coroutineScope
        }
        val activeVehicleId = activeVehicle.id
        val activeVehicleType = activeVehicle.vehicleType
        PaparcarLogger.d(DIAG, "  ✓ active vehicleId=$activeVehicleId type=$activeVehicleType")

        var completed = false
        val sessionStartMs = Clock.System.now().toEpochMilliseconds()
        var locationCount = 0

        // Sibling job: count pedestrian steps while the vehicle is stopped. Steps that
        // arrive during a stop are strong evidence the user got out of the car, which
        // is the canonical signal that distinguishes a real park from a queue.
        // [BUG-GARAGE-COLA-001]
        val stepJob = launch {
            stepDetector.steps().collect {
                val updated = _detectionState.updateAndGet { s ->
                    if (s.stoppedSince != null) s.copy(stepCount = s.stepCount + 1) else s
                }
                if (updated.stoppedSince != null) {
                    PaparcarLogger.d(DIAG, "  ✦ step #${updated.stepCount} (stopped)")
                }
            }
        }

        locations
            .takeWhile {
                val keep = !completed
                if (!keep) PaparcarLogger.d(DIAG, "  takeWhile=false — flow will end")
                keep
            }
            .catch { e -> PaparcarLogger.e(DIAG, "✗ upstream flow error", e) }
            .collect { location ->
                locationCount++
                val now = Clock.System.now().toEpochMilliseconds()
                val sessionAgeMs = now - sessionStartMs
                PaparcarLogger.d(
                    DIAG,
                    "─ loc#$locationCount speed=${location.speed}m/s acc=${location.accuracy}m sessionAge=${sessionAgeMs}ms"
                )
                val stoppedDuration = updateStopTracking(location, now)

                // updateAndGet returns the post-update snapshot atomically — without it, an external
                // callback (onVehicleExit/onStillDetected) could mutate state between .update {} and
                // a subsequent .value read, giving us a 'state' that doesn't reflect our own write.
                val state = _detectionState.updateAndGet { s ->
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
                        sessionStartMs = s.sessionStartMs ?: now,
                        maxSpeedMps = if (location.speed > s.maxSpeedMps) location.speed else s.maxSpeedMps,
                    )
                }
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
                    return@collect
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
                    PaparcarLogger.d(DIAG, "  ◀ USER-CONFIRMED path done — returning from collect")
                    return@collect
                }

                if (!state.hasEverMoved) {
                    PaparcarLogger.d(DIAG, "  ⏸ skipping: !hasEverMoved")
                    return@collect
                }

                if (state.highConfidenceReachedAt != null) {
                    // Pedestrian steps observed while stopped are the strongest evidence that
                    // the user has actually exited the car — confirm immediately regardless of
                    // the activity-exit signal or elapsed window. [BUG-GARAGE-COLA-001]
                    val hasStepsProof = state.stepCount >= config.minStepsToConfirm
                    val window = if (state.highCandidateHadVehicleExit)
                        config.vehicleExitObservationWindowMs
                    else
                        config.confirmationObservationWindowMs
                    val elapsed = now - state.highConfidenceReachedAt
                    val windowElapsed = elapsed >= window
                    PaparcarLogger.d(
                        DIAG,
                        "  ⏳ CANDIDATE phase — elapsed=${elapsed}ms window=${window}ms steps=${state.stepCount}/${config.minStepsToConfirm}"
                    )

                    // Vehicle-mismatch guard: if the user's active vehicle is a CAR but the
                    // session profile (sustained slow trip) looks like a scooter, suppress
                    // auto-confirm and rely on the High-confidence notification — the user
                    // tells us by tapping. Prevents false-positive parking events when the
                    // user took their scooter but the app has the car selected as default.
                    // [BUG-SCOOTER-001]
                    val isMismatch = activeVehicleType == VehicleType.CAR &&
                            state.sessionDurationMs(now) >= config.mismatchMinSessionDurationMs &&
                            state.maxSpeedKmh <= config.mismatchMaxSpeedKmh

                    // Three confirmation paths:
                    //  1. Step proof (hasStepsProof) — strongest, fires the moment the user steps out.
                    //  2. Vehicle-exit fast path — window elapsed with an IN_VEHICLE→EXIT signal present.
                    //  3. Slow path — only if steps confirm; otherwise the candidate is discarded as a
                    //     likely queue / traffic stop. This is the key change vs the pre-step behaviour,
                    //     where 5 minutes of stillness alone was enough to auto-confirm. [BUG-GARAGE-COLA-001]
                    val confirmNow = when {
                        isMismatch -> false
                        hasStepsProof -> true
                        windowElapsed && state.highCandidateHadVehicleExit -> true
                        else -> false
                    }
                    if (isMismatch) {
                        PaparcarLogger.d(
                            DIAG,
                            "  ⊘ MISMATCH guard active — CAR vehicle but maxSpeed=${state.maxSpeedKmh}km/h " +
                                    "≤ ${config.mismatchMaxSpeedKmh}, session=${state.sessionDurationMs(now)}ms " +
                                    "≥ ${config.mismatchMinSessionDurationMs}. Suppressing auto-confirm. [BUG-SCOOTER-001]"
                        )
                    }

                    if (confirmNow) {
                        val reliability = config.reliabilityVehicleExit
                        val pathLabel = if (hasStepsProof) "steps" else "vehicleExit+window"
                        PaparcarLogger.d(DIAG, "  ▶ CANDIDATE confirmed via $pathLabel — entering confirmParking(reliability=$reliability)")
                        val locationToConfirm = state.bestStopLocation ?: state.bestFix(location)
                        completed = true
                        withContext(NonCancellable) {
                            PaparcarLogger.d(DIAG, "    → confirmParking(reliability=$reliability) START")
                            confirmParking(locationToConfirm, reliability, vehicleId = activeVehicleId)
                                .onFailure { PaparcarLogger.e(TAG, "Failed to confirm parking", it) }
                            PaparcarLogger.d(DIAG, "    ← confirmParking(reliability=$reliability) END")
                        }
                        PaparcarLogger.d(DIAG, "  ◀ CANDIDATE confirm done — returning from collect")
                    } else if (windowElapsed) {
                        // Slow path expired without steps and without vehicleExit — almost certainly
                        // a queue / traffic stop with the user still inside the car. Discard the
                        // candidate so a subsequent legitimate stop can be evaluated freshly.
                        // The user notification fired when High was first reached; if they did park
                        // and ignored the notification, the next session will catch them.
                        PaparcarLogger.d(DIAG, "  ⊘ CANDIDATE expired without steps/exit — discarding (likely cola/atasco) [BUG-GARAGE-COLA-001]")
                        _detectionState.update { it.copy(highConfidenceReachedAt = null) }
                    }
                    return@collect
                }

                evaluateConfidence(location, stoppedDuration, state, now)
            }
        stepJob.cancel()
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
            // with good accuracy. Three or more consecutive candidates clear bestStopLocation
            // — that distinguishes a brief vehicle maneuver from GPS oscillation (accuracy
            // > 15 m at 1.7 m/s is noise, not real motion; field-confirmed Redmi 2026-05-30).
            // Uses [repositionMaxAccuracyMeters] (stricter than the LOC-002 isDriving gate)
            // so that urban GPS drift at 22–48 m accuracy can never trigger a burst. [PARKING-001]
            val isRepositionCandidate = location.speed >= config.repositionSpeedMps &&
                    location.accuracy <= config.repositionMaxAccuracyMeters
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
                    lowFirstReachedAt = null,
                    bestStopLocation = if (shouldClearBestStop) null else it.bestStopLocation,
                    vehicleExitConfirmed = if (isDriving) false else it.vehicleExitConfirmed,
                    activityStillDetected = if (isDriving) false else it.activityStillDetected,
                    highConfidenceReachedAt = if (isDriving) null else it.highConfidenceReachedAt,
                    consecutiveRepositionFixes = newConsecutive,
                    // Vehicle drove away — discard accumulated steps so a future stop is evaluated
                    // freshly without inheriting evidence from a previous walking/queue episode.
                    stepCount = if (isDriving) 0 else it.stepCount,
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
                // Record the first time Low/Medium is reached in this stop (once per stop).
                // Used to drive the fallback notification timeout. [BUG-DETECT-310502]
                if (state.lowFirstReachedAt == null) {
                    _detectionState.update { it.copy(lowFirstReachedAt = now) }
                }

                val hasExitOrStill = state.vehicleExitConfirmed || state.activityStillDetected
                // Fall back to showing the notification after lowNotifTimeoutMs even without
                // an activity-exit or STILL signal. On hardware where AR delivers the EXIT
                // transition late (or not at all), the user would otherwise never see the
                // confirmation prompt during the stop. [BUG-DETECT-310502]
                val timeoutReached = state.lowFirstReachedAt != null &&
                        (now - state.lowFirstReachedAt) >= config.lowNotifTimeoutMs

                if (!state.mediumNotificationShown && (hasExitOrStill || timeoutReached)) {
                    val reason = when {
                        hasExitOrStill -> "exit=${state.vehicleExitConfirmed} still=${state.activityStillDetected}"
                        else -> "timeout=${now - (state.lowFirstReachedAt ?: now)}ms"
                    }
                    PaparcarLogger.d(DIAG, "  → showing parking-confirmation notif (Low/Medium, $reason)")
                    _detectionState.update { it.copy(mediumNotificationShown = true) }
                    PaparcarLogger.d(DIAG, "    ↳ calling notifyParkingConfirmation BEFORE")
                    notifyParkingConfirmation(confidence)
                    PaparcarLogger.d(DIAG, "    ↳ notifyParkingConfirmation AFTER")
                } else if (!state.mediumNotificationShown) {
                    val waitMs = config.lowNotifTimeoutMs - (now - (state.lowFirstReachedAt ?: now))
                    PaparcarLogger.d(DIAG, "  ⊘ Low/Medium notif suppressed — no vehicleExit/STILL, timeout in ~${waitMs}ms [BUG-3]")
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
