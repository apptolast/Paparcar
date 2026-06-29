package io.apptolast.paparcar.domain.coordinator

import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import io.apptolast.paparcar.domain.usecase.parking.ParkingDecision
import io.apptolast.paparcar.domain.usecase.parking.ParkingDecisionInput
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
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
 * External state updates (vehicle exit, user confirmation) are fed in via
 * [onVehicleExit], [onUserConfirmedParking], and [onUserDeniedParking].
 *
 * **Confirmation paths and reliability:**
 * 1. User taps "Sí" → immediate, [ParkingDetectionConfig.reliabilityUserConfirmed] (1.0).
 * 2. IN_VEHICLE→EXIT observed + [ParkingDetectionConfig.vehicleExitObservationWindowMs]
 *    elapsed without the vehicle driving away → [ParkingDetectionConfig.reliabilityVehicleExit] (~0.90).
 * 3. Step proof (`stepCount ≥ minStepsToConfirm`) inside CANDIDATE phase →
 *    [ParkingDetectionConfig.reliabilityVehicleExit] (steps are unambiguous user-out-of-car).
 * 4. **EXIT + steps fast confirm** (post-CANDIDATE bypass): `vehicleExitConfirmed = true`
 *    AND `stepCount ≥ minStepsToConfirm` BEFORE the scoring path reaches High →
 *    [ParkingDetectionConfig.reliabilityVehicleExit]. Skips the slow-path 5-min stop
 *    requirement entirely. [BUG-OPPO-LATE-CONFIRM][DET-D-03]
 *
 * **Prompt invariant.** A notification is shown when [ParkingConfidence.High] is first reached
 * via paths 2/3, so the user can override the auto-confirmation. Path 4 skips the prompt and
 * goes straight to the post-save "Vehículo aparcado · Cancelar" card; the REVERT button on
 * that card carries the same override affordance.
 *
 * **Path precedence inside the collect block** (BUG-COORD-115 invariant):
 *   1. `falseEnterAbortSteps` reached pre-drive → abort spurious session. [BUG-FALSE-ENTER-WALKING]
 *   2. `maxNoMovementMs` elapsed pre-drive → abort spurious session.
 *   3. Lock `activeVehicleId` on first driving-speed fix.
 *   4. `userConfirmedParking` short-circuits everything.
 *   5. `!hasEverReachedDrivingSpeed` skip (waiting for the driving signal).
 *   6. Response-timeout abort.
 *   7. Candidate-phase decision tree.
 *   8. EXIT + steps fast confirm (post-CANDIDATE bypass). [BUG-OPPO-LATE-CONFIRM]
 *   9. Confidence evaluation (advances [ConfirmationPhase]).
 * This ordering guarantees that a user tap always wins over an auto-confirm that landed in
 * the same iteration, eliminating any double-save risk by construction. The pre-drive aborts
 * at the top let a spurious AR ENTER end the session before any side-effect runs.
 *
 * **Lifecycle:** Stateful Koin `single`. State is fully reset on entry to [invoke] AND
 * on exit (finally), so the same instance can be reused across multiple driving
 * sessions without leaking data from a previous run. [FIX BUG-SERVICE-109]
 *
 * **Thread-safety:** All mutable state is held in a single [MutableStateFlow]
 * of [ParkingDetectionState] and updated atomically via [MutableStateFlow.update].
 * External signals ([onVehicleExit] etc.) may be called from any thread.
 */
@OptIn(ExperimentalTime::class)
class CoordinatorParkingDetector(
    private val calculateParkingConfidence: CalculateParkingConfidenceUseCase,
    private val confirmParking: ConfirmParkingUseCase,
    private val notifyParkingConfirmation: NotifyParkingConfirmationUseCase,
    private val notificationPort: AppNotificationManager,
    private val vehicleRepository: VehicleRepository,
    private val stepDetector: StepDetectorSource,
    private val config: ParkingDetectionConfig,
    private val detectionEventLogger: DetectionEventLogger,
    private val evaluateParkingDecision: EvaluateParkingDecisionUseCase,
    /** Wall-clock source (epoch-ms). Injectable so the time-driven post-confirm hold [DET-C-02]
     *  can be unit-tested without sleeping. Defaults to the system clock. */
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /**
     * Atomic snapshot of all mutable detection variables for a single session.
     * Updated via [MutableStateFlow.update] to ensure thread-safe transitions.
     *
     * [REFACTOR-200: the four timestamp/flag fields lowFirstReachedAt,
     *  confirmationNotificationShownAt, highConfidenceReachedAt, highCandidateHadVehicleExit
     *  are folded into a single [ConfirmationPhase] field. The legacy combinations
     *  are still encoded — they're just no longer reachable in an invalid form.]
     */
    /** A confirmed-but-held parking decision awaiting the [ParkingDetectionConfig.confirmHoldMs]
     *  grace window. Captured at the egress confirm so the saved location stays pinned to the
     *  parked-car position even if the user keeps walking during the hold. [DET-C-02] */
    private data class PendingConfirm(
        val location: GpsPoint,
        val reliability: Float,
        val vehicleId: String?,
        val pathLabel: String,
        val confirmedAt: Long,
    )

    private data class ParkingDetectionState(
        /** Epoch-ms of the first GPS sample with speed < 1 m/s in the current stop. `null` while moving. */
        val stoppedSince: Long? = null,
        /** GPS fixes collected within [ParkingDetectionConfig.initialStopWindowMs] of the initial stop.
         *  The fix with the lowest [GpsPoint.accuracy] value is used as the saved parking spot. */
        val stoppedFixes: List<GpsPoint> = emptyList(),
        val vehicleExitConfirmed: Boolean = false,
        val userConfirmedParking: Boolean = false,
        /** [DET-C-02] A tentatively-confirmed park awaiting the post-confirm hold window. While
         *  non-null the session is "tentatively parked": it stays alive so that resuming driving
         *  before the window elapses discards it and re-anchors at the real spot. */
        val pendingConfirm: PendingConfirm? = null,
        /** [REFACTOR-200] explicit confirmation lifecycle. See [ConfirmationPhase]. */
        val phase: ConfirmationPhase = ConfirmationPhase.Idle,
        /** `true` once GPS speed has reached [ParkingDetectionConfig.minimumTripSpeedMps] at least
         *  once, regardless of displacement from [sessionOrigin]. Enables short-trip detection
         *  ("circled the block"). [BUG-SHORT-TRIP] */
        val hasEverReachedDrivingSpeed: Boolean = false,
        /** `true` once both speed AND displacement thresholds have been crossed simultaneously.
         *  Used exclusively for the [maxNoMovementMs] guard against spurious IN_VEHICLE_ENTER. */
        val hasEverMoved: Boolean = false,
        /** First GPS fix received in this session. Captured once and never overwritten. */
        val sessionOrigin: GpsPoint? = null,
        /** Best (lowest accuracy value) GPS fix recorded while the vehicle was stopped. Cleared
         *  when the vehicle drives away. Also serves as the egress anchor: [hasEgressDisplacement]
         *  measures how far the current fix is from it. [code-review #4: a dedicated egressAnchor
         *  pinned on the *first* stopped fix could latch onto a poor-accuracy fix; reusing
         *  bestStopLocation gets the lowest-accuracy fix within the initial-stop window, which is
         *  exactly the parked-car position we want to measure displacement from.] */
        val bestStopLocation: GpsPoint? = null,
        // ── REPOSITION DETECTION (PARKING-001) ────────────────────────────────
        val consecutiveRepositionFixes: Int = 0,
        // ── STEP DETECTOR (BUG-GARAGE-COLA-001 + BUG-FALSE-ENTER-WALKING) ─────
        /** Pedestrian steps counted under two different gates depending on session phase:
         *  - **Pre-drive** (`!hasEverReachedDrivingSpeed`): every step counts, regardless of
         *    `stoppedSince`. Drives the [ParkingDetectionConfig.falseEnterAbortSteps] guard
         *    that aborts spurious AR `IN_VEHICLE_ENTER` events fired while the user is walking.
         *  - **Post-drive** (`hasEverReachedDrivingSpeed && stoppedSince != null`): the
         *    canonical "user has exited the car" signal that confirms parking inside the
         *    CANDIDATE phase OR via the EXIT+steps fast-confirm short-circuit.
         *
         *  Reset to 0 on `isDriving` AND on CANDIDATE discard (BUG-COORD-105) so cross-stop
         *  contamination cannot trigger an instant false confirm on the next stop. */
        val stepCount: Int = 0,
        // ── SESSION TELEMETRY (BUG-SCOOTER-001) ───────────────────────────────
        val sessionStartMs: Long? = null,
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
     * Epoch-ms when [AppNotificationManager.showParkingSavedConfirm] was last posted by
     * [runConfirm]. Lives across [invoke] calls (the coordinator is a Koin single) so the
     * session-start cleanup can decide whether the existing notification on
     * [AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID] is a fresh revert card
     * (preserve) or a stale prompt from an abandoned session (dismiss).
     *
     * Reset to `null` whenever the session-start dismiss fires.
     *
     * **Process death:** lost. A coordinator created after process restart treats any
     * lingering notification as stale and dismisses it — reasonable since we have no way
     * to verify its age. [REFACTOR-300-FIX]
     */
    @Volatile private var savedConfirmPostedAt: Long? = null

    // ── DETECTION DIAGNOSTICS (DET-LOG-03) ────────────────────────────────────
    /** Id of the in-flight session (= its start epoch-ms as string). Set at [invoke] entry,
     *  cleared in the finally. Null between sessions. Used to tag every [DetectionEvent]. */
    @Volatile private var currentSessionId: String? = null

    /** Terminal outcome label emitted in the [DetectionEvent.SessionEnded] for the current
     *  session. Defaults to "ended"; refined by abort paths and by [runConfirm]. */
    @Volatile private var sessionOutcome: String = "ended"

    /** Emits a [DetectionEvent] for the current session, or no-ops if no session is active.
     *  The logger contract guarantees this never throws and never blocks on network. */
    private suspend fun logDetection(build: (sessionId: String) -> DetectionEvent) {
        val sid = currentSessionId ?: return
        detectionEventLogger.log(build(sid))
    }

    private fun nowMs(): Long = clock()

    /**
     * True once the coordinator has observed GPS movement meeting the trip thresholds
     * ([ParkingDetectionConfig.minimumTripSpeedMps] AND [ParkingDetectionConfig.minimumTripDistanceMeters]).
     *
     * In-session only. Cross-session, [BUG-SERVICE-109] is closed by the `finally { reset() }`
     * inside [invoke]; this property therefore returns `false` between sessions.
     */
    val hasDetectedMovement: Boolean get() = _detectionState.value.hasEverReachedDrivingSpeed

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs the detection loop until a parking spot is confirmed or [locations] ends.
     * Resets all session state on entry and on exit, and dismisses any stale
     * confirmation notification.
     */
    suspend operator fun invoke(locations: Flow<GpsPoint>) = coroutineScope {
        PaparcarLogger.d(DIAG, "▶ coordinator.invoke() entry — calling reset()")
        reset()

        var completed = false
        val sessionStartMs = clock()
        var locationCount = 0

        // [DET-LOG-04] Edge-detect the AR signals so each transition is logged once (not on every
        // subsequent fix). Reset to false when the signal clears (driving away), so a re-entry logs again.
        var loggedVehicleExit = false

        // [DET-LOG-03] Open a diagnostics session. Id = start epoch-ms; outcome defaults to "ended"
        // and is refined by the abort paths / runConfirm before the finally emits SessionEnded.
        currentSessionId = sessionStartMs.toString()
        sessionOutcome = "ended"
        logDetection { sid -> DetectionEvent.SessionStarted(sid, sessionStartMs, strategy = "COORDINATOR") }

        // Session-start notification cleanup, gated by [savedConfirmPostedAt] age.
        //
        // We DO dismiss when the visible notification on [PARKING_CONFIRMATION_NOTIFICATION_ID]
        // is either (a) a stale prompt from an abandoned previous session or (b) a revert
        // card that has been visible long enough that the user has had ample opportunity to
        // act and the next driving session implicitly closes the window.
        //
        // We DO NOT dismiss when a freshly-posted revert card from a recent auto-confirm is
        // still within [ParkingDetectionConfig.confirmationResponseTimeoutMs]. This protects
        // the post-save card across a spurious IN_VEHICLE_ENTER fired by Activity Recognition
        // while the user is walking from the parked car — the bogus session would otherwise
        // wipe the user's chance to tap "Cancelar". [REFACTOR-300-FIX]
        //
        // The finally never touches notifications: [runConfirm] paths dismiss explicitly
        // (user-tap / response-timeout / failure), and the auto-confirm success path is
        // exactly what we are protecting here.
        val savedConfirmAge = savedConfirmPostedAt?.let { sessionStartMs - it }
        if (savedConfirmAge == null || savedConfirmAge > config.confirmationResponseTimeoutMs) {
            PaparcarLogger.d(
                DIAG,
                "  → session-start dismiss PARKING_CONFIRMATION (savedConfirmAge=${savedConfirmAge}ms, " +
                    "limit=${config.confirmationResponseTimeoutMs}ms)"
            )
            notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
            savedConfirmPostedAt = null
        } else {
            PaparcarLogger.d(
                DIAG,
                "  ⊘ session-start dismiss skipped — fresh revert card (age=${savedConfirmAge}ms) " +
                    "[REFACTOR-300-FIX]"
            )
        }

        // vehicleId is captured lazily when hasEverReachedDrivingSpeed first becomes true.
        // Capturing at session start (on IN_VEHICLE_ENTER) was a race: a new vehicle
        // registered between the AR signal and real movement would hijack the active slot.
        // [BUG-NEW-VEHICLE-DEFAULT]
        var activeVehicleId: String? = null
        var activeVehicleType: VehicleType? = null

        // [REFACTOR-201: harden stepJob against StepDetectorSource exceptions [BUG-COORD-112].
        //  Previously an uncaught throwable from steps().collect would cascade up and cancel
        //  the parent coroutineScope, killing the entire detection loop. Now we re-throw
        //  CancellationException (cooperative) and log everything else — stepping degrades
        //  gracefully into the slow-path / vehicle-exit confirmation paths.]
        val stepJob = launch {
            try {
                stepDetector.steps().collect {
                    // [BUG-FALSE-ENTER-WALKING] Count steps in TWO situations, with different roles:
                    //  1. Before driving speed is ever reached — the user is walking, this session
                    //     is a spurious AR ENTER. Steps drive the early-abort guard checked in the
                    //     location collector. Counted regardless of stoppedSince.
                    //  2. After driving speed has been reached AND the car is currently stopped —
                    //     the user has parked, steps are proof they exited the car. This is the
                    //     existing BUG-GARAGE-COLA-001 behaviour; gated on stoppedSince so steps
                    //     during driving (phone bouncing in pocket) don't accumulate.
                    val updated = _detectionState.updateAndGet { s ->
                        val shouldCount = !s.hasEverReachedDrivingSpeed || s.stoppedSince != null
                        if (shouldCount) s.copy(stepCount = s.stepCount + 1) else s
                    }
                    if (!updated.hasEverReachedDrivingSpeed) {
                        PaparcarLogger.d(DIAG, "  ✦ step #${updated.stepCount} (pre-drive, false-ENTER candidate)")
                        logDetection { sid -> DetectionEvent.Step(sid, nowMs(), updated.stepCount, stopped = false) }
                    } else if (updated.stoppedSince != null) {
                        PaparcarLogger.d(DIAG, "  ✦ step #${updated.stepCount} (stopped)")
                        logDetection { sid -> DetectionEvent.Step(sid, nowMs(), updated.stepCount, stopped = true) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PaparcarLogger.w(DIAG, "  ⚠ stepDetector failed — falling back to window-based confirm: ${e.message}")
            }
        }

        try {
            locations
                .takeWhile {
                    val keep = !completed
                    if (!keep) PaparcarLogger.d(DIAG, "  takeWhile=false — flow will end")
                    keep
                }
                .catch { e -> PaparcarLogger.e(DIAG, "✗ upstream flow error", e) }
                .collect { location ->
                    locationCount++
                    val now = clock()
                    val sessionAgeMs = now - sessionStartMs
                    PaparcarLogger.d(
                        DIAG,
                        "─ loc#$locationCount speed=${location.speed}m/s acc=${location.accuracy}m sessionAge=${sessionAgeMs}ms"
                    )
                    val stoppedDuration = updateStopTracking(location, now)

                    val state = _detectionState.updateAndGet { s ->
                        val origin = s.sessionOrigin ?: location
                        val distFromOrigin = io.apptolast.paparcar.domain.util.haversineMeters(
                            origin.latitude, origin.longitude,
                            location.latitude, location.longitude,
                        )
                        val hasJustReachedSpeed = !s.hasEverReachedDrivingSpeed &&
                                location.speed >= config.minimumTripSpeedMps
                        val hasJustMoved = !s.hasEverMoved &&
                                location.speed >= config.minimumTripSpeedMps &&
                                distFromOrigin >= config.minimumTripDistanceMeters
                        if (hasJustReachedSpeed) {
                            PaparcarLogger.d(DIAG, "  ✓ hasEverReachedDrivingSpeed → true (speed=${location.speed}≥${config.minimumTripSpeedMps}) dist=${distFromOrigin}m [BUG-SHORT-TRIP]")
                        }
                        if (hasJustMoved) {
                            PaparcarLogger.d(DIAG, "  ✓ hasEverMoved → true (speed≥${config.minimumTripSpeedMps}, dist≥${config.minimumTripDistanceMeters}m, actual=${distFromOrigin}m)")
                        }
                        s.copy(
                            sessionOrigin = s.sessionOrigin ?: location,
                            hasEverReachedDrivingSpeed = s.hasEverReachedDrivingSpeed || hasJustReachedSpeed,
                            hasEverMoved = s.hasEverMoved || hasJustMoved,
                            sessionStartMs = s.sessionStartMs ?: now,
                            maxSpeedMps = if (location.speed > s.maxSpeedMps) location.speed else s.maxSpeedMps,
                        )
                    }
                    PaparcarLogger.d(
                        DIAG,
                        "  state hasEverMoved=${state.hasEverMoved} hasEverReachedDrivingSpeed=${state.hasEverReachedDrivingSpeed} " +
                                "userConfirmed=${state.userConfirmedParking} " +
                                "vehicleExit=${state.vehicleExitConfirmed} stoppedSince=${state.stoppedSince} " +
                                "stoppedDur=${stoppedDuration}ms phase=${state.phase}"
                    )

                    // [DET-LOG-04] Raw-fix + AR-signal trace (the replay input stream). The fix
                    // carries speed/accuracy/position + the running stopped duration; the AR EXIT
                    // transition is edge-logged from the state flip fed by onVehicleExit.
                    logDetection { sid -> DetectionEvent.LocationFix(sid, now, location, stoppedDuration) }
                    if (state.vehicleExitConfirmed && !loggedVehicleExit) {
                        loggedVehicleExit = true
                        logDetection { sid -> DetectionEvent.ActivityTransition(sid, now, activity = "IN_VEHICLE", transition = "EXIT", location = location) }
                    } else if (!state.vehicleExitConfirmed) {
                        loggedVehicleExit = false
                    }

                    // [DET-C-02] Post-confirm hold. A tentative egress-confirm waits here to rule out
                    // an errand stop (park → walk to a kiosk → drive on to park properly): if the car
                    // drives off again before confirmHoldMs elapses, discard it and keep detecting so
                    // the saved park re-anchors at the FINAL spot. An explicit user-yes finalises now.
                    val pending = state.pendingConfirm
                    if (pending != null) {
                        val heldMs = now - pending.confirmedAt
                        val drivingResumed = location.speed > config.clearBestStopSpeedMps &&
                            location.accuracy <= config.minGpsAccuracyForDriving
                        when {
                            state.userConfirmedParking || heldMs >= config.confirmHoldMs -> {
                                PaparcarLogger.d(
                                    DIAG,
                                    "  ✓ hold settled (held=${heldMs}ms, userYes=${state.userConfirmedParking}) — finalizing tentative confirm [DET-C-02]"
                                )
                                completed = true
                                runConfirm(pending.location, pending.reliability, pending.vehicleId, pending.pathLabel)
                                return@collect
                            }
                            drivingResumed -> {
                                PaparcarLogger.d(
                                    DIAG,
                                    "  ↩ tentative confirm DISCARDED — drove off ${heldMs}ms into the hold (errand), re-anchoring [DET-C-02]"
                                )
                                _detectionState.update { it.copy(pendingConfirm = null) }
                                // Fall through: updateStopTracking already cleared bestStopLocation /
                                // steps on the driving fix, so the loop keeps detecting toward the real park.
                            }
                            else -> {
                                // Still holding (stopped, window not elapsed) — keep the session alive.
                                return@collect
                            }
                        }
                    }

                    // Fast spurious-ENTER abort by pedestrian steps. Triggers when AR fires an
                    // IN_VEHICLE_ENTER while the user is walking (typical: just got out of the
                    // car carrying bags, brisk pace). Without this, the same session would run
                    // for the full [maxNoMovementMs] (4 min) with the FGS notification glued on
                    // and could repeat as AR misfires again. [BUG-FALSE-ENTER-WALKING]
                    if (!state.hasEverReachedDrivingSpeed && state.stepCount >= config.falseEnterAbortSteps) {
                        PaparcarLogger.d(
                            DIAG,
                            "  ⊘ false-ENTER abort — ${state.stepCount} steps before driving speed " +
                                "[BUG-FALSE-ENTER-WALKING]"
                        )
                        sessionOutcome = "aborted_false_enter"
                        completed = true
                        return@collect
                    }

                    // Spurious IN_VEHICLE_ENTER guard. [BUG-NEW-VEHICLE-DEFAULT]
                    if (!state.hasEverReachedDrivingSpeed && (now - sessionStartMs) > config.maxNoMovementMs) {
                        PaparcarLogger.d(DIAG, "  ⚑ maxNoMovementMs guard hit → completed=true (spurious IN_VEHICLE_ENTER)")
                        sessionOutcome = "aborted_no_movement"
                        completed = true
                        return@collect
                    }

                    // Lock vehicleId on first driving-speed fix. [BUG-NEW-VEHICLE-DEFAULT] [BUG-SHORT-TRIP]
                    if (state.hasEverReachedDrivingSpeed && activeVehicleId == null) {
                        val v = vehicleRepository.observeActiveVehicle().first()
                        if (v == null) {
                            PaparcarLogger.w(DIAG, "  ✗ hasEverReachedDrivingSpeed but no active vehicle — abort session")
                            sessionOutcome = "aborted_no_vehicle"
                            completed = true
                            return@collect
                        }
                        activeVehicleId = v.id
                        activeVehicleType = v.vehicleType
                        PaparcarLogger.d(DIAG, "  ✓ vehicleId locked: $activeVehicleId type=$activeVehicleType")
                    }

                    // [BUG-COORD-115] precedence: user-confirm always wins.
                    if (state.userConfirmedParking) {
                        PaparcarLogger.d(DIAG, "  ▶ USER-CONFIRMED path — entering confirmParking")
                        val locationToConfirm = state.bestStopLocation ?: state.bestFix(location)
                        completed = true
                        runConfirm(
                            location = locationToConfirm,
                            reliability = config.reliabilityUserConfirmed,
                            vehicleId = activeVehicleId,
                            pathLabel = "user",
                        )
                        PaparcarLogger.d(DIAG, "  ◀ USER-CONFIRMED path done — returning from collect")
                        return@collect
                    }

                    if (!state.hasEverReachedDrivingSpeed) {
                        PaparcarLogger.d(DIAG, "  ⏸ skipping: !hasEverReachedDrivingSpeed")
                        return@collect
                    }

                    // Response-timeout abort. [BUG-STUCK-SESSION]
                    val promptShownAt = state.phase.promptShownAt
                    if (promptShownAt != null && (now - promptShownAt) > config.confirmationResponseTimeoutMs) {
                        PaparcarLogger.d(
                            DIAG,
                            "  ⑊ no user response after ${now - promptShownAt}ms (limit=${config.confirmationResponseTimeoutMs}ms) — aborting session [BUG-STUCK-SESSION]"
                        )
                        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
                        sessionOutcome = "aborted_response_timeout"
                        completed = true
                        return@collect
                    }

                    // Candidate-phase decision tree.
                    val candidate = state.phase as? ConfirmationPhase.Candidate
                    if (candidate != null) {
                        val didConfirm = evaluateCandidatePhase(
                            phase = candidate,
                            location = location,
                            state = state,
                            now = now,
                            activeVehicleId = activeVehicleId,
                            activeVehicleType = activeVehicleType,
                        )
                        if (didConfirm) completed = true
                        return@collect
                    }

                    // [DET-D-03] Steps + egress fast confirm — no AR EXIT required. The user has
                    // driven, stopped, taken ≥ minStepsToConfirm steps AND walked ≥
                    // minEgressDisplacementMeters from the parked car: that is unambiguously "parked
                    // and walked away" on its own. The egress gate is the decisive signal, so the AR
                    // IN_VEHICLE_EXIT requirement was redundant — a field trace (2026-06-26) showed the
                    // confirm needlessly waiting ~16 s for the AR EXIT while steps+egress were already
                    // satisfied, and it made detection fragile on hardware where EXIT is late or never
                    // fires. AR EXIT is now a non-decisive hint only. Anchor at bestStopLocation (the
                    // parked-car position). [supersedes BUG-OPPO-LATE-CONFIRM]
                    if (state.stepCount >= config.minStepsToConfirm) {
                        // elapsedSinceHighMs=0 → no observation window; hasStepsProof (steps + egress)
                        // is what confirms. The scooter mismatch guard still applies via the use case.
                        val decision = evaluateParkingDecision(
                            ParkingDecisionInput(
                                stepCount = state.stepCount,
                                hasEgressDisplacement = hasEgressDisplacement(state, location),
                                hadVehicleExit = state.vehicleExitConfirmed,
                                elapsedSinceHighMs = 0L,
                                vehicleType = activeVehicleType,
                                sessionDurationMs = state.sessionDurationMs(now),
                                maxSpeedKmh = state.maxSpeedKmh,
                            )
                        )
                        if (decision is ParkingDecision.Confirmed) {
                            PaparcarLogger.d(
                                DIAG,
                                "  ▶ ${state.stepCount} steps + egress → fast confirm, skipping slow path [DET-D-03]"
                            )
                            val locationToConfirm = state.bestStopLocation ?: state.bestFix(location)
                            completed = beginConfirm(
                                location = locationToConfirm,
                                reliability = decision.reliability,
                                vehicleId = activeVehicleId,
                                pathLabel = "steps+egress",
                                now = now,
                            )
                            return@collect
                        }
                        PaparcarLogger.d(
                            DIAG,
                            "  ⊘ steps+egress fast confirm gated ($decision) — anchorSet=${state.bestStopLocation != null}, falling to scoring"
                        )
                    }

                    evaluateConfidence(location, stoppedDuration, state, now)
                }
        } finally {
            stepJob.cancel()
            // [FIX BUG-SERVICE-109: reset state on session exit so cross-session reads of
            //  hasDetectedMovement and any other state fields return defaults. Without this,
            //  the next session start would briefly observe stale `hasEverReachedDrivingSpeed`.
            //  withContext(NonCancellable) so the reset survives an upstream cancellation.]
            withContext(NonCancellable) {
                // [DET-LOG-03] Close the diagnostics session before wiping state, then clear the id.
                logDetection { sid -> DetectionEvent.SessionEnded(sid, nowMs(), sessionOutcome) }
                currentSessionId = null
                reset()
            }
        }
        PaparcarLogger.d(DIAG, "■ coordinator.invoke() EXITED — locationCount=$locationCount completed=$completed")
    }

    /** Signals that the `IN_VEHICLE → EXIT` transition was received. Thread-safe. */
    fun onVehicleExit() {
        PaparcarLogger.d(DIAG, "✱ onVehicleExit() called")
        _detectionState.update { it.copy(vehicleExitConfirmed = true) }
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
        _detectionState.update {
            ParkingDetectionState(
                hasEverReachedDrivingSpeed = it.hasEverReachedDrivingSpeed,
                hasEverMoved = it.hasEverMoved,
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun reset() {
        _detectionState.value = ParkingDetectionState()
    }

    /**
     * [DET-A] True when the current fix is at least [ParkingDetectionConfig.minEgressDisplacementMeters]
     * away from [ParkingDetectionState.bestStopLocation] (the lowest-accuracy fix recorded at the
     * parked-car position).
     *
     * The displacement gate is ANDed with the pedestrian-step proof on both confirm paths so that
     * steps counted while the car never moved (phone bouncing in stop-and-go traffic) cannot
     * confirm a phantom spot. Returns false when no anchor has been captured yet — fail-negative,
     * which is the safe direction under the asymmetric-error principle.
     */
    private fun hasEgressDisplacement(state: ParkingDetectionState, current: GpsPoint): Boolean {
        val anchor = state.bestStopLocation ?: return false
        val d = io.apptolast.paparcar.domain.util.haversineMeters(
            anchor.latitude, anchor.longitude,
            current.latitude, current.longitude,
        )
        return d >= config.minEgressDisplacementMeters
    }

    /**
     * [REFACTOR: extract NonCancellable + onFailure boilerplate]
     *
     * Runs the [confirmParking] use case under NonCancellable so the save survives an
     * upstream cancellation, and on success morphs the prompt notification into the
     * post-save "Vehículo aparcado · Confirmar / Cancelar" card [REFACTOR-300]. The
     * old `notificationPort.dismiss(...)` is gone: the morph is what closes BUG-FGS-103
     * AND gives the user the revert affordance for cases where auto-confirm grabbed
     * someone else's car.
     *
     * Translates the `NotAuthenticated` transient-error case into a warn-level log.
     */
    /**
     * [DET-C-02] Begin an auto egress-confirm. With a positive [ParkingDetectionConfig.confirmHoldMs]
     * this does NOT confirm yet — it records a [PendingConfirm] and returns `false`, keeping the
     * session alive so the loop's hold handler can either finalise it (window elapsed / explicit
     * user-yes) or discard it (driving resumed → errand stop → re-anchor at the real spot). With
     * `confirmHoldMs == 0` it confirms immediately (legacy behaviour) and returns `true`.
     *
     * @return whether the caller should mark the session completed (true only on immediate confirm).
     */
    private suspend fun beginConfirm(
        location: GpsPoint,
        reliability: Float,
        vehicleId: String?,
        pathLabel: String,
        now: Long,
    ): Boolean {
        if (config.confirmHoldMs <= 0L) {
            runConfirm(location, reliability, vehicleId, pathLabel)
            return true
        }
        _detectionState.update {
            it.copy(pendingConfirm = PendingConfirm(location, reliability, vehicleId, pathLabel, confirmedAt = now))
        }
        PaparcarLogger.d(
            DIAG,
            "  ⏸ tentative confirm ($pathLabel) — holding ${config.confirmHoldMs}ms to rule out an errand stop [DET-C-02]"
        )
        return false
    }

    private suspend fun runConfirm(
        location: GpsPoint,
        reliability: Float,
        vehicleId: String?,
        pathLabel: String,
    ) {
        withContext(NonCancellable) {
            PaparcarLogger.d(DIAG, "    → confirmParking(reliability=$reliability, path=$pathLabel) START")
            // [CONFIRM-NO-NOTIF-CLEANUP] Notification responsibility lives here: the auto-detection
            // path owns the unified state-B "Vehículo aparcado · Cancelar" card so the user has a
            // revert window if AR / steps misfired. See showParkingSavedConfirm call in onSuccess.
            confirmParking(location, reliability, vehicleId = vehicleId)
                .onSuccess { saved ->
                    // [REFACTOR-300] Replace the prompt notification at the same ID with the
                    // post-save "Vehículo aparcado" card carrying ACK and REVERT actions. This
                    // unifies what used to be a "prompt → dismissed → 'saved' notif posted"
                    // double-show, and lets the user revert if detection grabbed the wrong car.
                    val vehicleName = runCatching {
                        vehicleRepository.observeActiveVehicle().first()
                            ?.let { it.displayName(fallback = "").takeIf { n -> n.isNotBlank() } }
                    }.getOrNull()
                    notificationPort.showParkingSavedConfirm(
                        parkingId = saved.id,
                        vehicleName = vehicleName,
                        latitude = saved.location.latitude,
                        longitude = saved.location.longitude,
                    )
                    // Record post time so the next session-start can decide whether the card
                    // is fresh (preserve) or stale (dismiss). [REFACTOR-300-FIX]
                    savedConfirmPostedAt = Clock.System.now().toEpochMilliseconds()
                    // [DET-LOG-03] Terminal CONFIRMED decision for the session trace.
                    sessionOutcome = "confirmed_$pathLabel"
                    logDetection { sid ->
                        DetectionEvent.Decision(sid, nowMs(), outcome = "CONFIRMED", pathLabel = pathLabel, confidence = reliability, location = location)
                    }
                }
                .onFailure { e ->
                    if (e is PaparcarError.Auth.NotAuthenticated) {
                        // Transient session loss — not a real crash. Will self-heal on next launch.
                        PaparcarLogger.w(TAG, "confirmParking ($pathLabel path) — session temporarily unavailable")
                    } else {
                        PaparcarLogger.e(TAG, "Failed to confirm parking ($pathLabel path)", e)
                    }
                    notificationPort.showConfirmationFailed()
                    // Save failed → no parkingId to revert. Just clean up the prompt.
                    notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
                    // [DET-LOG-03] Record the failed confirm in the session trace.
                    sessionOutcome = "confirm_failed_$pathLabel"
                    logDetection { sid ->
                        DetectionEvent.Decision(sid, nowMs(), outcome = "CONFIRM_FAILED", pathLabel = pathLabel, location = location)
                    }
                }
            PaparcarLogger.d(DIAG, "    ← confirmParking(reliability=$reliability, path=$pathLabel) END")
        }
    }

    /**
     * Evaluates a stop that has already reached [ConfirmationPhase.Candidate]. Three paths:
     *  1. **Step proof** (hasStepsProof) — strongest, fires the moment the user steps out.
     *  2. **Vehicle-exit fast** — window elapsed with an IN_VEHICLE→EXIT signal present.
     *  3. **Slow path** — only if steps confirm; otherwise the candidate is discarded as a
     *     likely queue / traffic stop.
     *
     * Returns true if the candidate was confirmed (caller marks the session completed).
     */
    private suspend fun evaluateCandidatePhase(
        phase: ConfirmationPhase.Candidate,
        location: GpsPoint,
        state: ParkingDetectionState,
        now: Long,
        activeVehicleId: String?,
        activeVehicleType: VehicleType?,
    ): Boolean {
        // [DET-A] Steps prove egress only when paired with displacement from the park anchor.
        val stepsReached = state.stepCount >= config.minStepsToConfirm
        val hasEgress = hasEgressDisplacement(state, location)
        if (stepsReached && !hasEgress) {
            PaparcarLogger.d(
                DIAG,
                "  ⊘ CANDIDATE steps proof gated by EGRESS — anchorSet=${state.bestStopLocation != null}, " +
                    "need ≥${config.minEgressDisplacementMeters}m walked from park anchor [DET-A]"
            )
        }

        // [DET-D-02] Delegate the verdict to the pure decision function. The orchestrator below
        // keeps the side effects (confirm, phase mutation, diagnostics).
        val elapsed = now - phase.highReachedAt
        val decision = evaluateParkingDecision(
            ParkingDecisionInput(
                stepCount = state.stepCount,
                hasEgressDisplacement = hasEgress,
                hadVehicleExit = phase.hadVehicleExit,
                elapsedSinceHighMs = elapsed,
                vehicleType = activeVehicleType,
                sessionDurationMs = state.sessionDurationMs(now),
                maxSpeedKmh = state.maxSpeedKmh,
            )
        )
        PaparcarLogger.d(
            DIAG,
            "  ⏳ CANDIDATE phase — elapsed=${elapsed}ms steps=${state.stepCount}/${config.minStepsToConfirm} → decision=$decision"
        )

        return when (decision) {
            is ParkingDecision.Confirmed -> {
                PaparcarLogger.d(DIAG, "  ▶ CANDIDATE confirmed via ${decision.pathLabel} — entering confirmParking(reliability=${decision.reliability})")
                val locationToConfirm = state.bestStopLocation ?: state.bestFix(location)
                // [DET-C-02] May hold instead of confirming now; returns true only on immediate confirm.
                beginConfirm(
                    location = locationToConfirm,
                    reliability = decision.reliability,
                    vehicleId = activeVehicleId,
                    pathLabel = decision.pathLabel,
                    now = now,
                )
            }
            ParkingDecision.Rejected -> {
                // Window expired without the egress conjunction — discard. Phase falls back to
                // Notified (preserving shownAt so the response-timeout still applies — the user can
                // still tap the visible prompt). [FIX BUG-COORD-105][REFACTOR-200]
                PaparcarLogger.d(DIAG, "  ⊘ CANDIDATE expired without egress proof — discarding [BUG-GARAGE-COLA-001]")
                _detectionState.update {
                    it.copy(
                        phase = ConfirmationPhase.Notified(phase.shownAt),
                        stepCount = 0,
                    )
                }
                logDetection { sid -> DetectionEvent.Candidate(sid, now, action = "DISCARDED", phase = "Candidate→Notified", location = location) }
                false
            }
            ParkingDecision.Inconclusive -> false
        }
    }

    /**
     * Updates `stoppedSince` / `stoppedFixes` when the vehicle is stopped, or resets
     * them when it starts moving again. Returns the total stopped duration in ms.
     *
     * At driving speed ([ParkingDetectionConfig.clearBestStopSpeedMps]) the following are
     * also cleared to prevent stale signals from polluting the next genuine stop:
     * [bestStopLocation], [vehicleExitConfirmed], and the
     * [phase] (back to [ConfirmationPhase.Idle]).
     */
    private fun updateStopTracking(location: GpsPoint, now: Long): Long {
        return if (location.speed < config.stoppedSpeedThresholdMps) {
            _detectionState.update { s ->
                val startedAt = s.stoppedSince ?: now
                val withinInitialWindow = (now - startedAt) < config.initialStopWindowMs
                // Freeze bestStopLocation after the initial-stop window (default 30 s). [LOC-001]
                val newBestStop = when {
                    !withinInitialWindow -> s.bestStopLocation
                    s.bestStopLocation == null || location.accuracy < s.bestStopLocation.accuracy -> location
                    else -> s.bestStopLocation
                }
                s.copy(
                    stoppedSince = startedAt,
                    stoppedFixes = if (withinInitialWindow && s.stoppedFixes.size < config.maxStoppedFixes)
                        s.stoppedFixes + location else s.stoppedFixes,
                    bestStopLocation = newBestStop,
                    // Reset the reposition counter on every stopped fix. [PARKING-001]
                    consecutiveRepositionFixes = 0,
                )
            }
            now - (_detectionState.value.stoppedSince ?: 0L)
        } else {
            val isDriving = location.speed >= config.clearBestStopSpeedMps &&
                    location.accuracy <= config.minGpsAccuracyForDriving
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
                // [REFACTOR-200] phase resets to Idle on isDriving. Walking pace preserves
                // the current phase so the response-timeout from a prior prompt still ticks
                // — that's how BUG-STUCK-SESSION's "walked home" abort fires.
                val nextPhase = if (isDriving) ConfirmationPhase.Idle else it.phase
                it.copy(
                    stoppedSince = null,
                    stoppedFixes = emptyList(),
                    phase = nextPhase,
                    bestStopLocation = if (shouldClearBestStop) null else it.bestStopLocation,
                    vehicleExitConfirmed = if (isDriving) false else it.vehicleExitConfirmed,
                    consecutiveRepositionFixes = newConsecutive,
                    stepCount = if (isDriving) 0 else it.stepCount,
                )
            }
            0L
        }
    }

    /**
     * Runs the confidence scorer and advances the [ConfirmationPhase] state machine.
     * On reaching [ParkingConfidence.High] for the first time, enters the [ConfirmationPhase.Candidate]
     * phase and always shows a confirmation notification (if not already shown). Does not
     * confirm immediately — the observation window in [invoke] handles auto-confirmation timing.
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
        )
        val confidence = calculateParkingConfidence(signals)
        PaparcarLogger.d(DIAG, "  ⚖ scoring=$confidence (signals: speed=${signals.speed} stopped=${signals.stoppedDurationMs}ms accuracy=${signals.gpsAccuracy} exit=${signals.activityExit})")

        // [REFACTOR-200] phase advancement via explicit transitions.
        when (confidence) {
            is ParkingConfidence.NotYet -> Unit

            is ParkingConfidence.Low,
            is ParkingConfidence.Medium -> advanceLowMedium(confidence, state, now)

            is ParkingConfidence.High -> advanceHigh(confidence, state, now)
        }
    }

    private suspend fun advanceLowMedium(
        confidence: ParkingConfidence,
        state: ParkingDetectionState,
        now: Long,
    ) {
        when (val phase = state.phase) {
            is ConfirmationPhase.Idle -> {
                _detectionState.update { it.copy(phase = ConfirmationPhase.LowReached(now)) }
                PaparcarLogger.d(DIAG, "  → phase: Idle → LowReached(firstReachedAt=$now) [BUG-DETECT-310502]")
            }

            is ConfirmationPhase.LowReached -> {
                val hasExit = state.vehicleExitConfirmed
                val timeoutReached = (now - phase.firstReachedAt) >= config.lowNotifTimeoutMs
                if (hasExit || timeoutReached) {
                    val reason = if (hasExit)
                        "exit=${state.vehicleExitConfirmed}"
                    else
                        "timeout=${now - phase.firstReachedAt}ms"
                    PaparcarLogger.d(DIAG, "  → showing parking-confirmation notif (Low/Medium, $reason)")
                    _detectionState.update { it.copy(phase = ConfirmationPhase.Notified(now)) }
                    notifyParkingConfirmation(confidence)
                } else {
                    val waitMs = config.lowNotifTimeoutMs - (now - phase.firstReachedAt)
                    PaparcarLogger.d(DIAG, "  ⊘ Low/Medium notif suppressed — no vehicleExit, timeout in ~${waitMs}ms")
                }
            }

            is ConfirmationPhase.Notified, is ConfirmationPhase.Candidate -> {
                // Already prompted; nothing to do on a Low/Medium re-evaluation.
                Unit
            }
        }
    }

    private suspend fun advanceHigh(
        confidence: ParkingConfidence,
        state: ParkingDetectionState,
        now: Long,
    ) {
        val newCandidate: (Long) -> ConfirmationPhase.Candidate = { shownAt ->
            ConfirmationPhase.Candidate(
                highReachedAt = now,
                hadVehicleExit = state.vehicleExitConfirmed,
                shownAt = shownAt,
            )
        }
        when (val phase = state.phase) {
            is ConfirmationPhase.Idle, is ConfirmationPhase.LowReached -> {
                // Prompt was never shown — fire it as part of this transition.
                PaparcarLogger.d(DIAG, "  ▶ HIGH reached — entering CANDIDATE phase + showing notif, vehicleExit=${state.vehicleExitConfirmed}")
                _detectionState.update { it.copy(phase = newCandidate(now)) }
                notifyParkingConfirmation(confidence)
                logDetection { sid -> DetectionEvent.Candidate(sid, now, action = "OPENED", phase = "from ${phase::class.simpleName}") }
            }

            is ConfirmationPhase.Notified -> {
                // Prompt already shown at phase.shownAt — preserve it so the response timeout
                // keeps ticking from the original prompt instant.
                PaparcarLogger.d(DIAG, "  ▶ HIGH reached after Notified(shownAt=${phase.shownAt}) — entering CANDIDATE phase (suppressing duplicate notif) [BUG-STUCK-SESSION]")
                _detectionState.update { it.copy(phase = newCandidate(phase.shownAt)) }
                logDetection { sid -> DetectionEvent.Candidate(sid, now, action = "OPENED", phase = "from Notified") }
            }

            is ConfirmationPhase.Candidate -> {
                // Already in CANDIDATE — keep the original highReachedAt and shownAt so the
                // observation window does not reset on every subsequent High fix.
                Unit
            }
        }
    }

    private companion object {
        const val TAG = "CoordinatorParkingDetector"
        const val DIAG = "PARKDIAG/Coord"
    }
}
