package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.domain.detection.assertionBlocksRelocation
import io.apptolast.paparcar.domain.detection.HoldAction
import io.apptolast.paparcar.domain.detection.DepartureConfirmationListener
import io.apptolast.paparcar.domain.detection.DetectionDiagnosticsTap
import io.apptolast.paparcar.domain.detection.DetectionEffectDispatcher
import io.apptolast.paparcar.domain.detection.DetectionEffectExecutor
import io.apptolast.paparcar.domain.detection.StagePass
import io.apptolast.paparcar.domain.detection.EffectOutcome
import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.domain.detection.DetectionPhaseSink
import io.apptolast.paparcar.domain.detection.SessionEpilogue
import io.apptolast.paparcar.domain.detection.VehicleFenceOwnershipPolicy
import io.apptolast.paparcar.domain.detection.isHumanPoweredRide
import io.apptolast.paparcar.domain.detection.physics.outrunsPedestrianReach
import io.apptolast.paparcar.domain.detection.physics.isCredibleFixAccuracy
import io.apptolast.paparcar.domain.detection.physics.isCredibleMovingFix
import io.apptolast.paparcar.domain.detection.physics.DriveProofBounds
import io.apptolast.paparcar.domain.detection.physics.isCorroboratedVehicleHop
import io.apptolast.paparcar.domain.detection.physics.sustainedDepartureFromAnchor
import io.apptolast.paparcar.domain.detection.physics.honestZoneRadius
import io.apptolast.paparcar.domain.detection.physics.creditSpeedBand
import io.apptolast.paparcar.domain.detection.physics.sustainedDriveWitnessed
import io.apptolast.paparcar.domain.detection.physics.walkableInsideGapMeters
import io.apptolast.paparcar.domain.detection.physics.SavedParkingShape
import io.apptolast.paparcar.domain.detection.physics.SessionOutcome
import io.apptolast.paparcar.domain.detection.state.AnchorCapture
import io.apptolast.paparcar.domain.detection.state.AnchorTrust
import io.apptolast.paparcar.domain.detection.state.ConfirmationLifecycle
import io.apptolast.paparcar.domain.detection.state.ConfirmationPhase
import io.apptolast.paparcar.domain.detection.state.anchorRestMs
import io.apptolast.paparcar.domain.detection.state.egressExceedsWalkReach
import io.apptolast.paparcar.domain.detection.state.escapesAnchorEnvelope
import io.apptolast.paparcar.domain.detection.state.hasEgressDisplacement
import io.apptolast.paparcar.domain.detection.state.hasKinematicEgressSignal
import io.apptolast.paparcar.domain.detection.state.isAnchorLocked
import io.apptolast.paparcar.domain.detection.state.isAnchorPinned
import io.apptolast.paparcar.domain.detection.state.isAnchorWalkEntered
import io.apptolast.paparcar.domain.detection.state.isEgressBornAtAnchor
import io.apptolast.paparcar.domain.detection.state.movementOutrunsSteps
import io.apptolast.paparcar.domain.detection.state.sustainedDepartureFrom
import io.apptolast.paparcar.domain.detection.stages.ConfidenceScoringStage
import io.apptolast.paparcar.domain.detection.stages.CandidateStage
import io.apptolast.paparcar.domain.detection.stages.FalseEnterAbortStage
import io.apptolast.paparcar.domain.detection.stages.HoldResolutionStage
import io.apptolast.paparcar.domain.detection.stages.FastConfirmStage
import io.apptolast.paparcar.domain.detection.stages.NoMovementBudgetStage
import io.apptolast.paparcar.domain.detection.stages.PreDriveSkipStage
import io.apptolast.paparcar.domain.detection.stages.ResponseTimeoutStage
import io.apptolast.paparcar.domain.detection.stages.UserConfirmStage
import io.apptolast.paparcar.domain.detection.stages.VehicleAttributionStage
import io.apptolast.paparcar.domain.detection.stages.DetectionStage
import io.apptolast.paparcar.domain.detection.stages.DiagnosticNote
import io.apptolast.paparcar.domain.detection.stages.SessionStage
import io.apptolast.paparcar.domain.detection.stages.detectionStageOrder
import io.apptolast.paparcar.domain.detection.stages.DetectionEffect
import io.apptolast.paparcar.domain.detection.stages.StageVerdict
import io.apptolast.paparcar.domain.detection.state.DetectionSessionState
import io.apptolast.paparcar.domain.detection.state.EgressBirth
import io.apptolast.paparcar.domain.detection.state.WalkIn
import io.apptolast.paparcar.domain.detection.state.DriveProof
import io.apptolast.paparcar.domain.detection.state.DriveProofSource
import io.apptolast.paparcar.domain.detection.state.EgressEvidence
import io.apptolast.paparcar.domain.detection.state.PendingConfirm
import io.apptolast.paparcar.domain.detection.state.SessionTelemetry
import io.apptolast.paparcar.domain.detection.state.StopTracking
import io.apptolast.paparcar.domain.detection.state.updateStopTracking
import io.apptolast.paparcar.domain.detection.physics.effectiveDriving
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import io.apptolast.paparcar.domain.usecase.parking.EvaluateUnattendedParkingSaveUseCase
import io.apptolast.paparcar.domain.usecase.parking.FinalizeDeducedDepartureUseCase
import io.apptolast.paparcar.domain.usecase.parking.RetractDeducedDepartureUseCase
import io.apptolast.paparcar.domain.usecase.parking.ParkingDecision
import io.apptolast.paparcar.domain.usecase.parking.ParkingDecisionInput
import io.apptolast.paparcar.domain.usecase.parking.PromptReason
import io.apptolast.paparcar.domain.usecase.parking.UnattendedParkingSave
import io.apptolast.paparcar.domain.usecase.parking.UnattendedSaveInput
import io.apptolast.paparcar.domain.usecase.parking.UnattendedSaveReason
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *   3. Lock `attributedVehicleId` on first driving-speed fix.
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
 * of [DetectionSessionState] and updated atomically via [MutableStateFlow.update].
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
    /** Receives the coarse [DetectionPhase] mapped from the internal confirmation phase, so Home can
     *  show a distinct "candidate / looking for spot" treatment while a trip is being evaluated.
     *  Nullable so existing test doubles need no change. [DET-PHASE-001] */
    private val phaseSink: DetectionPhaseSink? = null,
    /** Wall-clock source (epoch-ms). Injectable so the time-driven post-confirm hold [DET-C-02]
     *  can be unit-tested without sleeping. Defaults to the system clock. */
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** [DET-WALK-ENTERED-ANCHOR-ZONE-001] What the unattended timeout should do with the session —
     *  pure, config-only, same defaulting rationale as the two proofs above. */
    private val evaluateUnattendedParkingSave: EvaluateUnattendedParkingSaveUseCase =
        EvaluateUnattendedParkingSaveUseCase(config),
    /** [DET-HANDOFF-NOT-MANUAL-001 §B] Completes a departure that was only DEDUCED, at the instant
     *  this session MEASURES a drive: the deduction is now proven, so the provisional spot is
     *  promoted to the full TTL and the car is released — the commit moved from the guess to the
     *  proof. Null in test doubles that do not exercise it. */
    private val finalizeDeducedDeparture: FinalizeDeducedDepartureUseCase? = null,
    /** [DET-HANDOFF-NOT-MANUAL-001 §B.3] The other half of the same pair: this session ENDED without
     *  ever measuring a drive, so the departure it was deduced from is refuted and the spot it
     *  published provisionally is withdrawn. Null in test doubles that do not exercise it. */
    private val retractDeducedDeparture: RetractDeducedDepartureUseCase? = null,
) : DepartureConfirmationListener {

    private val _detectionState = MutableStateFlow(DetectionSessionState())

    /**
     * Epoch-ms when [AppNotificationManager.showParkingSavedConfirm] was last posted by
     * the executor. Lives across [invoke] calls (the coordinator is a Koin single) so the
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
    /** [09 §7] The single emitter, and the owner of the one-per-session markers. */
    private val diagnostics = DetectionDiagnosticsTap(detectionEventLogger)

    /**
     * [09 §4] The only place in the core that performs I/O. It does the side effect and REPORTS what
     * the session must record; applying that is [DetectionEffectDispatcher]'s job, because the
     * session state has exactly one owner and it is not the thing doing the I/O.
     */
    private val effects = DetectionEffectExecutor(
        confirmParking = confirmParking,
        notifyParkingConfirmation = notifyParkingConfirmation,
        notificationPort = notificationPort,
        vehicleRepository = vehicleRepository,
        config = config,
        diagnostics = diagnostics,
        nowMs = ::nowMs,
    )

    /**
     * [09 §4] The layer between a stage's request and the executor's I/O: it makes the calls and
     * turns what they report into session state. The executor still never reads the session, and no
     * stage still imports a repository — those are P3.11's criteria and they are untouched.
     */
    private val dispatcher = DetectionEffectDispatcher(
        state = _detectionState,
        effects = effects,
        diagnostics = diagnostics,
        vehicleRepository = vehicleRepository,
        config = config,
        nowMs = ::nowMs,
    )


    // ── DETECTION DIAGNOSTICS (DET-LOG-03) ────────────────────────────────────
    /** Id of the in-flight session (= its start epoch-ms as string). Set at [invoke] entry,
     *  cleared in the finally. Null between sessions. Used to tag every [DetectionEvent]. */
    @Volatile private var currentSessionId: String? = null

    /** [DET-HOLD-BRANCHES-MUST-SPEAK-001] A held confirm the user's stop dropped. The entrypoint
     *  is not suspend and the coordinator owns no scope, so the note is handed to the epilogue —
     *  which is suspend and runs microseconds later, when the caller cancels the job. */
    @Volatile private var heldConfirmDroppedByUser: PendingConfirm? = null

    /** What the session that just finished left behind. Written once, in the `finally`, from the
     *  state as it stood before [reset] wiped it — see [SessionEpilogue]. */
    @Volatile private var epilogue: SessionEpilogue = SessionEpilogue()

    /** [DET-HONEST-CLOSE-001] The terminal outcome of the session that just finished — the same
     *  label the [DetectionEvent.SessionEnded] carried. Read by the detection service after
     *  [invoke] returns to decide whether to run the honest-close ladder (on `aborted_false_enter`
     *  / `aborted_no_movement`). */
    val lastSessionOutcome: String get() = epilogue.outcome.serialized

    /** [11 bug #3] The same ending, typed — what the service asks its membership questions of.
     *  [lastSessionOutcome] stays the wire form for telemetry. */
    val lastOutcome: SessionOutcome get() = epilogue.outcome

    /** [DET-HONEST-CLOSE-001] Position at the abort moment (last processed fix, or the stop anchor
     *  fallback), or null when no fix was seen. The honest-close ladder's candidate new spot. */
    val lastSessionFix: GpsPoint? get() = epilogue.lastFix

    /** [DET-FROZEN-COUNTER-001] Diagnostics id of the session that just finished, so post-session
     *  actors (the honest-close ladder in the service) can log under the same trace. */
    val lastSessionId: String? get() = epilogue.sessionId

    /** [DET-FROZEN-COUNTER-001] Steps the finished session's own wakeup step DETECTOR counted —
     *  the cumulative counter's liveness witness for the honest-close ladder. */
    val lastSessionStepEvents: Int get() = epilogue.stepEvents

    /** [DET-FROZEN-COUNTER-001] Max GPS speed (m/s) the finished session PROVED — measured
     *  movement outranks the step inference in the honest-close ladder. */
    val lastSessionMaxSpeedMps: Float get() = epilogue.maxSpeedMps

    /** The session is over. One statement, so "who ended it" has one answer and the three sibling
     *  coroutines stop needing a closure over a local `var`. */
    private fun endSession() {
        _detectionState.update { it.copy(session = it.session.sessionCompleted()) }
    }

    /** Emits a [DetectionEvent] for the current session, or no-ops if no session is active.
     *  The logger contract guarantees this never throws and never blocks on network. */
    private suspend fun logDetection(build: (sessionId: String) -> DetectionEvent) =
        diagnostics.emit(build)


    private fun nowMs(): Long = clock()

    /**
     * True once the coordinator has observed GPS movement meeting the trip thresholds
     * ([ParkingDetectionConfig.minimumTripSpeedMps] AND [ParkingDetectionConfig.minimumTripDistanceMeters]).
     *
     * In-session only. Cross-session, [BUG-SERVICE-109] is closed by the `finally { reset() }`
     * inside [invoke]; this property therefore returns `false` between sessions.
     */
    val hasDetectedMovement: Boolean get() = _detectionState.value.session.driveAuthorized

    /** [09 §5] The vehicle this session resolved, read LIVE: attribution happens mid-iteration and
     *  the readers downstream of it must see it, exactly as they did when this was a local `var`. */
    private val attributedVehicleId: String? get() = _detectionState.value.session.attributedVehicleId
    private val attributedVehicleType: VehicleType? get() = _detectionState.value.session.attributedVehicleType

    /**
     * [DET-G-05] Live upgrade from the sibling departure pipeline: `DepartureDetectionWorker`
     * confirmed the geofence exit was a real drive-away AFTER this session was armed unverified
     * (no vehicle evidence at arm time — AR ENTER can take up to ~2 min to deliver). Seeds
     * [DetectionSessionState.session.driveAuthorized] on the RUNNING session so the confirm
     * paths unlock — same effect as arming with `armedByConfirmedDeparture=true`, but only once
     * the evidence actually arrived. No-ops between sessions and when already seeded.
     */
    override fun notifyDepartureConfirmed() {
        if (currentSessionId == null) return
        // The worker MEASURED this one (a fresh fix at driving speed, past the independence gap),
        // so the seed is no longer on trust and can never be retracted afterwards — and the
        // evidence label that says so moves WITH it, in one transition.
        // [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001]
        val alreadyAuthorized = _detectionState.value.session.driveAuthorized
        _detectionState.update { it.copy(session = it.session.departureConfirmed()) }
        if (alreadyAuthorized) return
        PaparcarLogger.d(DIAG, "  ✓ departure confirmed post-arm → seed hasEverReachedDrivingSpeed=true [DET-G-05]")
    }

    /**
     * [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] The departure this session was armed on was
     * REFUTED. Take back what the arm lent on trust — and nothing else.
     *
     * Three conditions, all necessary:
     *  - a session is running (nothing to retract between sessions);
     *  - it is the session THIS fence armed (a different fence's verdict says nothing about it);
     *  - the seed is still unearned. Once any measurement has backed it, the drive is a fact of
     *    this trip and the EXIT's adjudication cannot undo it.
     *
     * Retracting restores every anti-walking guard (false-ENTER abort, no-movement budget, the
     * steps+egress gate) — which is precisely what the 2026-08-22 session needed: it had already
     * counted 9 pedestrian steps indoors when this verdict landed.
     */
    override fun notifyDepartureDismissed(geofenceId: String) {
        if (currentSessionId == null) return
        if (currentArmGeofenceId != geofenceId) return
        val session = _detectionState.value.session
        if (!session.driveAuthorized || !session.authorizedOnArmTrustOnly) return
        _detectionState.update { it.copy(session = it.session.departureDismissed()) }
        PaparcarLogger.w(
            DIAG,
            "  ⤺ departure DISMISSED post-arm (geof=${geofenceId.take(8)}) — retracting the unearned " +
                "seed; this session must measure the drive itself [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001]",
        )
    }

    /** Arm-evidence label of the in-flight session (see [ArmEvidence] label constants).
     *  Set at [invoke] entry, upgraded by [notifyDepartureConfirmed], downgraded by
     *  [notifyDepartureDismissed]. [DET-SOLID-001] */

    /** [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] The fence whose EXIT armed the in-flight
     *  session — the address a `Dismissed` verdict must match to retract anything. */
    @Volatile private var currentArmGeofenceId: String? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs the detection loop until a parking spot is confirmed or [locations] ends.
     * Resets all session state on entry and on exit, and dismisses any stale
     * confirmation notification.
     */
    suspend operator fun invoke(
        locations: Flow<GpsPoint>,
        /** Typed evidence behind this arm. [ArmEvidence.isVerifiedDeparture] evidence seeds
         *  [DetectionSessionState.session.driveAuthorized] — the arm fired MID-trip (the car
         *  already crossed its parked-car geofence radius, provenly driving), so this session's
         *  own GPS stream cannot be relied on to re-observe driving speed on a short hop.
         *  [ArmEvidence.Manual] / [ArmEvidence.Unverified] arms keep every anti-walking guard
         *  active: their stream is expected to witness the drive itself. [DET-G-04][DET-SOLID-001] */
        armEvidence: ArmEvidence = ArmEvidence.Manual,
        /** The vehicle whose geofence exit NOMINATED this trip (the fence that fired identifies the
         *  car). Preferred over the current active vehicle when locking attribution, so a swap-race
         *  or a nominated trip with a stale active flag still plants the pin on the right car. Null
         *  for manual / AR-armed trips with no nominating fence. [VEH-ACTIVE-FENCE-001] */
        nominatingVehicleId: String? = null,
        /** [DET-ZOMBIE-PROBE-001] The arm came from a FAR-delivered (stale-lane) geofence EXIT.
         *  Shrinks the no-movement budget to [ParkingDetectionConfig.staleExitNoMovementMs]: a
         *  zombie delivery (event held for hours, handed over with the phone parked at home) can
         *  never show driving and used to burn the full 4-min GPS window per delivery. A real
         *  mid-drive far exit shows driving fixes immediately (the car is moving by construction)
         *  and escapes the guard within the probe. Only meaningful for UNVERIFIED evidence —
         *  verified arms seed [DetectionSessionState.session.driveAuthorized] and never
         *  consult this guard. */
        staleExitDelivery: Boolean = false,
        /** [DET-SHORT-HOP-PROOF-001] The pin the car LEFT (the nominating fence's parked position).
         *  Reference for the displacement-based drive proof — a position the car provably occupied,
         *  which is exactly what makes the indoor-mirage class impossible. Null for manual / AR
         *  arms with no origin pin: then only the speed-based proof applies. */
        departureAnchor: GpsPoint? = null,
        /** Radius of the fence the car left — the user could already have been anywhere inside it
         *  when the clock started, so it counts in favour of "walkable". [DET-SHORT-HOP-PROOF-001] */
        departureFenceRadiusMeters: Float = 0f,
        /** [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] The fence whose EXIT armed this session, so
         *  a later `Dismissed` verdict from the departure worker can be matched to the arm it
         *  refutes. Null for manual / sentry / handoff arms, which never borrowed a fence's word
         *  and therefore have nothing to retract. */
        armingGeofenceId: String? = null,
        /** [DET-ASSERTION-OUTRANKS-INFERENCE-001] The vehicle's ACTIVE parked session as it stood
         *  when this one armed — read once here rather than per decision, the same way
         *  [armEvidence] and [armingGeofenceId] are captured. Only its position and
         *  `detectionReliability` are consulted, and only to answer one question: would confirming
         *  here MOVE a pin the user asserted? Null when the vehicle holds no active pin. */
        activeParkedPin: UserParking? = null,
    ) = coroutineScope {
        val sessionJob = coroutineContext[kotlinx.coroutines.Job]
        val sessionStartMs = clock()
        val thisSessionId = sessionStartMs.toString()
        // [DET-AUDIT-002 T8/M1] Ownership claim FIRST — before reset() touches the shared
        // singleton state. cancel() on the previous session's job is asynchronous: its finally
        // could run AFTER this entry and wipe the NEW session's id and seeds. With the claim in
        // place, a superseded finally sees a foreign id and keeps its hands off (see the guard
        // in this function's finally).
        currentSessionId = thisSessionId
        // [09 §7] The tap follows the session id, so an emission can never land under the previous
        // session's document — and the one-per-session markers start clean.
        diagnostics.open(thisSessionId)
        PaparcarLogger.d(DIAG, "▶ coordinator.invoke() entry (armEvidence=${armEvidence.persistLabel}) — calling reset()")
        reset()

        // [DET-G-04] Seed hasEverReachedDrivingSpeed when the arm carries VERIFIED departure
        // evidence — the drive already happened and this session cannot re-observe it. The gate —
        // and the [falseEnterAbortSteps] guard it feeds — protects unverified/manual arms: an arm
        // with no vehicle evidence (walking exit, spurious trigger) must abort on the step burst
        // instead of confirming a phantom park (BUG-REPark-WALK-001). [DET-SOLID-001]
        if (armEvidence.isVerifiedDeparture) {
            // [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] Flagged as GRANTED ON TRUST: nothing in
            // this session has measured a drive yet. The flag clears on the first measurement and
            // makes the seed retractable until then — the worker is still adjudicating this exit.
            _detectionState.update { it.copy(session = it.session.seededOnArmTrust()) }
            PaparcarLogger.d(DIAG, "  ✓ ${armEvidence.persistLabel} → seed hasEverReachedDrivingSpeed=true (armed mid-trip; drive already happened) [DET-G-04]")
        }
        currentArmGeofenceId = armingGeofenceId
        // Session provenance stamped on the confirmed park — the repark-plausibility guard in
        // ConfirmParkingUseCase bypasses verified arms and interrogates self-observed ones.
        // Upgraded live by notifyDepartureConfirmed. [DET-SOLID-001]
        // [DET-ASSERTION-OUTRANKS-INFERENCE-001] The asserted pin travels with them: a SNAPSHOT, not
        // a live read — the question is whether THIS session may relocate the pin that existed when
        // it armed.
        _detectionState.update {
            it.copy(
                session = it.session.armed(armEvidence.persistLabel, nominatingVehicleId, activeParkedPin),
            )
        }

        // The exit line below is logged AFTER the finally, and the finally wipes the state — so the
        // flag it reports has to be read before the wipe, exactly as the local `var` it replaced was.
        var completedAtExit = false

        // [DET-JAM-WINDOW-001] Whether this session earned the extended no-movement budget by
        // measured recent creep — logged once, and folds under the distinct jam outcome label.
        var jamExtensionLogged = false
        // [DET-JAM-WINDOW-001] Rolling window of credible pre-drive fixes; recent creep = the
        // displacement between its oldest and newest entries. Session-scoped, cleared with invoke.
        val creepWindow = ArrayDeque<Pair<Long, GpsPoint>>()

        // Spurious IN_VEHICLE_ENTER guard. [BUG-NEW-VEHICLE-DEFAULT]
        // [DET-ZOMBIE-PROBE-001] A stale-delivered EXIT gets the SHORT probe: a real
        // mid-drive far delivery shows driving fixes within seconds, a zombie delivery
        // never will — no point burning the full window on a phone sitting at home.
        // [DET-JAM-WINDOW-001] A TRAFFIC-JAM CRAWL is neither: the car leaves the spot
        // but creeps below driving speed past the 4-min budget (a long light, a jam at
        // the exit), and the silent fold lost the whole trip's coverage. Measured creep
        // (displacement ≥ jamCreepMinMeters from the session origin) buys the session
        // the extended budget — a stationary spurious arm shows only GPS noise and
        // keeps folding at the standard budget, so the OEM power profile of false
        // starts is unchanged. Stale-lane zombies never get the extension.
        //
        // A local function rather than a member: the creep window and the extension latch are
        // per-SESSION bookkeeping, so they live and die with this `invoke` exactly as the deque did.
        suspend fun runNoMovementBudget(location: GpsPoint, now: Long): StagePass {
            if (!_detectionState.value.session.driveAuthorized) {
                if (location.accuracy <= JAM_CREEP_MAX_ACCURACY_M) creepWindow.addLast(now to location)
                while (creepWindow.isNotEmpty() && now - creepWindow.first().first > config.jamCreepWindowMs) {
                    creepWindow.removeFirst()
                }
            }
            val recentCreepMeters = if (creepWindow.size >= 2) {
                val oldest = creepWindow.first().second
                val newest = creepWindow.last().second
                io.apptolast.paparcar.domain.util.haversineMeters(
                    oldest.latitude, oldest.longitude, newest.latitude, newest.longitude,
                )
            } else {
                0.0
            }
            val budgetVerdict = noMovementBudgetStage.evaluate(
                state = _detectionState.value,
                fix = location,
                sessionAgeMs = now - sessionStartMs,
                staleExitDelivery = staleExitDelivery,
                recentCreepMeters = recentCreepMeters,
                extensionAlreadyAnnounced = jamExtensionLogged,
                config = config,
            )
            budgetVerdict.notes.forEach { PaparcarLogger.d(DIAG, it.text) }
            if (budgetVerdict !is StageVerdict.Handled) return StagePass(endsPass = false, endsSession = false)
            // The extension latch is the loop's: it exists so the trace carries one line
            // per session instead of one per fix, and the stage only reports whether it
            // has already spoken.
            if (budgetVerdict.notes.any { it.claim == DiagnosticNote.Claim.NO_MOVEMENT_BUDGET_EXTENDED }) {
                jamExtensionLogged = true
            }
            val budgetPass = dispatcher.run(budgetVerdict.effects, now)
            return StagePass(endsPass = budgetVerdict.stopsIteration, endsSession = budgetPass.endsSession)
        }

        // [DET-LOG-04] Edge-detect the AR signals so each transition is logged once (not on every
        // subsequent fix). Reset to false when the signal clears (driving away), so a re-entry logs again.
        var loggedVehicleExit = false
        // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C] Edge markers for the AR EVIDENCE lane. Seeded
        // to 0 (not to the current state) on purpose: a stamp INHERITED from before this session —
        // the singleton state is only reset when a session ends, so a cycling stamp delivered
        // between sessions rides into the next one — gets logged on the first fix, with its true
        // age. That inheritance is invisible today and it is the hardest kind of veto to explain
        // after the fact.
        var loggedBicycleRideAtMs = 0L
        var loggedVehicleRideAtMs = 0L
        var loggedMotorWitnessed = false
        // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C] The OTHER source of the human-powered veto. The
        // AR lane above is now in the trace; the cadence latch was still logcat-only, and a trace
        // that shows no cycling stamp and no cadence line leaves the reader inferring the veto by
        // elimination — which is exactly how the 2026-08-20 Oppo session (63 km/h, three verdicts
        // degraded) stayed unattributable. Owned by the step collector alone, so a plain flag is
        // enough; the fix collector never touches it.

        // [DET-LOG-03] Diagnostics session id claimed at entry (T8). The outcome defaults to
        // "ended" — [reset] above put it there — and is refined by the abort paths and by the
        // confirm before the finally emits SessionEnded.
        logDetection { sid -> DetectionEvent.SessionStarted(sid, sessionStartMs, strategy = "COORDINATOR", evidence = _detectionState.value.session.armEvidence) }

        // Session-start notification cleanup, gated by the executor's saved-confirm age.
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
        val savedConfirmAge = effects.savedConfirmPostedAt?.let { sessionStartMs - it }
        if (savedConfirmAge == null || savedConfirmAge > config.confirmationResponseTimeoutMs) {
            PaparcarLogger.d(
                DIAG,
                "  → session-start dismiss PARKING_CONFIRMATION (savedConfirmAge=${savedConfirmAge}ms, " +
                    "limit=${config.confirmationResponseTimeoutMs}ms)"
            )
            notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
            effects.forgetSavedConfirm()
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
        // [09 §5] Vehicle attribution and the fix counter live in DetectionSessionState.session.

        // [DET-HANDOFF-NOT-MANUAL-001 §B] One-shot: the first measured drive settles any departure
        // this trip's arm was deduced from. Later fixes have nothing left to settle.
        var deducedDepartureSettled = false

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
                    // [DET-SOLID-001][B4] Enter-arm step veto (config-gated, default OFF): the
                    // FIRST step arriving suspiciously soon after a VerifiedByVehicleEnter arm,
                    // with no driving observed by the stream, marks the ENTER as spurious
                    // (walking, BUG-FALSE-ENTER-WALKING hardware quirk) — degrade the evidence
                    // and un-seed so the false-ENTER abort guard re-arms.
                    if (config.enterArmStepVetoMs > 0 &&
                        _detectionState.value.session.armEvidence == ArmEvidence.LABEL_VERIFIED_ENTER &&
                        _detectionState.value.egress.stepCount == 0 &&
                        (clock() - sessionStartMs) < config.enterArmStepVetoMs &&
                        _detectionState.value.drive.provenMaxSpeedMps < config.minimumTripSpeedMps
                    ) {
                        PaparcarLogger.d(DIAG, "  ⊘ enter-arm step veto — first step ${clock() - sessionStartMs}ms after arm, no driving seen → evidence degraded to self_observed [DET-SOLID-001]")
                        _detectionState.update { it.copy(session = it.session.enterArmStepVeto()) }
                    }
                    // [DET-AR-FIRST-001 F3] Post-drive steps also count while the park ANCHOR is
                    // set even though GPS reads walking movement: those steps ARE the user's
                    // egress walk. Gating them on `stoppedSince` starved the count the moment the
                    // walk began (field 2026-07-10, Camelias: 3 steps at the kerb, then ZERO for
                    // the whole walk into the house — the person/car discriminator and the
                    // steps+egress confirm both ran blind). Driving still flushes the anchor AND
                    // the count, so jam jiggle cannot accumulate across stops.
                    val stepAtMs = clock()
                    val updated = _detectionState.updateAndGet { s ->
                        // [DET-STEP-SPEED-GATE-001][DET-MOTOR-PROOF-001]
                        // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001][DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001]
                        // The counting gate and the cadence reading both live in the sub-state that
                        // owns the counters; the anchor's state is PRESENTED, never copied in.
                        s.copy(
                            egress = s.egress.onStepEvent(
                                stepAtMs = stepAtMs,
                                driveAuthorized = s.session.driveAuthorized,
                                stopped = s.anchorTrust.stopStartedAt != null,
                                anchorPresent = s.anchorTrust.anchor != null,
                                anchorPinned = s.isAnchorPinned(config),
                                lastFixSpeedMps = s.session.lastSpeedMps,
                                lastFixCredible = s.drive.lastFixCredible,
                                lastFixSeenAtMs = s.drive.lastFixSeenAtMs,
                                pedestrianCeilingMps = config.egressStepMaxSpeedMps,
                                motorProofSpeedMps = config.motorProofSpeedMps,
                                cadenceFixFreshnessMs = config.pedalCadenceFixFreshnessMs,
                            ),
                        )
                    }
                    // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C] Edge-logged the first time the latch
                    // actually HOLDS — both halves at once. The previous edge fired on the step that
                    // made the event count equal the threshold, and its own comment conceded the
                    // miss: a session whose second distinct fix arrives later satisfies the verdict
                    // with no line at all. A veto that can decide a session silently is the defect,
                    // so the marker is a latch, not an equality.
                    if (updated.egress.fastMotionStepEvents >= config.pedalCadenceMinStepEvents &&
                        updated.egress.fastMotionStepFixes >= config.pedalCadenceMinFixes &&
                        diagnostics.latchOnce(DetectionDiagnosticsTap.Latch.PEDAL_CADENCE)
                    ) {
                        PaparcarLogger.d(
                            DIAG,
                            "  ♲ pedal cadence — ${updated.egress.fastMotionStepEvents} steps concurrent with " +
                                "${updated.egress.fastMotionStepFixes} above-ceiling fixes → human-powered ride, " +
                                "automatic saves degrade to a prompt [DET-MOTOR-PROOF-001]"
                        )
                        // …and into the trace, not just logcat. A trail that only exists while the
                        // phone is tethered to a PC is no trail at all: nobody drives cabled, and
                        // the logcat ring had already rotated past this decision when it was first
                        // investigated. The band is stamped too, because §A's ceiling changed what
                        // counts and a later trace must show which rule produced the latch.
                        logDetection { sid ->
                            DetectionEvent.Decision(
                                sid, nowMs(), outcome = "PEDAL_CADENCE_LATCHED",
                                pathLabel = "steps=${updated.egress.fastMotionStepEvents} " +
                                    "fixes=${updated.egress.fastMotionStepFixes} band=" +
                                    "${config.egressStepMaxSpeedMps}-${config.motorProofSpeedMps}mps",
                            )
                        }
                    }
                    if (!updated.session.driveAuthorized) {
                        PaparcarLogger.d(DIAG, "  ✦ step #${updated.egress.stepCount} (pre-drive, false-ENTER candidate)")
                        logDetection { sid -> DetectionEvent.Step(sid, nowMs(), updated.egress.stepCount, stopped = false) }
                    } else if (updated.anchorTrust.stopStartedAt != null) {
                        PaparcarLogger.d(DIAG, "  ✦ step #${updated.egress.stepCount} (stopped)")
                        logDetection { sid -> DetectionEvent.Step(sid, nowMs(), updated.egress.stepCount, stopped = true) }
                    } else if (updated.anchorTrust.anchor != null) {
                        PaparcarLogger.d(DIAG, "  ✦ step #${updated.egress.stepCount} (egress walk, anchor set) [DET-AR-FIRST-001]")
                        logDetection { sid -> DetectionEvent.Step(sid, nowMs(), updated.egress.stepCount, stopped = false) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PaparcarLogger.w(DIAG, "  ⚠ stepDetector failed — falling back to window-based confirm: ${e.message}")
            }
        }

        // Mirror the internal confirmation phase to the UI as a coarse [DetectionPhase], so Home shows
        // a distinct "candidate" treatment the moment the user stops and starts walking away. A reactive
        // collector covers every phase mutation (and every return@collect path) with one emit point;
        // cancelled in the finally alongside stepJob. [DET-PHASE-001]
        val phaseJob = phaseSink?.let { sink ->
            launch {
                _detectionState
                    .map { it.confirmation.phase.toDetectionPhase() }
                    .distinctUntilChanged()
                    .collect { sink.setPhase(it) }
            }
        }

        // [DET-AUDIT-002 T7/M2] Hold watchdog: every hold decision above is driven by the NEXT
        // GPS fix — and the common egress (walking into a building/garage right after parking)
        // is exactly when the stream starves. Without this, a tentatively-confirmed park died in
        // silence: no fix ever arrived to finalize it. A clock, not a fix, now closes the hold:
        // if the window (plus margin for the settling fix) elapses with the SAME confirm still
        // pending, finalize it at the pinned location and end the session. collectLatest cancels
        // the timer whenever the pending slot changes (fix-driven finalize or errand discard).
        // [DET-CONFIRM-FRESHNESS-001] Deliberately NOT re-validated: there is no fix to re-validate
        // against — a starved stream is the walked-into-a-building egress, not a car driving off
        // (a moving car keeps producing fixes; asymmetric risk accepted and documented).
        val holdWatchdogJob = if (config.confirmHoldMs > 0) {
            launch {
                _detectionState
                    .map { it.confirmation.pendingConfirm }
                    .distinctUntilChanged()
                    .collectLatest { pending ->
                        if (pending == null) return@collectLatest
                        delay(config.confirmHoldMs + HOLD_WATCHDOG_MARGIN_MS)
                        val live = _detectionState.value
                        if (!live.session.completed && live.confirmation.pendingConfirm === pending) {
                            PaparcarLogger.w(
                                DIAG,
                                "  ⚑ hold starved of fixes for ${config.confirmHoldMs + HOLD_WATCHDOG_MARGIN_MS}ms — finalizing the held confirm at the pinned location [DET-AUDIT-002 T7]"
                            )
                            // [DET-HOLD-BRANCHES-MUST-SPEAK-001] A pin planted with NO fix to
                            // re-validate it. Deliberate, but a trace has to say so — in forensics
                            // this is what "a spot appeared and I don't know why" looks like.
                            effects.logHold(
                                HoldAction.STARVED,
                                heldMs = config.confirmHoldMs + HOLD_WATCHDOG_MARGIN_MS,
                                pathLabel = pending.pathLabel,
                                location = pending.location,
                            )
                            val finalized = dispatcher.apply(
                                effects.confirm(
                                    _detectionState.value, pending.location, pending.reliability,
                                    pending.vehicleId, pending.pathLabel,
                                ),
                            )
                            if (finalized) {
                                endSession()
                                // The collect loop is suspended on a starved stream — cancelling
                                // the session scope is what actually ends the session. The save
                                // already ran under NonCancellable; the finally logs SessionEnded.
                                sessionJob?.cancel(CancellationException("hold-watchdog-finalized [DET-AUDIT-002 T7]"))
                            }
                        }
                    }
            }
        } else {
            null
        }

        try {
            locations
                .takeWhile {
                    val keep = !_detectionState.value.session.completed
                    if (!keep) PaparcarLogger.d(DIAG, "  takeWhile=false — flow will end")
                    keep
                }
                .catch { e -> PaparcarLogger.e(DIAG, "✗ upstream flow error", e) }
                .collect { location ->
                    _detectionState.update { it.copy(session = it.session.countFix()) }
                    val locationCount = _detectionState.value.session.fixCount
                    val now = clock()
                    val sessionAgeMs = now - sessionStartMs
                    PaparcarLogger.d(
                        DIAG,
                        "─ loc#$locationCount lat=${location.latitude} lon=${location.longitude} speed=${location.speed}m/s acc=${location.accuracy}m sessionAge=${sessionAgeMs}ms"
                    )
                    // [09 §5] The fix reduction that precedes the precedence: is the car stopped,
                    // and where does that leave the anchor? Its own file since P3.13. The CAS keeps
                    // the write atomic against the step collector; what changed is that the trace
                    // lines the reduction produces come back as data and are said ONCE, after the
                    // winning attempt — a retry no longer repeats them [07 §4.2].
                    var tracked: StopTracking? = null
                    _detectionState.update { s ->
                        s.updateStopTracking(location, now, config).also { tracked = it }.state
                    }
                    val stopTracking = requireNotNull(tracked)
                    stopTracking.notes.forEach { PaparcarLogger.d(DIAG, it.text) }
                    val stoppedDuration = stopTracking.stoppedDurationMs

                    val state = _detectionState.updateAndGet { s ->
                        val origin = s.session.origin ?: location
                        val distFromOrigin = io.apptolast.paparcar.domain.util.haversineMeters(
                            origin.latitude, origin.longitude,
                            location.latitude, location.longitude,
                        )
                        // [DET-SOLID-001] A driving-speed crossing is only trusted from a fix whose
                        // accuracy is credible: a single degraded fix (walking, acc 80–200 m) used
                        // to flip hasEverReachedDrivingSpeed and unlock every confirm path — the
                        // same hole the DET-G-04 seed opened, but via GPS noise. Same 50 m gate
                        // that already protects the driving-clears-anchor decision [LOC-002].
                        val credibleSpeedFix = isCredibleFixAccuracy(location, config.minGpsAccuracyForDriving)
                        val hasJustReachedSpeed = !s.session.driveAuthorized &&
                                location.speed >= config.minimumTripSpeedMps &&
                                credibleSpeedFix
                        val hasJustMoved = !s.session.hasEverMoved &&
                                location.speed >= config.minimumTripSpeedMps &&
                                credibleSpeedFix &&
                                distFromOrigin >= config.minimumTripDistanceMeters
                        if (hasJustReachedSpeed) {
                            PaparcarLogger.d(DIAG, "  ✓ hasEverReachedDrivingSpeed → true (speed=${location.speed}≥${config.minimumTripSpeedMps}) dist=${distFromOrigin}m [BUG-SHORT-TRIP]")
                        }
                        if (hasJustMoved) {
                            PaparcarLogger.d(DIAG, "  ✓ hasEverMoved → true (speed≥${config.minimumTripSpeedMps}, dist≥${config.minimumTripDistanceMeters}m, actual=${distFromOrigin}m)")
                        }
                        // [DET-DRIVE-PROOF-001][DET-SHORT-HOP-PROOF-001] Both proofs, the promotion,
                        // the ring and the two band clocks live in the sub-state that owns them; the
                        // departure pin, its fence and the session clock are PRESENTED.
                        val newDrive = s.drive.onFix(
                            fix = location,
                            nowMs = now,
                            credibleSpeedFix = credibleSpeedFix,
                            departureAnchor = departureAnchor,
                            departureFenceRadiusMeters = departureFenceRadiusMeters,
                            elapsedSinceArmMs = now - sessionStartMs,
                            bounds = driveProofBounds,
                            config = config,
                        )
                        if (newDrive.isProven && !s.drive.isProven) {
                            val how = when (newDrive.proven) {
                                DriveProofSource.SHORT_HOP -> "displacement from the pin [DET-SHORT-HOP-PROOF-001]"
                                else -> "track [DET-DRIVE-PROOF-001]"
                            }
                            PaparcarLogger.d(DIAG, "  ✓ drive PROVEN by $how — session speed statistic unlocked (pendingMax=${newDrive.peakMps}m/s)")
                        }
                        if (newDrive.drivingBandMs >= config.sustainedDriveProofMs && s.drive.drivingBandMs < config.sustainedDriveProofMs) {
                            PaparcarLogger.d(DIAG, "  ✓ sustained drive — ${newDrive.drivingBandMs}ms accumulated in the driving band (≥${config.sustainedDriveProofMs}ms) [DET-MOTOR-PROOF-001]")
                        }
                        if (newDrive.motorBandMs >= config.sustainedDriveProofMs && s.drive.motorBandMs < config.sustainedDriveProofMs) {
                            PaparcarLogger.d(DIAG, "  ✓ MOTOR witnessed — ${newDrive.motorBandMs}ms held above ${config.motorProofSpeedMps} m/s; no bicycle claim can stand against this session [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001]")
                        }
                        // [09 §5] Rules 1 and 2 of the fix reduction, as one indivisible step: the
                        // session's authorization is settled by the proof produced by THIS fix, not
                        // by the previous one. See [DetectionSessionState.onFix].
                        s.onFix(
                            newDrive = newDrive,
                            fix = location,
                            nowMs = now,
                            reachedDrivingSpeed = hasJustReachedSpeed,
                            moved = hasJustMoved,
                        )
                    }
                    // [DET-HANDOFF-NOT-MANUAL-001 §B] The car moved, and now it is MEASURED: any
                    // departure that was published on a mere deduction becomes real here — promote
                    // the provisional spot, release the session, drop its geofence. Nothing was
                    // taken from the user until this line.
                    if (state.drive.isProven && !deducedDepartureSettled) {
                        deducedDepartureSettled = true
                        runCatching { finalizeDeducedDeparture?.invoke(attributedVehicleId) }
                            .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ finalize deduced departure failed: ${e.message}") }
                    }

                    PaparcarLogger.d(
                        DIAG,
                        "  state hasEverMoved=${state.session.hasEverMoved} hasEverReachedDrivingSpeed=${state.session.driveAuthorized} " +
                                "userConfirmed=${state.confirmation.userConfirmed} " +
                                "vehicleExit=${state.egress.vehicleExitHint} stoppedSince=${state.anchorTrust.stopStartedAt} " +
                                "stoppedDur=${stoppedDuration}ms phase=${state.confirmation.phase}"
                    )

                    // [DET-LOG-04] Raw-fix + AR-signal trace (the replay input stream). The fix
                    // carries speed/accuracy/position + the running stopped duration; the AR EXIT
                    // transition is edge-logged from the state flip fed by onVehicleExit.
                    logDetection { sid -> DetectionEvent.LocationFix(sid, now, location, stoppedDuration) }
                    if (state.egress.vehicleExitHint && !loggedVehicleExit) {
                        loggedVehicleExit = true
                        logDetection { sid -> DetectionEvent.ActivityTransition(sid, now, activity = "IN_VEHICLE", transition = "EXIT", location = location) }
                    } else if (!state.egress.vehicleExitHint) {
                        loggedVehicleExit = false
                    }
                    // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C] The AR EVIDENCE lane, edge-logged
                    // the same way — because it was the only lane that could decide a session
                    // without leaving a mark. The 2026-08-20 trace has 1 476 events and not one of
                    // them names the `ON_BICYCLE` stamp that vetoed it: 66 minutes of silence that
                    // could not be read from the data at all, only reconstructed by elimination.
                    // Logged from the collector rather than the signal methods for the same reason
                    // the EXIT is: those are non-suspend entry points called from a receiver, and
                    // the edge belongs in the fix stream's order.
                    state.egress.bicycleRideAtMs?.let { stampedAt ->
                        if (stampedAt != loggedBicycleRideAtMs) {
                            loggedBicycleRideAtMs = stampedAt
                            logDetection { sid ->
                                DetectionEvent.ActivityTransition(
                                    sid, now, activity = "ON_BICYCLE", transition = "ENTER",
                                    location = location, trueTimeAgeMs = now - stampedAt,
                                )
                            }
                        }
                    }
                    state.egress.vehicleRideAtMs?.let { stampedAt ->
                        if (stampedAt != loggedVehicleRideAtMs) {
                            loggedVehicleRideAtMs = stampedAt
                            // The counterpart: without it the trace shows a veto and no sign of the
                            // boarding that should have superseded it — which is precisely the
                            // comparison the verdict makes.
                            logDetection { sid ->
                                DetectionEvent.ActivityTransition(
                                    sid, now, activity = "IN_VEHICLE", transition = "ENTER",
                                    location = location, trueTimeAgeMs = now - stampedAt,
                                )
                            }
                        }
                    }
                    // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §A/§C] …and the refutation that now
                    // outranks both, once, when it crosses. If the motor band ever refutes a ride
                    // that really was muscle, this is the line that will say so.
                    if (!loggedMotorWitnessed && state.drive.motorBandMs >= config.sustainedDriveProofMs) {
                        loggedMotorWitnessed = true
                        logDetection { sid ->
                            DetectionEvent.Decision(
                                sid, now, outcome = "MOTOR_WITNESSED",
                                pathLabel = "motorBand=${state.drive.motorBandMs}ms ≥${config.motorProofSpeedMps}mps",
                                location = location,
                            )
                        }
                    }

                    // ── THE PRECEDENCE ────────────────────────────────────────────────
                    // Driven from [detectionStageOrder], not written out again. Until now the run
                    // order was still the physical sequence of nine call sites and the declared
                    // order was a list nothing consulted: `StageOrderTest` compared the list to the
                    // enum, and both could agree while the loop did something else. Now permuting
                    // the list permutes execution, which is what makes the four precedence tests of
                    // P0.1 discriminating for the ORDER and not just for the branches.
                    var endsPass = false
                    for (entry in detectionStageOrder) {
                        val pass = if (entry == DetectionStage.NO_MOVEMENT_BUDGET) {
                            // The one stage that does not implement the interface, and says why in
                            // its own KDoc: its decision needs the creep window and the extension
                            // latch, which are per-session bookkeeping the loop maintains on every
                            // fix — including the fixes where the stage is skipped.
                            runNoMovementBudget(location, now)
                        } else {
                            runStage(stageFor(entry), location, now, stoppedDuration)
                        }
                        if (pass.endsSession) endSession()
                        if (pass.endsPass) {
                            endsPass = true
                            break
                        }
                    }
                    if (endsPass) return@collect
                }
        } finally {
            stepJob.cancel()
            phaseJob?.cancel()
            holdWatchdogJob?.cancel()
            // [FIX BUG-SERVICE-109: reset state on session exit so cross-session reads of
            //  hasDetectedMovement and any other state fields return defaults. Without this,
            //  the next session start would briefly observe stale `hasEverReachedDrivingSpeed`.
            //  withContext(NonCancellable) so the reset survives an upstream cancellation.]
            withContext(NonCancellable) {
                // [DET-AUDIT-002 T8/M1] Only the session that still OWNS the singleton state may
                // tear it down. A superseded session (a newer invoke claimed the id at its entry)
                // must not reset() the successor's seeds nor log a SessionEnded under its id.
                if (currentSessionId == thisSessionId) {
                    // [DET-AUDIT-002 T7/M2] Belt to the watchdog's braces: if the stream ENDED
                    // (upstream completion / cancellation) with a confirm still held, finalize it
                    // rather than silently dropping a park the egress proof already earned.
                    val pending = _detectionState.value.confirmation.pendingConfirm
                    if (pending != null && !_detectionState.value.session.completed) {
                        PaparcarLogger.w(DIAG, "  ⚑ session ended with a HELD confirm — finalizing at the pinned location [DET-AUDIT-002 T7]")
                        // [DET-HOLD-BRANCHES-MUST-SPEAK-001] The other pin planted with no fix
                        // behind it. Emitted BEFORE runConfirm so the note survives even if the
                        // save itself is what fails.
                        effects.logHold(
                            HoldAction.SESSION_ENDED,
                            heldMs = nowMs() - pending.confirmedAt,
                            pathLabel = pending.pathLabel,
                            location = pending.location,
                        )
                        if (dispatcher.apply(
                                effects.confirm(
                                    _detectionState.value, pending.location, pending.reliability,
                                    pending.vehicleId, pending.pathLabel,
                                ),
                            )
                        ) {
                            endSession()
                        }
                    }
                    // [DET-HANDOFF-NOT-MANUAL-001 §B.3] This session is over and it never measured a
                    // drive (had it, the one-shot above would have settled the deduction already).
                    // So a departure deduced from this trip is refuted: take the provisional spot
                    // back instead of leaving it standing for the rest of its short TTL. Runs AFTER
                    // the held-confirm finalize on purpose — a park confirmed just now replaces the
                    // pending session, and then there is nothing left to retract.
                    if (!deducedDepartureSettled) {
                        runCatching { retractDeducedDeparture?.invoke() }
                            .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ retract of a refuted deduction failed: ${e.message}") }
                    }
                    // [DET-HONEST-CLOSE-001][DET-FROZEN-COUNTER-001] Everything the session leaves
                    // behind, read BEFORE reset() wipes the state and written in ONE statement —
                    // five `@Volatile` fields with the same deadline were one value all along.
                    val finished = _detectionState.value
                    completedAtExit = finished.session.completed
                    epilogue = SessionEpilogue.of(finished, currentSessionId)
                    // [DET-LOG-03] Close the diagnostics session before wiping state, then clear the id.
                    heldConfirmDroppedByUser?.let { dropped ->
                        effects.logHold(
                            HoldAction.DROPPED_BY_USER,
                            heldMs = nowMs() - dropped.confirmedAt,
                            pathLabel = dropped.pathLabel,
                            location = dropped.location,
                        )
                    }
                    heldConfirmDroppedByUser = null
                    logDetection { sid -> DetectionEvent.SessionEnded(sid, nowMs(), epilogue.outcome.serialized) }
                    currentSessionId = null
                    diagnostics.close()
                    // [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] A late verdict for a fence whose
                    // session is over addresses nobody.
                    currentArmGeofenceId = null
                    reset()
                } else {
                    PaparcarLogger.d(DIAG, "  ⊘ session $thisSessionId superseded — leaving the successor's state untouched [DET-AUDIT-002 T8]")
                    // Stamp the superseded session's terminal outcome under ITS OWN id. The shared-state
                    // guard above skips the usual SessionEnded to protect the successor, which left
                    // superseded sessions with no outcome in the trace (audit 2026-07-15 gap). Logging
                    // under thisSessionId touches no successor state. [VEH-ACTIVE-FENCE-001]
                    detectionEventLogger.log(DetectionEvent.SessionEnded(thisSessionId, nowMs(), outcome = "superseded"))
                }
            }
        }
        PaparcarLogger.d(DIAG, "■ coordinator.invoke() EXITED — locationCount=${_detectionState.value.session.fixCount} completed=$completedAtExit")
    }

    /** Signals that the `IN_VEHICLE → EXIT` transition was received. Thread-safe. */
    fun onVehicleExit(atMs: Long = nowMs()) {
        PaparcarLogger.d(DIAG, "✱ onVehicleExit(at=$atMs) called")
        _detectionState.update {
            it.copy(egress = it.egress.onVehicleExit(atMs))
        }
    }

    /** [DET-BIKE-NOT-A-CAR-001] An AR `ON_BICYCLE` ENTER, stamped with its TRUE transition time
     *  (AR delivers up to ~2 min late). Records evidence; the verdict is
     *  [EvaluateHumanPoweredRideUseCase]'s. Thread-safe. */
    fun onHumanPoweredRide(atMs: Long) {
        PaparcarLogger.d(DIAG, "✱ onHumanPoweredRide(at=$atMs) — cycling observed; automatic saves vetoed [DET-BIKE-NOT-A-CAR-001]")
        _detectionState.update { it.copy(egress = it.egress.onBicycleRide(atMs)) }
    }

    /** [DET-BIKE-NOT-A-CAR-001] An AR `IN_VEHICLE` ENTER, stamped with its TRUE transition time.
     *  Evidence only — arming remains exclusive to the geofence exit, the manual affordance and the
     *  privileged AR decision lane. Its role here is to supersede an earlier cycling stamp.
     *  Thread-safe. */
    fun onVehicleRide(atMs: Long) {
        _detectionState.update { it.copy(egress = it.egress.onVehicleRide(atMs)) }
    }

    /** User tapped "Yes, I parked". Dismisses the notification and marks confirmation. Thread-safe. */
    fun onUserConfirmedParking() {
        PaparcarLogger.d(DIAG, "✱ onUserConfirmedParking() called")
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        _detectionState.update { it.copy(confirmation = it.confirmation.userSaidYes()) }
    }

    /**
     * [DET-STOP-BUTTON-001] User tapped "Stop detection" on the live session (Home row or the
     * service notification). The caller cancels the tracking job right after; this stamps the
     * session's terminal outcome and — the part that makes the button honest — DROPS any held
     * confirm.
     *
     * Without the drop, the `finally` of [invoke] would see a `pendingConfirm` with `completed ==
     * false` and finalize it ("belt to the watchdog's braces", [DET-AUDIT-002 T7]): the button
     * would plant exactly the pin the user just refused. A user stop leaves NO pin and NO prompt —
     * the doctrine's asymmetric failure, requested by the only authority that outranks measured
     * evidence.
     *
     * Thread-safe (single atomic state update, same as the other user signals).
     */
    fun onUserStoppedDetection() {
        PaparcarLogger.d(DIAG, "✱ onUserStoppedDetection() — user stopped the live session; dropping any held confirm [DET-STOP-BUTTON-001]")

        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        // [DET-HOLD-BRANCHES-MUST-SPEAK-001] Remember WHAT the stop dropped, before the state wipe
        // makes it unknowable. Without this the trace shows a session that ended stopped_by_user and
        // no way to tell whether the button cost the user a pin that was one fix from being planted.
        heldConfirmDroppedByUser = _detectionState.value.confirmation.pendingConfirm
        _detectionState.update {
            DetectionSessionState(
                session = it.session.endedWith(SessionOutcome.StoppedByUser).keepingIdentity(),
            )
        }
    }

    /** User dismissed the confirmation ("Keep driving"). Resets all heuristics. Thread-safe. */
    fun onUserDeniedParking() {
        PaparcarLogger.d(DIAG, "✱ onUserDeniedParking() called")
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        _detectionState.update { DetectionSessionState(session = it.session.keepingAuthorization()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun reset() {
        _detectionState.value = DetectionSessionState()
        // [DET-HOLD-BRANCHES-MUST-SPEAK-001] Belt for the superseded path: that epilogue skips the
        // emit (it must not touch the successor's state), so without this the note would survive
        // into the next session and be filed under an id it has nothing to do with.
        heldConfirmDroppedByUser = null
    }

    private val confidenceScoringStage = ConfidenceScoringStage(
        calculateParkingConfidence = calculateParkingConfidence,
        evaluateParkingDecision = evaluateParkingDecision,
    )

    private val holdResolutionStage = HoldResolutionStage()

    private val falseEnterAbortStage = FalseEnterAbortStage()

    private val noMovementBudgetStage = NoMovementBudgetStage()
    private val vehicleAttributionStage = VehicleAttributionStage()

    private val userConfirmStage = UserConfirmStage()

    private val preDriveSkipStage = PreDriveSkipStage()

    private val responseTimeoutStage = ResponseTimeoutStage(evaluateUnattendedParkingSave)

    private val candidateStage = CandidateStage(evaluateParkingDecision)

    private val fastConfirmStage = FastConfirmStage(evaluateParkingDecision)

    /** [DET-DRIVE-PROOF-001] The look-back window AND the ring's retention rule, in one object so
     *  they cannot drift apart — a ring that forgets faster than the window looks back turns a real
     *  drive into one that silently stops proving itself, with no error anywhere. */
    private val driveProofBounds = DriveProofBounds(
        windowMinMs = config.driveProofWindowMinMs,
        windowMaxMs = config.driveProofWindowMaxMs,
        hopMarginMeters = config.credibleDriveHopMarginMeters,
        minDistanceMeters = config.minimumTripDistanceMeters,
        maxRateMps = config.sustainedDepartureMaxRateMps,
        progressFraction = DRIVE_PROOF_PROGRESS_FRACTION,
        retentionSlackMs = DRIVE_PROOF_PRUNE_SLACK_MS,
        maxRetainedFixes = DRIVE_PROOF_MAX_RECENT_FIXES,
    )


    /**
     * [09 §4] Run one stage: log its notes in order, write back what it changed, execute what it
     * asked for, and say whether this fix's pass is over.
     *
     * ## The snapshot, and why it is gone
     *
     * Every stage used to be handed the state as it stood at the TOP of the iteration, and that
     * photograph expired three separate times inside the same fix: P3.1 lost a `pendingConfirm` set
     * microseconds earlier, P3.3 stamped a freshness line at a count that had moved, and P3.6 read a
     * null vehicle id on the very fix that resolved one — which saved a parking spot to nobody. Each
     * was patched where it bit: a narrow write-back here, a live vehicle read there, an effect
     * instead of a `newState` in the third.
     *
     * A stage now reads the state as the stage above it LEFT it. That is the cure the three patches
     * were standing in for, and it is what makes the precedence mean what it says: "the hold
     * outranks the user confirm" is only true if the user-confirm stage can SEE what the hold did.
     *
     * ⚠️ The write-back stays narrow, for a reason that did not go away. The step collector is a
     * genuine second writer and always will be — it is a sensor stream. So a verdict's `newState`,
     * computed from a value read microseconds ago, may never be assigned wholesale: it would clobber
     * a step counted in between. Stages only ever change `confirmation.phase`, which is idempotent
     * against that race; everything defined RELATIVE to a counter goes through [DetectionEffect]
     * and is applied to the live state. See the warning on [StageVerdict.Handled].
     *
     * The two answers are NOT the same and were never the same in the branches either: a fast
     * confirm always ends the pass, but whether it ends the SESSION depends on whether the confirm
     * was held through its grace window. Returning one boolean for both is how the session stops
     * ending — three replays caught exactly that while this stage was being moved.
     */
    private suspend fun runStage(
        stage: SessionStage,
        location: GpsPoint,
        now: Long,
        stoppedDuration: Long,
    ): StagePass {
        val verdict = stage.evaluate(_detectionState.value, location, now, stoppedDuration, config)
        verdict.notes.forEach { PaparcarLogger.d(DIAG, it.text) }
        if (verdict !is StageVerdict.Handled) return StagePass(endsPass = false, endsSession = false)
        val phase = verdict.newState.confirmation.phase
        _detectionState.update { it.copy(confirmation = it.confirmation.copy(phase = phase)) }
        val fromEffects = dispatcher.run(verdict.effects, now)
        return StagePass(
            // An effect may end the pass on its own: the vehicle abort is only discovered AFTER the
            // lookup the stage asked for, so the stage could not have declared it.
            endsPass = verdict.stopsIteration || fromEffects.endsPass,
            endsSession = fromEffects.endsSession,
        )
    }

    /**
     * The stage that owns a place in the precedence — looked up rather than listed again, so the run
     * order is [detectionStageOrder] itself and not a second opinion of it.
     *
     * [DetectionStage.NO_MOVEMENT_BUDGET] is deliberately absent: it is the one stage that does not
     * implement [SessionStage], and its own KDoc says why. The loop names that exception out loud
     * instead of hiding it behind a lookup that would have to throw.
     */
    private fun stageFor(entry: DetectionStage): SessionStage = requireNotNull(sessionStages[entry]) {
        "no stage registered for $entry"
    }

    private val sessionStages: Map<DetectionStage, SessionStage> = listOf(
        holdResolutionStage,
        falseEnterAbortStage,
        vehicleAttributionStage,
        userConfirmStage,
        preDriveSkipStage,
        responseTimeoutStage,
        candidateStage,
        fastConfirmStage,
        confidenceScoringStage,
    ).associateBy { it.stage }


    private companion object {
        const val TAG = "CoordinatorParkingDetector"
        const val DIAG = "PARKDIAG/Coord"

        /** [DET-JAM-WINDOW-001] Fixes worse than this never enter the creep window — a multipath
         *  teleport (acc 100+) at home must not fabricate the recent-creep that buys the extended
         *  no-movement budget. */
        const val JAM_CREEP_MAX_ACCURACY_M = 50f

        /** [DET-DRIVE-PROOF-001] Fraction of the window displacement every LATE-half in-window
         *  fix must already sit from the look-back position — the flat-then-jump mirage keeps
         *  its late fixes AT the origin, a real drive has long left it. Kept permissive so a
         *  mid-window red light never vetoes an honest drive. */
        const val DRIVE_PROOF_PROGRESS_FRACTION = 0.25f

        /** [DET-DRIVE-PROOF-001] Extra retention beyond the max look-back window, so a fix can
         *  still anchor a window that opens a few fixes later. */
        const val DRIVE_PROOF_PRUNE_SLACK_MS = 30_000L

        /** [DET-DRIVE-PROOF-001] Hard cap on the recent-fix ring. */
        const val DRIVE_PROOF_MAX_RECENT_FIXES = 48

        /** Score shown on the confirmation prompt when an auto-confirm is degraded by the
         *  repark-plausibility guard — Medium-band so the copy asks rather than asserts. [DET-SOLID-001] */
        const val IMPLAUSIBLE_REPARK_PROMPT_SCORE = 0.6f

        /** Score for the weak-evidence (ENTER-only) prompt — same Medium-band treatment. [DET-SOLID-001] */
        const val WEAK_EVIDENCE_PROMPT_SCORE = 0.6f

        /** [DET-AUDIT-002 T7] Extra wait past confirmHoldMs before the clock (not a fix) closes a
         *  starved hold — room for the settling fix of a healthy stream to win the race. */
        const val HOLD_WATCHDOG_MARGIN_MS = 30_000L

        /** [DET-CONFIRM-ANCHOR-001] How far from a car witness (the witnessed stop anchor or the
         *  egress birth) a user "Sí" may arrive and still count as "answered near the car".
         *  Sized between the standard near-car radii (geofenceRadiusMeters 80 m,
         *  geofenceRadiusVanMeters 120 m) and under egressBirthFloorMeters (150 m) — the scale
         *  at which an honest near-car fix can still sit on a sparse stream. */
        const val USER_CONFIRM_NEAR_CAR_MAX_METERS = 100.0
    }
}

/**
 * Coarse mapping for the UI: only [ConfirmationPhase.Candidate] — HIGH confidence, the detector is
 * sure the user has stopped and is walking away — surfaces the "Parking…" treatment
 * ([DetectionPhase.Candidate]). Every other phase is a normal in-motion trip → [DetectionPhase.Driving].
 *
 * Crucially [ConfirmationPhase.LowReached]/[ConfirmationPhase.Notified] map to Driving too: they fire on
 * the first Low/Medium confidence sample, i.e. on ANY brief slowdown or stop (a traffic light), which is
 * not yet "parking". Treating them as Candidate made the chip/banner read "Parking…" for most of a normal
 * trip. [DET-PHASE-001]
 */
internal fun ConfirmationPhase.toDetectionPhase(): DetectionPhase =
    if (this is ConfirmationPhase.Candidate) DetectionPhase.Candidate else DetectionPhase.Driving
