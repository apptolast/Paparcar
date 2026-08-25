package io.apptolast.paparcar.domain.coordinator

import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.domain.detection.assertionBlocksRelocation
import io.apptolast.paparcar.domain.detection.HoldAction
import io.apptolast.paparcar.domain.detection.DepartureConfirmationListener
import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.domain.detection.DetectionPhaseSink
import io.apptolast.paparcar.domain.detection.VehicleFenceOwnershipPolicy
import io.apptolast.paparcar.domain.detection.isHumanPoweredRide
import io.apptolast.paparcar.domain.detection.physics.outrunsPedestrianReach
import io.apptolast.paparcar.domain.detection.physics.isCredibleFixAccuracy
import io.apptolast.paparcar.domain.detection.physics.isCredibleMovingFix
import io.apptolast.paparcar.domain.detection.physics.DriveProofBounds
import io.apptolast.paparcar.domain.detection.physics.corroboratesDrive
import io.apptolast.paparcar.domain.detection.physics.isCorroboratedVehicleHop
import io.apptolast.paparcar.domain.detection.physics.pruneRecentFixes
import io.apptolast.paparcar.domain.detection.physics.sustainedDepartureFromAnchor
import io.apptolast.paparcar.domain.detection.physics.honestZoneRadius
import io.apptolast.paparcar.domain.detection.physics.creditSpeedBand
import io.apptolast.paparcar.domain.detection.physics.sustainedDriveWitnessed
import io.apptolast.paparcar.domain.detection.physics.walkableInsideGapMeters
import io.apptolast.paparcar.domain.detection.physics.SessionOutcome
import io.apptolast.paparcar.domain.detection.state.ConfirmationLifecycle
import io.apptolast.paparcar.domain.detection.state.PendingConfirm
import io.apptolast.paparcar.domain.detection.state.SessionTelemetry
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
import io.apptolast.paparcar.domain.usecase.detection.EvaluateShortHopDriveProofUseCase
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
    /** Receives the coarse [DetectionPhase] mapped from the internal confirmation phase, so Home can
     *  show a distinct "candidate / looking for spot" treatment while a trip is being evaluated.
     *  Nullable so existing test doubles need no change. [DET-PHASE-001] */
    private val phaseSink: DetectionPhaseSink? = null,
    /** Wall-clock source (epoch-ms). Injectable so the time-driven post-confirm hold [DET-C-02]
     *  can be unit-tested without sleeping. Defaults to the system clock. */
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** [DET-SHORT-HOP-PROOF-001] The displacement-based drive proof — pure, config-only, so it
     *  defaults from [config] and needs no DI change or test-double churn. */
    private val evaluateShortHopDriveProof: EvaluateShortHopDriveProofUseCase =
        EvaluateShortHopDriveProofUseCase(config),
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
    /**
     * Atomic snapshot of all mutable detection variables for a single session.
     * Updated via [MutableStateFlow.update] to ensure thread-safe transitions.
     *
     * [REFACTOR-200: the four timestamp/flag fields lowFirstReachedAt,
     *  confirmationNotificationShownAt, highConfidenceReachedAt, highCandidateHadVehicleExit
     *  are folded into a single [ConfirmationPhase] field. The legacy combinations
     *  are still encoded — they're just no longer reachable in an invalid form.]
     */
    private data class ParkingDetectionState(
        /** Epoch-ms of the first GPS sample with speed < 1 m/s in the current stop. `null` while moving. */
        val stoppedSince: Long? = null,
        /** GPS fixes collected within [ParkingDetectionConfig.initialStopWindowMs] of the initial stop.
         *  The fix with the lowest [GpsPoint.accuracy] value is used as the saved parking spot. */
        val stoppedFixes: List<GpsPoint> = emptyList(),
        val vehicleExitConfirmed: Boolean = false,
        /** [09 §5] The prompt/confirm conversation: which [ConfirmationPhase] this stop is in, the
         *  confirm held through its grace window, and the user's "Sí". */
        val confirmation: ConfirmationLifecycle = ConfirmationLifecycle(),
        /** [09 §5] Session identity and the drive AUTHORIZATION — origin, first-fix clock, fix
         *  counter, last speed, previous fix, arm evidence, vehicle attribution. Every change goes
         *  through one of its named transitions; nothing here is written field by field. */
        val session: SessionTelemetry = SessionTelemetry(),
        /** Best (lowest accuracy value) GPS fix recorded while the vehicle was stopped. Cleared
         *  when the vehicle drives away. Also serves as the egress anchor: [hasEgressDisplacement]
         *  measures how far the current fix is from it. [code-review #4: a dedicated egressAnchor
         *  pinned on the *first* stopped fix could latch onto a poor-accuracy fix; reusing
         *  bestStopLocation gets the lowest-accuracy fix within the initial-stop window, which is
         *  exactly the parked-car position we want to measure displacement from.] */
        val bestStopLocation: GpsPoint? = null,
        /** [ANCHOR-LOCK-001] `stoppedSince` of the stop during which [bestStopLocation] was
         *  captured. When the anchor is LOCKED (egress steps observed), refinement is allowed
         *  only while still in that same stop — a LATER stop is the pedestrian standing still,
         *  never the car, and must not re-capture the anchor. */
        val anchorCapturedAtStop: Long? = null,
        /** [DET-ANCHOR-FREEZE-001] `true` once a stop matured past
         *  [ParkingDetectionConfig.anchorFreezeStopMs] after measured in-session driving: the car
         *  provably came to rest at [bestStopLocation]. A frozen anchor behaves like a LOCKED one
         *  (no re-capture at later stops, no clear below real driving speed, reposition-burst
         *  vetoed) WITHOUT needing the step stream — the guard for hardware whose step counter
         *  delivers late or never (field 2026-07-11, Redmi: zero steps for the whole walk home;
         *  the unlocked anchor followed the pedestrian and the pin landed at the front door). */
        val anchorFrozen: Boolean = false,
        /** [DET-ANCHOR-FREEZE-001] Moving fixes below a resolved CAR verdict since the last one
         *  WITH it — the "entered this stop on foot" odometer. A stop may only freeze while this
         *  is ≤ [ParkingDetectionConfig.anchorFreezeMaxWalkFixes]: the real park is reached
         *  driving (count 0); the front-door stand is reached after a stretch of walking fixes. */
        val walkFixesSinceDriving: Int = 0,
        /** [DET-KINEMATIC-EGRESS-001] QUALITY pedestrian-band fixes observed while the anchor is
         *  FROZEN — the GPS-measured egress walk. Reaching
         *  [ParkingDetectionConfig.kinematicEgressMinWalkFixes] (with egress displacement) is the
         *  mute-step-counter peer of the step proof. Survives walk pauses (a crossing); only a
         *  resolved CAR movement (which also clears the anchor) resets it. */
        val kinematicEgressFixes: Int = 0,
        /** [DET-ANCHOR-EGRESS-001] The fix at which the FIRST egress evidence (a counted step or
         *  a kinematic walk fix) was observed with the anchor PINNED — where the egress walk was
         *  BORN. A genuine egress is born at the car, so this must sit within the accuracy
         *  envelopes of [bestStopLocation]; an egress born far away proves the anchor belongs to
         *  an intermediate stop (field 2026-07-15: frozen at a traffic light 1.11 km before the
         *  real park, the walk at the destination confirmed kinematic+egress AT the light).
         *  Cleared with the anchor. */
        val egressOriginFix: GpsPoint? = null,
        /** [DET-ANCHOR-EGRESS-001] Steps already counted when [egressOriginFix] was recorded —
         *  they widen the allowed birth distance (the user may have walked a few steps before
         *  the first post-pin fix arrived on a sparse stream). */
        val egressOriginStepCount: Int = 0,
        /** [DET-CREDIBLE-DRIVE-001] Value of [walkFixesSinceDriving] at the moment
         *  [bestStopLocation] was (re)captured — how much WALKING led into the stop the anchor
         *  belongs to. Above [ParkingDetectionConfig.anchorFreezeMaxWalkFixes] the anchor is
         *  WALK-ENTERED: the pedestrian's standing spot, not the car's rest (field 2026-07-15,
         *  Camelias-Oppo: the walk back from a reposition, step counter mute, ended frozen at
         *  the house door 37 m from the car). A walk-entered anchor may keep detecting, but no
         *  auto-confirm may pin it silently — ask instead. */
        val anchorWalkFixesAtCapture: Int = 0,
        /** [DET-CONFIRM-FRESHNESS-001] Step EVENTS delivered since the last resolved CAR movement
         *  — the walk odometer's raw feed, deliberately NOT [stepCount] (whose counting gate
         *  ignores steps while moving with no anchor, which is exactly the walk-in stretch).
         *  Reset together with [walkFixesSinceDriving]. */
        val stepEventsSinceDriving: Int = 0,
        /** [DET-CONFIRM-FRESHNESS-001] [stepEventsSinceDriving] at the moment [bestStopLocation]
         *  was (re)captured. Corroborates the walk-entered taint: a person walking into a stop
         *  fires step events on the way (live counter), a car's final parking maneuver fires none.
         *  Field 2026-07-23, Vista Hermosa: the deceleration into the spot (13.5→2.65→1.49→1.44
         *  m/s, acc 37–56 m) tainted a PERFECT anchor as walk-entered with zero steps — the
         *  silent confirm degraded to a 1 AM prompt and the timeout guard then refused the save. */
        val anchorStepEventsAtCapture: Int = 0,
        /** [DET-CONFIRM-FRESHNESS-001] Whether the step sensor had already proven itself ALIVE
         *  ([sessionSawSteps]) by the time [bestStopLocation] was (re)captured. A taint without
         *  step corroboration is only meaningful when the counter could not have testified —
         *  snapshot at capture so a counter that wakes late cannot retroactively soften a taint
         *  earned while it was silent. */
        val anchorSawStepsAtCapture: Boolean = false,
        /** [DET-WALK-ENTERED-ANCHOR-ZONE-001] First fix of the current walk-band run — the position
         *  at which [walkFixesSinceDriving] left zero. Reset together with that odometer by any
         *  resolved CAR movement, so it always marks where the "entered on foot" stretch began. */
        val walkRunOriginFix: GpsPoint? = null,
        /** [DET-WALK-ENTERED-ANCHOR-ZONE-001] Metres between [walkRunOriginFix] and
         *  [bestStopLocation] at the moment the anchor (re)bound — a MEASURED bound on how far the
         *  walk-in could have dragged the anchor. [anchorStepEventsAtCapture] bounds the same offset
         *  but only speaks when the step counter is alive; this one is a GPS geometry and always
         *  does. The unattended-timeout fallback used to demand the step witness and therefore lost
         *  every park whose device had a mute counter (field 2026-08-16, Redmi: 25,6 min at 96,7
         *  km/h, home reached, zero pins). Cleared with the anchor. */
        val anchorWalkInSpanMeters: Double = 0.0,
        /** [DET-GAP-ANCHOR-001] Size of the GPS hole the CURRENT stop was entered through, in ms,
         *  or `0L` when it was witnessed normally: the fix that opened the stop arrived more than
         *  [ParkingDetectionConfig.anchorGapMaxFixGapMs] after a [previousFix] still at REAL
         *  driving speed. The car was last SEEN moving and its arrival at rest was never witnessed
         *  — the stop's location may be a drive-past point. Recomputed each time a stop opens;
         *  read at anchor (re)bind.
         *  [DET-GAP-ANCHOR-ZONE-001] Holds the MAGNITUDE rather than the fact, because the hole's
         *  duration is what bounds the doubt it creates. */
        val stopEnteredAfterGapMs: Long = 0L,
        /** [DET-GAP-ANCHOR-001] [stopEnteredAfterGapMs] at the moment [bestStopLocation] was
         *  (re)bound to its stop — the GAP-ENTERED taint. Like the walk-entered taint it
         *  invalidates the ANCHOR, not the park: every proof may hold (the user did park and
         *  walk away) but not where this anchor says, so silent confirm degrades to a prompt and
         *  a user "Sí" re-anchors at the user's current stop. Field 2026-07-29, Redmi Av. Sanlúcar:
         *  a 100-s MIUI hole ended in one speed-0 fix mid-route and steps+egress pinned 315 m
         *  before the real park.
         *  [DET-GAP-ANCHOR-ZONE-001] The unattended save no longer nudges unconditionally: the
         *  phone can only have covered the car→anchor offset on foot inside the hole, so this
         *  duration bounds it and the park is kept as an area once the car has come to a sustained
         *  rest. See `EvaluateUnattendedParkingSaveUseCase`. */
        val anchorGapMsAtCapture: Long = 0L,
        /** [DET-CONFIRM-FRESHNESS-001] `true` once ANY step event arrived this session — the
         *  step sensor is ALIVE, so its silence during measured movement is evidence of the CAR
         *  (a mute sensor's silence is noise: Camelias-Oppo). Never reset within the session. */
        val sessionSawSteps: Boolean = false,
        /** [DET-BIKE-NOT-A-CAR-001] True transition time of the last AR `ON_BICYCLE` ENTER seen by
         *  this session, or null. A VETO input only — cycling can never arm or confirm anything,
         *  it can only contradict the kinematics, which a bicycle satisfies as comfortably as a
         *  car (field 2026-08-16, Samsung: 59 min at up to 38 km/h re-pinned the car 4,8 km away
         *  on a beach path). */
        val bicycleRideAtMs: Long? = null,
        /** [DET-BIKE-NOT-A-CAR-001] True transition time of the last AR `IN_VEHICLE` ENTER. Cycling
         *  to the station and then driving is a trip made BY CAR: the later boarding supersedes the
         *  ride, which is why both are timestamps rather than booleans — AR delivers transitions
         *  out of order relative to wall clock and only the true times are comparable. */
        val vehicleRideAtMs: Long? = null,
        /** [DET-CONFIRM-FRESHNESS-001] Moving fixes at ≥ clearBestStopSpeedMps provably outside
         *  the PINNED anchor's accuracy envelopes while a LIVE step counter counted nothing.
         *  Any step event resets it; reaching
         *  [ParkingDetectionConfig.frozenAnchorSteplessDepartureFixes] resolves the movement as
         *  CAR and unfreezes the anchor (the traffic-light / parking-search creep, field
         *  2026-07-23 "Bodegas Osborne"). */
        val pinnedSteplessMovingFixes: Int = 0,
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
         *  Reset to 0 on `isDriving` ONLY. A candidate discard used to zero it too (BUG-COORD-105);
         *  it now moves [stepsAtLastDiscard] instead, because the counter is read by consumers that
         *  need the whole truth (anchor lock, walk-reach ceilings, the unattended verdict) and not
         *  just by the next confirm. [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] */
        val stepCount: Int = 0,
        /** [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] Where [stepCount] stood when a candidate was
         *  last discarded: the freshness line. Steps at or below it happened, and are still
         *  testimony for every other reader, but they have spent their power to CONFIRM — a new
         *  candidate must earn `minStepsToConfirm` above this mark. Cleared with [stepCount] by
         *  measured driving, and by nothing else. */
        val stepsAtLastDiscard: Int = 0,
        // ── SESSION TELEMETRY (BUG-SCOOTER-001) ───────────────────────────────
        /** Session peak of credible driving speed, but ZERO until [driveProven] latches — this
         *  is the statistic every confirm path reads as "did this session measure driving?"
         *  (evaluator `sessionSawDriving`, the unattended save gate, honest-close, the persisted
         *  `tripMaxSpeedMps`). A single Doppler mirage (45 m/s at claimed acc 5 m, phone
         *  indoors) used to set it for the whole session, unlock the kinematic confirm and pin
         *  the living room (field 2026-07-27). [DET-DRIVE-PROOF-001] */
        val maxSpeedMps: Float = 0f,
        /** [DET-DRIVE-PROOF-001] Peak credible-accuracy speed observed so far — the pre-proof
         *  accumulator [maxSpeedMps] promotes from the moment the TRACK proves a drive, so a
         *  proven session reports the same vmax it always did. */
        val pendingMaxSpeedMps: Float = 0f,
        /** [DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001] How many fixes this session has seen at real
         *  driving speed with credible accuracy. [pendingMaxSpeedMps] is a PEAK — one sample — and
         *  a receiver converging out of a cold start emits exactly one (field 2026-08-16 23:52,
         *  Oppo: 42 km/h at acc 11.5 m on the third fix, with the user on foot the whole session).
         *  A count distinguishes that spike from a drive the look-back window merely failed to
         *  corroborate, which is the case [DET-NODRIVE-ZONE-001] was written for. */
        val credibleDrivingFixes: Int = 0,
        /** [DET-DRIVE-PROOF-001] TRUE once the track corroborated a drive (see
         *  [corroboratesDrive]): real ground covered across a bounded look-back window, not a
         *  fix's bare Doppler claim. Latched for the session. */
        val driveProven: Boolean = false,
        /** [DET-DRIVE-PROOF-001] Recent fixes (bounded ring) — the look-back candidates and
         *  in-window witnesses [corroboratesDrive] judges the current fix against. */
        val recentFixes: List<GpsPoint> = emptyList(),
        /** [DET-SHORT-HOP-PROOF-001] Consecutive credible fixes so far sitting unambiguously away
         *  from the pin the car left. A run of them proves a drive the SPEED-based proof cannot
         *  see on a short stop-and-go hop; any fix that fails the geometry resets the run, so a
         *  lone cache teleport never counts. */
        val shortHopQualifyingFixes: Int = 0,
        // ── MOTOR PROOF (DET-MOTOR-PROOF-001) ─────────────────────────────────
        /** Cumulative ms spent in the credible driving band: gaps between SUCCESSIVE in-band
         *  credible fixes (speed ≥ minimumTripSpeedMps, accuracy ≤ minGpsAccuracyForDriving),
         *  credited only when the gap fits inside [ParkingDetectionConfig.driveProofWindowMaxMs] —
         *  the same span the drive-proof shape already trusts to bridge (Calle Gavia's whole
         *  legitimate drive is one 36-s in-band hop; urban accuracy degradation punches holes
         *  through a real drive's band run, field Enamorados). A wider gap proves nothing and
         *  credits NOTHING, so two isolated spikes minutes apart never sum. A lone spike has no
         *  in-band peer at all and credits nothing either. Read through [provenDrivingBandMs] —
         *  like [maxSpeedMps], the statistic is worth nothing until the track proves a drive. */
        val drivingBandMs: Long = 0L,
        /** GPS timestamp (epoch-ms) of the last credible in-band fix — the other endpoint of the
         *  next band gap. GPS time, not wall clock: the trace replayer drives the clock from the
         *  same stamps, so a recorded trace replays identically. */
        val lastBandFixTimestampMs: Long = 0L,
        /** Wall-clock (ms) when the last fix was PROCESSED — the freshness reference a concurrent
         *  step event is judged against (step events carry no GPS timestamp of their own). */
        val lastFixSeenAtMs: Long = 0L,
        /** Whether the last fix's accuracy was credible (≤ minGpsAccuracyForDriving). */
        val lastFixCredible: Boolean = false,
        /** Step events concurrent with a fresh, credible fix ABOVE the pedestrian ceiling
         *  (`egressStepMaxSpeedMps`) — feet moving in rhythm while the position travels faster
         *  than any walk is the PEDALLING signature, the kinematic second source of
         *  `isHumanPoweredRide` (field 2026-08-18 20:32: 16-20 such steps at 3,3-4,1 m/s in a
         *  6-min ride AR never classified). Never reset mid-session: cadence is evidence about
         *  the session's movement, and a car's phantom bursts (1-3 steps) stay under threshold. */
        val fastMotionStepEvents: Int = 0,
        /** Distinct fixes credited with ≥1 cadence step — one fix's burst can be one pothole. */
        val fastMotionStepFixes: Int = 0,
        /** Dedup marker: the [lastFixSeenAtMs] already credited to [fastMotionStepFixes]. */
        val fastMotionCreditedFixAtMs: Long = 0L,
        /** [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] The same clock as [drivingBandMs], one band
         *  higher (`motorProofSpeedMps`): time this session SUSTAINED a speed muscle cannot
         *  produce. Deliberately NOT read through the drive-proof promotion the way
         *  [provenDrivingBandMs] is — its job is to REFUTE a human-powered claim, never to buy a
         *  silent pin, and the asymmetry runs the safe way: doubting the veto costs a prompt,
         *  believing it cost a car (field 2026-08-20, 361 s held above 40 km/h and the session
         *  still died judged a bicycle). */
        val motorBandMs: Long = 0L,
        /** GPS timestamp (epoch-ms) of the last credible in-MOTOR-band fix. */
        val lastMotorBandFixTimestampMs: Long = 0L,
    ) {
        /** [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] Steps that may still CONFIRM: those counted
         *  since the last candidate discard. Every other reader wants [stepCount], the full count —
         *  the difference between "this cannot confirm now" and "this never happened". */
        val freshStepCount: Int get() = (stepCount - stepsAtLastDiscard).coerceAtLeast(0)

        /** Returns the most GPS-accurate fix collected at the moment of stopping, or [fallback]. */
        fun bestFix(fallback: GpsPoint): GpsPoint =
            stoppedFixes.minByOrNull { it.accuracy } ?: fallback

        /** Convenience accessor for the mismatch heuristic — km/h is the human-facing unit. */
        val maxSpeedKmh: Float get() = maxSpeedMps * 3.6f

        /** [DET-MOTOR-PROOF-001] The sustained-drive statistic the evaluator's `sessionSawDriving`
         *  reads, under the same promotion rule as [maxSpeedMps]: ZERO until the track proved a
         *  drive [DET-DRIVE-PROOF-001], so an uncorroborated band run buys nothing. */
        val provenDrivingBandMs: Long get() = if (driveProven) drivingBandMs else 0L

        /** Wall-clock duration since the first GPS fix, in ms; `0` if no fix has arrived yet. */
        fun sessionDurationMs(now: Long): Long = session.ageMs(now)

        /** [DET-GAP-ANCHOR-ZONE-001] Whether the anchor carries the GAP-ENTERED taint — derived
         *  from [anchorGapMsAtCapture] so the fact and its magnitude can never disagree. The
         *  consumers that only need "is it tainted?" (user-confirm re-anchoring, the silent-confirm
         *  degrade to a prompt) read this; the unattended save reads the duration itself. */
        val anchorGapEnteredAtCapture: Boolean get() = anchorGapMsAtCapture > 0L
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
    @Volatile private var sessionOutcome: SessionOutcome = SessionOutcome.Ended

    /** [DET-HONEST-CLOSE-001] Snapshot of the last processed fix, captured in the finally BEFORE
     *  [reset] wipes the state, so it survives for the caller to read after [invoke] returns.
     *  Null between sessions / before the first fix. */
    /** [DET-HOLD-BRANCHES-MUST-SPEAK-001] A held confirm the user's stop dropped. The entrypoint
     *  is not suspend and the coordinator owns no scope, so the note is handed to the epilogue —
     *  which is suspend and runs microseconds later, when the caller cancels the job. */
    @Volatile private var heldConfirmDroppedByUser: PendingConfirm? = null

    @Volatile private var lastFinishedFix: GpsPoint? = null

    /** [DET-HONEST-CLOSE-001] The terminal outcome of the session that just finished — the same
     *  label the [DetectionEvent.SessionEnded] carried. Read by the detection service after
     *  [invoke] returns to decide whether to run the honest-close ladder (on `aborted_false_enter`
     *  / `aborted_no_movement`). Survives across the finally's [reset]. */
    val lastSessionOutcome: String get() = sessionOutcome.serialized

    /** [11 bug #3] The same ending, typed — what the service asks its membership questions of.
     *  [lastSessionOutcome] stays the wire form for telemetry. */
    val lastOutcome: SessionOutcome get() = sessionOutcome

    /** [DET-HONEST-CLOSE-001] Position at the abort moment (last processed fix, or the stop anchor
     *  fallback), or null when no fix was seen. The honest-close ladder's candidate new spot. */
    val lastSessionFix: GpsPoint? get() = lastFinishedFix

    /** [DET-FROZEN-COUNTER-001] Diagnostics id of the session that just finished, so post-session
     *  actors (the honest-close ladder in the service) can log under the same trace. Survives the
     *  finally's [reset]; null before the first session. */
    @Volatile private var lastFinishedSessionId: String? = null
    val lastSessionId: String? get() = lastFinishedSessionId

    /** [DET-FROZEN-COUNTER-001] Steps the finished session's own wakeup step DETECTOR counted —
     *  the cumulative counter's liveness witness for the honest-close ladder. In the two abort
     *  outcomes the ladder runs on, [ParkingDetectionState.stepCount] is never reset (no driving
     *  ever happened), so the terminal value IS the session's full pedestrian testimony. */
    @Volatile private var lastFinishedStepEvents: Int = 0
    val lastSessionStepEvents: Int get() = lastFinishedStepEvents

    /** [DET-FROZEN-COUNTER-001] Max GPS speed (m/s) the finished session measured — measured
     *  movement outranks the step inference in the honest-close ladder. */
    @Volatile private var lastFinishedMaxSpeedMps: Float = 0f
    val lastSessionMaxSpeedMps: Float get() = lastFinishedMaxSpeedMps

    /** Emits a [DetectionEvent] for the current session, or no-ops if no session is active.
     *  The logger contract guarantees this never throws and never blocks on network. */
    private suspend fun logDetection(build: (sessionId: String) -> DetectionEvent) {
        val sid = currentSessionId ?: return
        detectionEventLogger.log(build(sid))
    }

    /**
     * [DET-HOLD-BRANCHES-MUST-SPEAK-001] The single door the post-confirm hold [DET-C-02] speaks
     * through. Every open and every exit goes here and nowhere else: the lane is only worth having
     * if `type=HOLD` is the complete story of a hold, and that stops being true the moment a branch
     * writes its own event.
     *
     * Two events per hold, not one per fix — the hold spans ~2 min of a 2 s stream, so a per-fix
     * note would cost ~50 documents to say the same thing the exit says once.
     */
    private suspend fun logHold(
        action: HoldAction,
        heldMs: Long? = null,
        pathLabel: String? = null,
        location: GpsPoint? = null,
    ) {
        logDetection { sid ->
            DetectionEvent.Hold(sid, nowMs(), action = action, heldMs = heldMs, pathLabel = pathLabel, location = location)
        }
    }

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
     * [ParkingDetectionState.session.driveAuthorized] on the RUNNING session so the confirm
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

    /** [DET-ASSERTION-OUTRANKS-INFERENCE-001] The vehicle's active pin as it stood when this
     *  session armed. Consulted only through [assertionBlocksRelocation]. */
    @Volatile private var currentAssertedPin: UserParking? = null

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
         *  [ParkingDetectionState.session.driveAuthorized] — the arm fired MID-trip (the car
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
         *  verified arms seed [ParkingDetectionState.session.driveAuthorized] and never
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
        // [DET-ASSERTION-OUTRANKS-INFERENCE-001] Snapshot, not a live read: the question is whether
        // THIS session may relocate the pin that existed when it armed.
        currentAssertedPin = activeParkedPin
        // Session provenance stamped on the confirmed park — the repark-plausibility guard in
        // ConfirmParkingUseCase bypasses verified arms and interrogates self-observed ones.
        // Upgraded live by notifyDepartureConfirmed. [DET-SOLID-001]
        _detectionState.update { it.copy(session = it.session.armed(armEvidence.persistLabel)) }

        var completed = false

        // [DET-JAM-WINDOW-001] Whether this session earned the extended no-movement budget by
        // measured recent creep — logged once, and folds under the distinct jam outcome label.
        var jamExtensionLogged = false
        // [DET-JAM-WINDOW-001] Rolling window of credible pre-drive fixes; recent creep = the
        // displacement between its oldest and newest entries. Session-scoped, cleared with invoke.
        val creepWindow = ArrayDeque<Pair<Long, GpsPoint>>()

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
        var loggedPedalCadence = false

        // [DET-LOG-03] Diagnostics session id claimed at entry (T8). Outcome defaults to "ended"
        // and is refined by the abort paths / runConfirm before the finally emits SessionEnded.
        sessionOutcome = SessionOutcome.Ended
        logDetection { sid -> DetectionEvent.SessionStarted(sid, sessionStartMs, strategy = "COORDINATOR", evidence = _detectionState.value.session.armEvidence) }

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
        // [09 §5] Vehicle attribution and the fix counter live in ParkingDetectionState.session.

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
                        _detectionState.value.stepCount == 0 &&
                        (clock() - sessionStartMs) < config.enterArmStepVetoMs &&
                        _detectionState.value.maxSpeedMps < config.minimumTripSpeedMps
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
                        val shouldCount = !s.session.driveAuthorized ||
                            s.stoppedSince != null ||
                            // [DET-STEP-SPEED-GATE-001] Egress-walk steps (anchor set, GPS moving)
                            // count ONLY at pedestrian speed. A car crawling in stop-and-go traffic
                            // keeps the anchor set yet moves at driving speed; without this gate its
                            // vibration accumulated phantom steps that (a) faked steps+egress and
                            // (b) poisoned movementOutrunsSteps into holding the anchor mid-route →
                            // the in-motion false positive at Avenida de los Mástiles (field 2026-07-12).
                            (s.bestStopLocation != null && s.session.lastSpeedMps < config.egressStepMaxSpeedMps)
                        // [DET-MOTOR-PROOF-001] Feet moving in rhythm while a fresh, credible fix
                        // reads above the pedestrian ceiling: nobody WALKS at that speed, and a
                        // car's counter stays silent while rolling — this step is a PEDAL stroke.
                        // Counted on the raw event (independent of shouldCount, whose gates are
                        // egress-walk semantics), judged against the fix snapshot the location
                        // collector maintains.
                        // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] …but only inside the band a
                        // bicycle can actually occupy. The rule had a floor and no CEILING, so a
                        // phantom step next to a 36 m/s motorway fix was counted as a pedal stroke
                        // at 131 km/h. Above `motorProofSpeedMps` the concurrency proves the
                        // opposite of pedalling, and the counter is just reading road vibration.
                        // [DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001] …and only while the trip is
                        // still a TRIP. The band has a ceiling and a floor, but it had no notion of
                        // WHEN, and the same signature means opposite things on either side of the
                        // anchor: once the anchor is pinned — matured stop or egress steps — the
                        // session has already witnessed where the car came to rest, so feet moving
                        // next to a fast fix are the user walking away on a noisy stream, which is
                        // the EXPECTED shape of an egress rather than evidence of pedalling. Field
                        // 2026-08-22 (Redmi, Góndola→Camelias): a 75 km/h car trip with 57 driving
                        // fixes latched the cadence 36 s AFTER the anchor froze, on steps the log
                        // itself labelled `egress walk, anchor set` against 4.27 m/s fixes in a
                        // narrow street; the measured-motor refutation above never reached its
                        // 30 s sustained bar on that starved stream, so nothing else caught it.
                        val cadenceStep = !isAnchorPinned(s) &&
                            s.lastFixCredible &&
                            s.session.lastSpeedMps >= config.egressStepMaxSpeedMps &&
                            s.session.lastSpeedMps < config.motorProofSpeedMps &&
                            s.lastFixSeenAtMs > 0L &&
                            stepAtMs - s.lastFixSeenAtMs <= config.pedalCadenceFixFreshnessMs
                        // [DET-CONFIRM-FRESHNESS-001] Every step event — counted or gated — proves
                        // the sensor is ALIVE, feeds the raw walk odometer, and interrupts any
                        // stepless-departure run: a person is moving their feet, so the pinned
                        // anchor's movement may still be them.
                        val stepped = s.copy(
                            sessionSawSteps = true,
                            pinnedSteplessMovingFixes = 0,
                            stepEventsSinceDriving = s.stepEventsSinceDriving + 1,
                            fastMotionStepEvents = s.fastMotionStepEvents + if (cadenceStep) 1 else 0,
                            fastMotionStepFixes = s.fastMotionStepFixes +
                                if (cadenceStep && s.lastFixSeenAtMs != s.fastMotionCreditedFixAtMs) 1 else 0,
                            fastMotionCreditedFixAtMs =
                                if (cadenceStep) s.lastFixSeenAtMs else s.fastMotionCreditedFixAtMs,
                        )
                        if (shouldCount) stepped.copy(stepCount = stepped.stepCount + 1) else stepped
                    }
                    // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C] Edge-logged the first time the latch
                    // actually HOLDS — both halves at once. The previous edge fired on the step that
                    // made the event count equal the threshold, and its own comment conceded the
                    // miss: a session whose second distinct fix arrives later satisfies the verdict
                    // with no line at all. A veto that can decide a session silently is the defect,
                    // so the marker is a latch, not an equality.
                    if (!loggedPedalCadence &&
                        updated.fastMotionStepEvents >= config.pedalCadenceMinStepEvents &&
                        updated.fastMotionStepFixes >= config.pedalCadenceMinFixes
                    ) {
                        loggedPedalCadence = true
                        PaparcarLogger.d(
                            DIAG,
                            "  ♲ pedal cadence — ${updated.fastMotionStepEvents} steps concurrent with " +
                                "${updated.fastMotionStepFixes} above-ceiling fixes → human-powered ride, " +
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
                                pathLabel = "steps=${updated.fastMotionStepEvents} " +
                                    "fixes=${updated.fastMotionStepFixes} band=" +
                                    "${config.egressStepMaxSpeedMps}-${config.motorProofSpeedMps}mps",
                            )
                        }
                    }
                    if (!updated.session.driveAuthorized) {
                        PaparcarLogger.d(DIAG, "  ✦ step #${updated.stepCount} (pre-drive, false-ENTER candidate)")
                        logDetection { sid -> DetectionEvent.Step(sid, nowMs(), updated.stepCount, stopped = false) }
                    } else if (updated.stoppedSince != null) {
                        PaparcarLogger.d(DIAG, "  ✦ step #${updated.stepCount} (stopped)")
                        logDetection { sid -> DetectionEvent.Step(sid, nowMs(), updated.stepCount, stopped = true) }
                    } else if (updated.bestStopLocation != null) {
                        PaparcarLogger.d(DIAG, "  ✦ step #${updated.stepCount} (egress walk, anchor set) [DET-AR-FIRST-001]")
                        logDetection { sid -> DetectionEvent.Step(sid, nowMs(), updated.stepCount, stopped = false) }
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
                        if (!completed && _detectionState.value.confirmation.pendingConfirm === pending) {
                            PaparcarLogger.w(
                                DIAG,
                                "  ⚑ hold starved of fixes for ${config.confirmHoldMs + HOLD_WATCHDOG_MARGIN_MS}ms — finalizing the held confirm at the pinned location [DET-AUDIT-002 T7]"
                            )
                            // [DET-HOLD-BRANCHES-MUST-SPEAK-001] A pin planted with NO fix to
                            // re-validate it. Deliberate, but a trace has to say so — in forensics
                            // this is what "a spot appeared and I don't know why" looks like.
                            logHold(
                                HoldAction.STARVED,
                                heldMs = config.confirmHoldMs + HOLD_WATCHDOG_MARGIN_MS,
                                pathLabel = pending.pathLabel,
                                location = pending.location,
                            )
                            completed = runConfirm(pending.location, pending.reliability, pending.vehicleId, pending.pathLabel)
                            if (completed) {
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
                    val keep = !completed
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
                    val stoppedDuration = updateStopTracking(location, now)

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
                        // [DET-DRIVE-PROOF-001] The session speed statistic only turns on once
                        // the TRACK proves a drive: real ground covered across a bounded
                        // look-back window, judged by corroboratesDrive. A lone mirage — 45 m/s
                        // at claimed acc 5 m on a phone sitting indoors — set maxSpeed for the
                        // whole session, satisfied `sessionSawDriving`, and the kinematic path
                        // pinned the living room (field 2026-07-27). Arm seeding and session
                        // lifecycle (hasEverReachedDrivingSpeed) are deliberately untouched:
                        // the event nominates, only corroborated movement CONFIRMS.
                        val newPendingMax =
                            if (location.speed > s.pendingMaxSpeedMps && credibleSpeedFix) location.speed
                            else s.pendingMaxSpeedMps
                        // [DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001] …and how many samples back that peak.
                        val newCredibleDrivingFixes = s.credibleDrivingFixes +
                            if (credibleSpeedFix && location.speed >= config.minimumTripSpeedMps) 1 else 0
                        // [DET-SHORT-HOP-PROOF-001] Second, independent proof: measured DISPLACEMENT
                        // from the pin the car left. A short stop-and-go hop never holds a
                        // speed-window the [corroboratesDrive] shape can see (field 2026-08-14
                        // 22:56: 900 m driven, `drive 3/303`, park lost) — but the ground it covered
                        // is real, measured and unwalkable. Anchored to the PIN, never to the
                        // session's own first fix, so the indoor-mirage class stays impossible.
                        val shortHopRun =
                            if (evaluateShortHopDriveProof.qualifies(
                                    departureAnchor = departureAnchor,
                                    fix = location,
                                    fenceRadiusMeters = departureFenceRadiusMeters,
                                    elapsedSinceArmMs = now - sessionStartMs,
                                )
                            ) s.shortHopQualifyingFixes + 1 else 0
                        val shortHopProven = evaluateShortHopDriveProof(
                            departureAnchor = departureAnchor,
                            fix = location,
                            fenceRadiusMeters = departureFenceRadiusMeters,
                            elapsedSinceArmMs = now - sessionStartMs,
                            consecutiveQualifyingFixes = shortHopRun,
                        )
                        val driveProven = s.driveProven || shortHopProven || (credibleSpeedFix &&
                                location.speed >= config.minimumTripSpeedMps &&
                                corroboratesDrive(s.recentFixes, location))
                        if (driveProven && !s.driveProven) {
                            val how = if (shortHopProven) "displacement from the pin [DET-SHORT-HOP-PROOF-001]" else "track [DET-DRIVE-PROOF-001]"
                            PaparcarLogger.d(DIAG, "  ✓ drive PROVEN by $how — session speed statistic unlocked (pendingMax=${newPendingMax}m/s)")
                        }
                        // [DET-MOTOR-PROOF-001] The sustained-drive clock: credit the gap between
                        // SUCCESSIVE credible in-band fixes when it fits inside the span the
                        // drive-proof shape already trusts (a real drive's band run is punched
                        // through by urban accuracy holes — Enamorados — and a skeletal stream's
                        // whole drive can be one 36-s hop — Calle Gavia). A wider gap proves
                        // nothing and credits nothing; a lone spike has no in-band peer at all.
                        val fixInBand = credibleSpeedFix && location.speed >= config.minimumTripSpeedMps
                        val newDrivingBandMs = creditSpeedBand(
                            accumulatedMs = s.drivingBandMs,
                            lastInBandFixMs = s.lastBandFixTimestampMs,
                            fixTimestampMs = location.timestamp,
                            fixInBand = fixInBand,
                            windowMaxMs = config.driveProofWindowMaxMs,
                        )
                        if (newDrivingBandMs >= config.sustainedDriveProofMs && s.drivingBandMs < config.sustainedDriveProofMs) {
                            PaparcarLogger.d(DIAG, "  ✓ sustained drive — ${newDrivingBandMs}ms accumulated in the driving band (≥${config.sustainedDriveProofMs}ms) [DET-MOTOR-PROOF-001]")
                        }
                        // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] The same clock, one band higher:
                        // the part of the driving band muscle cannot reach. Its only job is to
                        // refute a human-powered claim, so it is NOT drive-proof-gated — a session
                        // that holds 40 km/h for half a minute has settled the question by itself.
                        val fixInMotorBand = credibleSpeedFix && location.speed >= config.motorProofSpeedMps
                        val newMotorBandMs = creditSpeedBand(
                            accumulatedMs = s.motorBandMs,
                            lastInBandFixMs = s.lastMotorBandFixTimestampMs,
                            fixTimestampMs = location.timestamp,
                            fixInBand = fixInMotorBand,
                            windowMaxMs = config.driveProofWindowMaxMs,
                        )
                        if (newMotorBandMs >= config.sustainedDriveProofMs && s.motorBandMs < config.sustainedDriveProofMs) {
                            PaparcarLogger.d(DIAG, "  ✓ MOTOR witnessed — ${newMotorBandMs}ms held above ${config.motorProofSpeedMps} m/s; no bicycle claim can stand against this session [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001]")
                        }
                        s.copy(
                            // [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] Origin, first-fix clock,
                            // last speed, the authorization and its "on trust" flag all move
                            // together — see [SessionTelemetry.onFix].
                            session = s.session.onFix(
                                fix = location,
                                nowMs = now,
                                reachedDrivingSpeed = hasJustReachedSpeed,
                                moved = hasJustMoved,
                                driveProven = driveProven,
                            ),
                            // maxSpeed feeds the mismatch guard AND the weak-evidence policy
                            // ("did this session witness driving?") — an indoor Doppler spike,
                            // whatever accuracy it claims, must not count as driving.
                            // [ANCHOR-LOCK-001][DET-DRIVE-PROOF-001]
                            maxSpeedMps = if (driveProven) newPendingMax else 0f,
                            pendingMaxSpeedMps = newPendingMax,
                            credibleDrivingFixes = newCredibleDrivingFixes,
                            driveProven = driveProven,
                            recentFixes = pruneRecentFixes(s.recentFixes, location),
                            shortHopQualifyingFixes = shortHopRun, // [DET-SHORT-HOP-PROOF-001]
                            // [DET-MOTOR-PROOF-001] The sustained-drive clock and the freshness /
                            // credibility snapshot the concurrent-step cadence judge reads.
                            drivingBandMs = newDrivingBandMs,
                            lastBandFixTimestampMs = if (fixInBand) location.timestamp else s.lastBandFixTimestampMs,
                            motorBandMs = newMotorBandMs,
                            lastMotorBandFixTimestampMs =
                                if (fixInMotorBand) location.timestamp else s.lastMotorBandFixTimestampMs,
                            lastFixSeenAtMs = now,
                            lastFixCredible = credibleSpeedFix,
                        )
                    }
                    // [DET-HANDOFF-NOT-MANUAL-001 §B] The car moved, and now it is MEASURED: any
                    // departure that was published on a mere deduction becomes real here — promote
                    // the provisional spot, release the session, drop its geofence. Nothing was
                    // taken from the user until this line.
                    if (state.driveProven && !deducedDepartureSettled) {
                        deducedDepartureSettled = true
                        runCatching { finalizeDeducedDeparture?.invoke(attributedVehicleId) }
                            .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ finalize deduced departure failed: ${e.message}") }
                    }

                    PaparcarLogger.d(
                        DIAG,
                        "  state hasEverMoved=${state.session.hasEverMoved} hasEverReachedDrivingSpeed=${state.session.driveAuthorized} " +
                                "userConfirmed=${state.confirmation.userConfirmed} " +
                                "vehicleExit=${state.vehicleExitConfirmed} stoppedSince=${state.stoppedSince} " +
                                "stoppedDur=${stoppedDuration}ms phase=${state.confirmation.phase}"
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
                    // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C] The AR EVIDENCE lane, edge-logged
                    // the same way — because it was the only lane that could decide a session
                    // without leaving a mark. The 2026-08-20 trace has 1 476 events and not one of
                    // them names the `ON_BICYCLE` stamp that vetoed it: 66 minutes of silence that
                    // could not be read from the data at all, only reconstructed by elimination.
                    // Logged from the collector rather than the signal methods for the same reason
                    // the EXIT is: those are non-suspend entry points called from a receiver, and
                    // the edge belongs in the fix stream's order.
                    state.bicycleRideAtMs?.let { stampedAt ->
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
                    state.vehicleRideAtMs?.let { stampedAt ->
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
                    if (!loggedMotorWitnessed && state.motorBandMs >= config.sustainedDriveProofMs) {
                        loggedMotorWitnessed = true
                        logDetection { sid ->
                            DetectionEvent.Decision(
                                sid, now, outcome = "MOTOR_WITNESSED",
                                pathLabel = "motorBand=${state.motorBandMs}ms ≥${config.motorProofSpeedMps}mps",
                                location = location,
                            )
                        }
                    }

                    // [DET-C-02] Post-confirm hold. A tentative egress-confirm waits here to rule out
                    // an errand stop (park → walk to a kiosk → drive on to park properly): if the car
                    // drives off again before confirmHoldMs elapses, discard it and keep detecting so
                    // the saved park re-anchors at the FINAL spot. An explicit user-yes finalises now.
                    val pending = state.confirmation.pendingConfirm
                    if (pending != null) {
                        val heldMs = now - pending.confirmedAt
                        // [ANCHOR-LOCK-001][DET-ANCHOR-FREEZE-001] With the anchor pinned (egress
                        // steps in hand, or the end-of-drive stop matured) the user is on foot —
                        // only REAL driving speed can mean "errand over, drove off"; brisk
                        // walking must not discard the hold.
                        val resumeSpeedBar = if (isAnchorPinned(state)) {
                            config.minimumTripSpeedMps
                        } else {
                            config.clearBestStopSpeedMps
                        }
                        // [DET-C-02] Strictly greater, deliberately: this discards a pin that has
                        // already earned its confirm, so the boundary is not moved by a pure-move
                        // refactor. Only the accuracy gate is shared with the driving predicates.
                        val drivingResumed = location.speed > resumeSpeedBar &&
                            isCredibleFixAccuracy(location, config.minGpsAccuracyForDriving)
                        when {
                            // [DET-CONFIRM-FRESHNESS-001] Settle-time re-validation: the confirm's
                            // evidence must still be TRUE when the pin is planted. If the current
                            // fix sits farther from the held pin than the counted steps could walk,
                            // a VEHICLE covered that ground during the hold — this was a pick-up /
                            // errand stop whose departure the drove-off discard missed (field
                            // 2026-07-23, Calle Abeto: the only rolling fix carried acc 71 m > the
                            // 50 m trust gate, then 95 s of GPS silence while driving; the hold
                            // settled with the car at ANOTHER traffic light 570 m away and pinned
                            // the pick-up spot). Discard and keep detecting toward the real park —
                            // same exit as the drove-off discard. The user-yes path is exempt: an
                            // explicit answer outranks every guard.
                            !state.confirmation.userConfirmed && heldMs >= config.confirmHoldMs &&
                                heldConfirmOutrunByVehicle(pending, state, location) -> {
                                PaparcarLogger.d(
                                    DIAG,
                                    "  ↩ tentative confirm STALE at settle — position outran the steps from the held pin (errand/pick-up stop), discarding and re-anchoring [DET-CONFIRM-FRESHNESS-001]"
                                )
                                _detectionState.update { it.copy(confirmation = it.confirmation.discardingHold()) }
                                // [DET-HOLD-BRANCHES-MUST-SPEAK-001] Was the lane's only member,
                                // as an ad-hoc `Decision`. It joins the typed lane so that its
                                // mute sibling below becomes comparable to it — one of them
                                // speaking and the other not is exactly what made the two
                                // impossible to tell apart from outside.
                                logHold(
                                    HoldAction.DISCARDED_STALE,
                                    heldMs = heldMs,
                                    pathLabel = pending.pathLabel,
                                    location = location,
                                )
                                // Fall through and keep detecting toward the real park.
                            }
                            state.confirmation.userConfirmed || heldMs >= config.confirmHoldMs -> {
                                PaparcarLogger.d(
                                    DIAG,
                                    "  ✓ hold settled (held=${heldMs}ms, userYes=${state.confirmation.userConfirmed}) — finalizing tentative confirm [DET-C-02]"
                                )
                                logHold(
                                    HoldAction.SETTLED,
                                    heldMs = heldMs,
                                    pathLabel = if (state.confirmation.userConfirmed) "user" else pending.pathLabel,
                                    location = pending.location,
                                )
                                // A user "Sí" during the hold is the USER-CONFIRMED path (1.0,
                                // every guard bypassed), not the auto path that opened the hold —
                                // the class KDoc promises it and the repark guard must not veto a
                                // park the user explicitly confirmed. Position stays the pinned
                                // hold location either way.
                                completed = if (state.confirmation.userConfirmed) {
                                    runConfirm(pending.location, config.reliabilityUserConfirmed, pending.vehicleId, "user")
                                } else {
                                    runConfirm(pending.location, pending.reliability, pending.vehicleId, pending.pathLabel)
                                }
                                return@collect
                            }
                            drivingResumed -> {
                                PaparcarLogger.d(
                                    DIAG,
                                    "  ↩ tentative confirm DISCARDED — drove off ${heldMs}ms into the hold (errand), re-anchoring [DET-C-02]"
                                )
                                _detectionState.update { it.copy(confirmation = it.confirmation.discardingHold()) }
                                logHold(
                                    HoldAction.DISCARDED_DROVE_OFF,
                                    heldMs = heldMs,
                                    pathLabel = pending.pathLabel,
                                    location = location,
                                )
                                // Fall through and keep detecting toward the real park. On a real
                                // driving fix updateStopTracking already cleared anchor + steps; on
                                // an ambiguous walking-band fix (unpinned anchor) it may have KEPT
                                // them — harmless: the next fix re-confirms from the same anchor
                                // and re-enters the hold (delayed finalize, never a lost park).
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
                    if (!state.session.driveAuthorized && state.stepCount >= config.falseEnterAbortSteps) {
                        PaparcarLogger.d(
                            DIAG,
                            "  ⊘ false-ENTER abort — ${state.stepCount} steps before driving speed " +
                                "[BUG-FALSE-ENTER-WALKING]"
                        )
                        sessionOutcome = SessionOutcome.AbortedFalseEnter
                        completed = true
                        return@collect
                    }

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
                    if (!state.session.driveAuthorized) {
                        if (location.accuracy <= JAM_CREEP_MAX_ACCURACY_M) creepWindow.addLast(now to location)
                        while (creepWindow.isNotEmpty() && now - creepWindow.first().first > config.jamCreepWindowMs) {
                            creepWindow.removeFirst()
                        }
                    }
                    val noMovementBudgetMs =
                        if (staleExitDelivery) config.staleExitNoMovementMs else config.maxNoMovementMs
                    if (!state.session.driveAuthorized && (now - sessionStartMs) > noMovementBudgetMs) {
                        val recentCreepMeters = if (creepWindow.size >= 2) {
                            val oldest = creepWindow.first().second
                            val newest = creepWindow.last().second
                            io.apptolast.paparcar.domain.util.haversineMeters(
                                oldest.latitude, oldest.longitude, newest.latitude, newest.longitude,
                            )
                        } else 0.0
                        val jamCrawl = !staleExitDelivery && recentCreepMeters >= config.jamCreepMinMeters
                        if (jamCrawl && (now - sessionStartMs) <= config.jamExtendedNoMovementMs) {
                            if (!jamExtensionLogged) {
                                jamExtensionLogged = true
                                PaparcarLogger.d(
                                    DIAG,
                                    "  ⏲ no-movement budget EXTENDED — recent creep ${recentCreepMeters.toInt()}m " +
                                        "in ${config.jamCreepWindowMs}ms without driving speed (jam/stop-go " +
                                        "crawl) → watching until ${config.jamExtendedNoMovementMs}ms [DET-JAM-WINDOW-001]",
                                )
                            }
                        } else {
                            PaparcarLogger.d(
                                DIAG,
                                "  ⚑ no-movement guard hit after ${now - sessionStartMs}ms " +
                                    "(budget=${noMovementBudgetMs}ms staleExitDelivery=$staleExitDelivery " +
                                    "recentCreep=${recentCreepMeters.toInt()}m jamExtended=$jamExtensionLogged) → completed=true",
                            )
                            // Distinct outcome + telemetry when the extension ran: field data sizes
                            // this cohort (jam that never cleared? crawl into a re-park?) before
                            // deciding whether it deserves a nudge. [DET-JAM-WINDOW-001]
                            sessionOutcome =
                                if (jamExtensionLogged) SessionOutcome.AbortedNoMovementJam else SessionOutcome.AbortedNoMovement
                            if (jamExtensionLogged) {
                                logDetection { sid ->
                                    DetectionEvent.Decision(
                                        sid, now,
                                        outcome = "NO_MOVEMENT_JAM_FOLD",
                                        pathLabel = "recentCreep=${recentCreepMeters.toInt()}m rawMax=${state.pendingMaxSpeedMps}mps",
                                        location = location,
                                    )
                                }
                            }
                            completed = true
                            return@collect
                        }
                    }

                    // Lock vehicleId on first driving-speed fix. [BUG-NEW-VEHICLE-DEFAULT] [BUG-SHORT-TRIP]
                    if (state.session.driveAuthorized && _detectionState.value.session.attributedVehicleId == null) {
                        val active = vehicleRepository.observeActiveVehicle().first()
                        // Attribute to the NOMINATING fence's vehicle (the geofence exit that armed
                        // this trip identifies the car), else the current active vehicle. Stops the
                        // pin landing on whatever ranked active. [VEH-ACTIVE-FENCE-001]
                        // A BT-paired nominator is vetoed by the policy — that identity belongs to
                        // the Bluetooth strategy alone; its fence only proves the phone left.
                        // [DET-BT-OWNERSHIP-001]
                        val nominatorVehicle = when {
                            nominatingVehicleId == null -> null
                            nominatingVehicleId == active?.id -> active
                            else -> vehicleRepository.observeVehicles().first()
                                .firstOrNull { it.id == nominatingVehicleId }
                        }
                        val nominatorIsBtPaired = nominatorVehicle?.bluetoothDeviceId != null
                        val resolvedId = VehicleFenceOwnershipPolicy.resolveSessionVehicleId(
                            nominatingVehicleId = nominatingVehicleId,
                            nominatingVehicleIsBtPaired = nominatorIsBtPaired,
                            activeVehicleId = active?.id,
                        )
                        val nominatorVetoed = nominatingVehicleId != null && resolvedId != nominatingVehicleId
                        if (resolvedId == null) {
                            PaparcarLogger.w(
                                DIAG,
                                "  ✗ hasEverReachedDrivingSpeed but no vehicle to attribute — abort session" +
                                    if (nominatorVetoed) " (nominator=$nominatingVehicleId vetoed: bt-owned, no active vehicle)" else "",
                            )
                            sessionOutcome = SessionOutcome.AbortedNoVehicle
                            completed = true
                            return@collect
                        }
                        // Vehicle type: the resolved vehicle's. Cheap when it IS the active one; a
                        // differing nominator was already looked up in the user's vehicle list.
                        val resolvedType = if (resolvedId == active?.id) {
                            active.vehicleType
                        } else {
                            nominatorVehicle?.vehicleType
                        }
                        _detectionState.update { it.copy(session = it.session.attributeVehicle(resolvedId, resolvedType)) }
                        PaparcarLogger.d(
                            DIAG,
                            "  ✓ vehicleId locked: $resolvedId type=$resolvedType (nominator=$nominatingVehicleId" +
                                (if (nominatorVetoed) " vetoed: bt-owned" else "") + ")",
                        )
                    }

                    // [BUG-COORD-115] precedence: user-confirm always wins.
                    if (state.confirmation.userConfirmed) {
                        PaparcarLogger.d(DIAG, "  ▶ USER-CONFIRMED path — entering confirmParking")
                        // [DET-ANCHOR-EGRESS-001][DET-GAP-ANCHOR-001] A user "Sí" answers "did you
                        // park?", not "is the anchor right": when the egress was born away from
                        // the pinned anchor, or the anchor's stop opened through a GPS hole (rest
                        // unwitnessed — possibly a drive-past point), the anchor is not the car —
                        // anchor the save at the user's current stop instead (they answer near
                        // the car; the wrong anchor may sit hundreds of meters out).
                        val locationToConfirm = if (isEgressBornAtAnchor(state) && !state.anchorGapEnteredAtCapture) {
                            state.bestStopLocation ?: state.bestFix(location)
                        } else {
                            // [DET-CONFIRM-ANCHOR-001] "They answer near the car" is an assumption,
                            // not a fact: a late "Sí" arrives from wherever the walk ended, and the
                            // current fix then IS the pedestrian's destination (field 2026-08-11
                            // 16:08: 32 driving fixes came to rest, mute step counter, the user
                            // answered after walking away and the pin planted at the destination).
                            // When the stop was WITNESSED (anchor present and NOT gap-entered — a
                            // gap-born anchor may be a drive-past point hundreds of meters out with
                            // unboundable forward error, so it never wins here) and the answer
                            // arrives far from BOTH the anchor and the egress birth, re-anchor at
                            // the witnessed end of driving. Answering near the egress BIRTH keeps
                            // today's behavior on purpose: a born-away egress means the birth, not
                            // the anchor, is where the car is (field 2026-07-15, Enamorados: frozen
                            // at a light 1.11 km back — the user's own stop is the right pin there).
                            val witnessedStop = state.bestStopLocation
                                ?.takeIf { !state.anchorGapEnteredAtCapture }
                            val currentFix = state.bestFix(location)
                            val stopDistanceMeters = witnessedStop?.let {
                                io.apptolast.paparcar.domain.util.haversineMeters(
                                    it.latitude, it.longitude, currentFix.latitude, currentFix.longitude,
                                )
                            }
                            val birthDistanceMeters = state.egressOriginFix?.let {
                                io.apptolast.paparcar.domain.util.haversineMeters(
                                    it.latitude, it.longitude, currentFix.latitude, currentFix.longitude,
                                )
                            }
                            val answeredFarFromCar = stopDistanceMeters != null &&
                                stopDistanceMeters > USER_CONFIRM_NEAR_CAR_MAX_METERS &&
                                (birthDistanceMeters == null || birthDistanceMeters > USER_CONFIRM_NEAR_CAR_MAX_METERS)
                            PaparcarLogger.d(
                                DIAG,
                                "  ⚓ user-confirm anchor: stopDistance=${stopDistanceMeters?.toInt()}m " +
                                    "birthDistance=${birthDistanceMeters?.toInt()}m " +
                                    "gapEntered=${state.anchorGapEnteredAtCapture} " +
                                    "→ ${if (answeredFarFromCar) "witnessed stop" else "current fix"} [DET-CONFIRM-ANCHOR-001]",
                            )
                            if (answeredFarFromCar) witnessedStop!! else currentFix
                        }
                        // [DET-USER-YES-IS-NOT-A-COORDINATE-001] The answer settles WHETHER the user
                        // parked — `reliabilityUserConfirmed` (1.0) is right and stays. It settles
                        // nothing about WHERE, and this path had one branch that pins an exact point
                        // immediately after concluding it does not know: a gap-born anchor is
                        // discarded here as "possibly a drive-past point hundreds of meters out",
                        // and the fallback fix was then saved as an exact coordinate with the doubt
                        // recorded nowhere. The unattended path already bounds that same doubt by
                        // what a person could walk inside the hole [DET-GAP-ANCHOR-ZONE-001]; the
                        // user path inherits the bound rather than a second opinion of it. The doubt
                        // hangs on the HOLE, not on which fallback the branch above happened to
                        // pick: when the stop was entered through one, every candidate position in
                        // this path is downstream of the same unwitnessed arrival.
                        //
                        // Below the zone FLOOR an area says less than the point does, so the point
                        // stands — this only stops the exact claim where it was already known to be
                        // unsupportable, and leaves every well-located pin exactly as it was.
                        val userDoubtMeters = walkableInsideGapMeters(
                            state.anchorGapMsAtCapture, config.maxPedestrianSpeedMps,
                        )
                        val userZoneRadius = approximateZoneRadius(locationToConfirm, userDoubtMeters)
                            .takeIf {
                                maxOf(locationToConfirm.accuracy, userDoubtMeters.toFloat()) >
                                    config.honestCloseMinZoneRadiusMeters
                            }
                        if (userZoneRadius != null) {
                            PaparcarLogger.d(
                                DIAG,
                                "  ◯ user-confirm saved as a ZONE r=${userZoneRadius}m — the answer proves the " +
                                    "park, not the spot (doubt=${userDoubtMeters.toInt()}m from a " +
                                    "${state.anchorGapMsAtCapture}ms GPS hole, fixAcc=${locationToConfirm.accuracy}m) " +
                                    "[DET-USER-YES-IS-NOT-A-COORDINATE-001]",
                            )
                        }
                        completed = runConfirm(
                            location = locationToConfirm,
                            reliability = config.reliabilityUserConfirmed,
                            vehicleId = attributedVehicleId,
                            pathLabel = "user",
                            zoneRadiusMeters = userZoneRadius,
                        )
                        PaparcarLogger.d(DIAG, "  ◀ USER-CONFIRMED path done — returning from collect")
                        return@collect
                    }

                    if (!state.session.driveAuthorized) {
                        PaparcarLogger.d(DIAG, "  ⏸ skipping: !hasEverReachedDrivingSpeed")
                        return@collect
                    }

                    // Response-timeout: SAVE, don't discard. [DET-RECONCILE-001] The prompt only
                    // shows after a real trip + stop + vehicle-exit signal — the parking almost
                    // certainly happened; the only missing bit is a human tap. Throwing the
                    // session away costs the user their car (field incident 2026-07-06, Redmi:
                    // a real parking was lost to an unnoticed notification), while saving it
                    // wrong costs one correction tap. Saved with low reliability so nothing
                    // community-facing trusts it on its own. Session-end still runs (completed).
                    val promptShownAt = state.confirmation.phase.promptShownAt
                    if (promptShownAt != null && (now - promptShownAt) > config.confirmationResponseTimeoutMs) {
                        // [DET-WALK-ENTERED-ANCHOR-ZONE-001] The seven-way precedence that used to
                        // live inline here (no-drive → unpinned → egress-mismatch → gap → walk-
                        // entered → vehicular-egress → exact save) is now ONE pure verdict, so the
                        // rule "a bounded doubt costs precision, never the park" exists in a single
                        // place instead of being re-derived per branch — which is how the Redmi's
                        // fully measured 25,6 min drive ended with no pin at all (field 2026-08-16,
                        // session 1786918991116). This block keeps only the side effects.
                        // [DET-CAR-REST-CLOCK-001] The sustained rest the save licence needs is the
                        // CAR's, and its witness is the pinned anchor's own stop: it was opened by
                        // the car halting and only re-measured real driving clears it. The phone's
                        // stop tracker is the wrong clock here — after egress it follows the
                        // walker, and indoor GPS noise resets it with no accuracy gate (field
                        // 2026-08-18 Góndola: ~15 s accumulated across 15 min of anchored rest).
                        val anchorRestMs = if (isAnchorPinned(state)) {
                            state.anchorCapturedAtStop?.let { now - it } ?: 0L
                        } else {
                            0L
                        }
                        val verdict = evaluateUnattendedParkingSave(
                            UnattendedSaveInput(
                                maxSpeedMps = state.maxSpeedMps,
                                pendingMaxSpeedMps = state.pendingMaxSpeedMps,
                                credibleDrivingFixes = state.credibleDrivingFixes,
                                anchor = state.bestStopLocation,
                                currentFix = location,
                                egressOriginFix = state.egressOriginFix,
                                stepCount = state.stepCount,
                                sessionSawSteps = state.sessionSawSteps,
                                vehicleExitConfirmed = state.vehicleExitConfirmed,
                                anchorPinned = isAnchorPinned(state),
                                anchorGapMs = state.anchorGapMsAtCapture,
                                anchorWalkEntered = isAnchorWalkEntered(state),
                                anchorStepEventsAtCapture = state.anchorStepEventsAtCapture,
                                anchorWalkInSpanMeters = state.anchorWalkInSpanMeters,
                                egressBornAtAnchor = isEgressBornAtAnchor(state),
                                egressExceedsWalkReach = egressExceedsWalkReach(state, location),
                                anchorRestMs = anchorRestMs,
                                humanPoweredRide = humanPoweredRide(state, attributedVehicleType, now),
                            )
                        )
                        PaparcarLogger.d(
                            DIAG,
                            "  ⑊ no user response after ${now - promptShownAt}ms " +
                                "(limit=${config.confirmationResponseTimeoutMs}ms) → $verdict " +
                                "[maxSpeed=${state.maxSpeedMps}m/s pinned=${isAnchorPinned(state)} " +
                                "walkEntered=${isAnchorWalkEntered(state)} walkFixes=${state.anchorWalkFixesAtCapture} " +
                                "stepEvents=${state.anchorStepEventsAtCapture} sawSteps=${state.anchorSawStepsAtCapture} " +
                                "walkInSpan=${state.anchorWalkInSpanMeters.toInt()}m carRest=${anchorRestMs}ms " +
                                "stopped=${stoppedDuration}ms " +
                                "gapMs=${state.anchorGapMsAtCapture}] " +
                                "[DET-WALK-ENTERED-ANCHOR-ZONE-001][DET-GAP-ANCHOR-ZONE-001]"
                        )
                        when (verdict) {
                            is UnattendedParkingSave.SaveZone -> {
                                if (saveUnattendedZone(
                                        reason = verdict.reason,
                                        center = verdict.center,
                                        doubtMeters = verdict.doubtMeters,
                                        vehicleId = attributedVehicleId,
                                        location = location,
                                        now = now,
                                    )
                                ) {
                                    completed = true
                                    return@collect
                                }
                                // The zone save degraded to yet another prompt or failed outright.
                                // Fall back to the ask its own reason names, so the user still gets
                                // the offer instead of silence.
                                nudgeUnattended(verdict.reason, attributedVehicleId, location, now)
                                completed = true
                                return@collect
                            }
                            is UnattendedParkingSave.Ask -> {
                                nudgeUnattended(verdict.reason, attributedVehicleId, location, now, verdict.distanceMeters)
                                completed = true
                                return@collect
                            }
                            UnattendedParkingSave.SaveExact -> {
                                // [DET-RECONCILE-001] The prompt only shows after a real trip + stop
                                // + vehicle-exit signal, and every anchor taint came back clean:
                                // save at the pinned anchor with low reliability so nothing
                                // community-facing trusts it on its own.
                                notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
                                val locationToConfirm = refinedParkLocation(state, location)
                                val saved = runConfirm(
                                    location = locationToConfirm,
                                    reliability = config.reliabilityUnattendedSave,
                                    vehicleId = attributedVehicleId,
                                    pathLabel = "unattended_timeout",
                                )
                                if (!saved) {
                                    // Guard degraded the save to yet another prompt — but the user
                                    // already ignored one for the full window; ending here (old
                                    // abort) is the only non-looping exit. Dismiss the re-posted
                                    // prompt so nothing dangles. [BUG-STUCK-SESSION]
                                    notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
                                    sessionOutcome = SessionOutcome.AbortedResponseTimeout
                                }
                                completed = true
                                return@collect
                            }
                        }
                    }

                    // Candidate-phase decision tree.
                    val candidate = state.confirmation.phase as? ConfirmationPhase.Candidate
                    if (candidate != null) {
                        val didConfirm = evaluateCandidatePhase(
                            phase = candidate,
                            location = location,
                            state = state,
                            now = now,
                            activeVehicleId = attributedVehicleId,
                            activeVehicleType = attributedVehicleType,
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
                    // [DET-KINEMATIC-EGRESS-001] The kinematic egress signal is the mute-counter
                    // peer: the FROZEN anchor has watched a sustained quality walk away from it —
                    // the same evidence, measured by GPS instead of the step sensor.
                    // [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] FRESH steps: a discarded
                    // candidate's steps stay on the record but may not re-arm this lane.
                    if (state.freshStepCount >= config.minStepsToConfirm || hasKinematicEgressSignal(state)) {
                        // elapsedSinceHighMs=0 → no observation window; the egress proofs are what
                        // confirm. The scooter mismatch guard still applies via the use case.
                        val decision = evaluateParkingDecision(
                            parkingDecisionInput(
                                state = state,
                                location = location,
                                now = now,
                                activeVehicleType = attributedVehicleType,
                                elapsedSinceHighMs = 0L,
                                hadVehicleExit = state.vehicleExitConfirmed,
                                // No stop has matured here — this lane runs on the egress proofs
                                // alone, so it may not reach the terminal human-powered close: a
                                // cyclist paused at a light must not be closed mid-ride.
                                // [DET-HUMAN-POWERED-EARLY-CLOSE-001]
                                restCertified = false,
                            )
                        )
                        if (decision is ParkingDecision.Confirmed) {
                            PaparcarLogger.d(
                                DIAG,
                                "  ▶ ${decision.pathLabel} (steps=${state.stepCount} kinematicFixes=${state.kinematicEgressFixes}) → fast confirm, skipping slow path [DET-D-03][DET-KINEMATIC-EGRESS-001]"
                            )
                            val locationToConfirm = refinedParkLocation(state, location)
                            completed = beginConfirm(
                                location = locationToConfirm,
                                reliability = decision.reliability,
                                vehicleId = attributedVehicleId,
                                pathLabel = decision.pathLabel,
                                now = now,
                            )
                            return@collect
                        }
                        if (decision is ParkingDecision.Prompt) {
                            degradeToPrompt(decision.pathLabel, decision.reason, location, now)
                            return@collect
                        }
                        PaparcarLogger.d(
                            DIAG,
                            "  ⊘ steps+egress fast confirm gated ($decision) — anchorSet=${state.bestStopLocation != null}, falling to scoring"
                        )
                    }

                    // [DET-HUMAN-POWERED-EARLY-CLOSE-001] The scorer can now END the session: High
                    // confidence certifies the sustained stop, and on a muscle-powered ride that is
                    // the entire verdict — no candidate, no prompt, no 15-minute wait.
                    if (evaluateConfidence(location, stoppedDuration, state, now, attributedVehicleId, attributedVehicleType)) {
                        completed = true
                        return@collect
                    }
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
                    if (pending != null && !completed) {
                        PaparcarLogger.w(DIAG, "  ⚑ session ended with a HELD confirm — finalizing at the pinned location [DET-AUDIT-002 T7]")
                        // [DET-HOLD-BRANCHES-MUST-SPEAK-001] The other pin planted with no fix
                        // behind it. Emitted BEFORE runConfirm so the note survives even if the
                        // save itself is what fails.
                        logHold(
                            HoldAction.SESSION_ENDED,
                            heldMs = nowMs() - pending.confirmedAt,
                            pathLabel = pending.pathLabel,
                            location = pending.location,
                        )
                        completed = runConfirm(pending.location, pending.reliability, pending.vehicleId, pending.pathLabel)
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
                    // [DET-HONEST-CLOSE-001] Snapshot the terminal fix BEFORE reset() wipes the
                    // state — the caller (the detection service) reads it after invoke returns to
                    // run the honest-close ladder on the two silent aborts. previousFix is the last
                    // fix processed; bestStopLocation is the stop anchor fallback.
                    lastFinishedFix = _detectionState.value.session.previousFix ?: _detectionState.value.bestStopLocation
                    // [DET-FROZEN-COUNTER-001] Snapshot the session's own evidence for the ladder:
                    // detector steps witness counter liveness, measured speed outranks inference.
                    lastFinishedSessionId = currentSessionId
                    lastFinishedStepEvents = _detectionState.value.stepCount
                    lastFinishedMaxSpeedMps = _detectionState.value.maxSpeedMps
                    // [DET-LOG-03] Close the diagnostics session before wiping state, then clear the id.
                    heldConfirmDroppedByUser?.let { dropped ->
                        logHold(
                            HoldAction.DROPPED_BY_USER,
                            heldMs = nowMs() - dropped.confirmedAt,
                            pathLabel = dropped.pathLabel,
                            location = dropped.location,
                        )
                    }
                    heldConfirmDroppedByUser = null
                    logDetection { sid -> DetectionEvent.SessionEnded(sid, nowMs(), sessionOutcome.serialized) }
                    currentSessionId = null
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
        PaparcarLogger.d(DIAG, "■ coordinator.invoke() EXITED — locationCount=${_detectionState.value.session.fixCount} completed=$completed")
    }

    /** [DET-BIKE-NOT-A-CAR-001] Whether this session's movement was human-powered — the profile
     *  answer OR the measured one. Thin wrapper so both decision sites and the unattended timeout
     *  ask the same pure evaluator with the same inputs. */
    private fun humanPoweredRide(
        s: ParkingDetectionState,
        vehicleType: VehicleType?,
        now: Long,
    ): Boolean = isHumanPoweredRide(
        vehicleType = vehicleType,
        bicycleRideAtMs = s.bicycleRideAtMs,
        vehicleRideAtMs = s.vehicleRideAtMs,
        nowMs = now,
        // [DET-MOTOR-PROOF-001] The kinematic source — pedal cadence measured by this session's
        // own stream, for the short rides AR never classifies.
        fastMotionStepEvents = s.fastMotionStepEvents,
        fastMotionStepFixes = s.fastMotionStepFixes,
        // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] …and the measurement that outranks both sources.
        sustainedMotorBandMs = s.motorBandMs,
        config = config,
    )

    /** Signals that the `IN_VEHICLE → EXIT` transition was received. Thread-safe. */
    fun onVehicleExit(atMs: Long = nowMs()) {
        PaparcarLogger.d(DIAG, "✱ onVehicleExit(at=$atMs) called")
        _detectionState.update {
            it.copy(
                vehicleExitConfirmed = true,
                // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] An EXIT is also evidence of the BOARDING
                // it must have followed, so it supersedes an earlier cycling stamp exactly like an
                // ENTER does. Only forward: AR delivers transitions out of order, and an EXIT
                // stamped older than a boarding we already know about would age the evidence.
                vehicleRideAtMs = maxOf(it.vehicleRideAtMs ?: Long.MIN_VALUE, atMs),
            )
        }
    }

    /** [DET-BIKE-NOT-A-CAR-001] An AR `ON_BICYCLE` ENTER, stamped with its TRUE transition time
     *  (AR delivers up to ~2 min late). Records evidence; the verdict is
     *  [EvaluateHumanPoweredRideUseCase]'s. Thread-safe. */
    fun onHumanPoweredRide(atMs: Long) {
        PaparcarLogger.d(DIAG, "✱ onHumanPoweredRide(at=$atMs) — cycling observed; automatic saves vetoed [DET-BIKE-NOT-A-CAR-001]")
        _detectionState.update { it.copy(bicycleRideAtMs = atMs) }
    }

    /** [DET-BIKE-NOT-A-CAR-001] An AR `IN_VEHICLE` ENTER, stamped with its TRUE transition time.
     *  Evidence only — arming remains exclusive to the geofence exit, the manual affordance and the
     *  privileged AR decision lane. Its role here is to supersede an earlier cycling stamp.
     *  Thread-safe. */
    fun onVehicleRide(atMs: Long) {
        _detectionState.update { it.copy(vehicleRideAtMs = atMs) }
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
        sessionOutcome = SessionOutcome.StoppedByUser
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        // [DET-HOLD-BRANCHES-MUST-SPEAK-001] Remember WHAT the stop dropped, before the state wipe
        // makes it unknowable. Without this the trace shows a session that ended stopped_by_user and
        // no way to tell whether the button cost the user a pin that was one fix from being planted.
        heldConfirmDroppedByUser = _detectionState.value.confirmation.pendingConfirm
        _detectionState.update { ParkingDetectionState(session = it.session.keepingIdentity()) }
    }

    /** User dismissed the confirmation ("Keep driving"). Resets all heuristics. Thread-safe. */
    fun onUserDeniedParking() {
        PaparcarLogger.d(DIAG, "✱ onUserDeniedParking() called")
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        _detectionState.update { ParkingDetectionState(session = it.session.keepingAuthorization()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun reset() {
        _detectionState.value = ParkingDetectionState()
        // [DET-HOLD-BRANCHES-MUST-SPEAK-001] Belt for the superseded path: that epilogue skips the
        // emit (it must not touch the successor's state), so without this the note would survive
        // into the next session and be filed under an id it has nothing to do with.
        heldConfirmDroppedByUser = null
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
            return runConfirm(location, reliability, vehicleId, pathLabel)
        }
        _detectionState.update {
            it.copy(confirmation = it.confirmation.holding(PendingConfirm(location, reliability, vehicleId, pathLabel, confirmedAt = now)))
        }
        PaparcarLogger.d(
            DIAG,
            "  ⏸ tentative confirm ($pathLabel) — holding ${config.confirmHoldMs}ms to rule out an errand stop [DET-C-02]"
        )
        // [DET-HOLD-BRANCHES-MUST-SPEAK-001] The open is the load-bearing one for testability: a
        // second OPENED in a trace is what distinguishes "the hold swallowed this fix" from "the
        // fast lane re-fired and restarted the clock" — the pair
        // DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001 measured as unobservable.
        logHold(HoldAction.OPENED, heldMs = 0L, pathLabel = pathLabel, location = location)
        return false
    }

    /**
     * [DET-FROZEN-COUNTER-001] Unattended-timeout fallback: a guard distrusts the EXACT anchor,
     * but the session measured real driving — a parking happened somewhere near the evidence, and
     * losing it entirely costs the user their car (field 2026-07-25/26, Redmi: 92 driving fixes
     * ended in a nudge nobody saw and no saved parking; the released spot left the vehicle
     * nowhere). Save an honest AREA instead: centered on the best witness ([center]), radius wide
     * enough to also cover [doubtMeters] (the guard's own measure of how far the truth may sit
     * from the center — a zone is only honest when that doubt is BOUNDABLE; guards with unbounded
     * doubt keep the nudge-only exit), reliability at the unattended floor so nothing
     * community-facing trusts it. The saved-parking card posted by [runConfirm] is the correction
     * surface.
     *
     * @return true when the zone was saved (the session outcome is stamped by [runConfirm] as
     *         `confirmed_unattended_zone_<reason>`); false when the save failed or a guard inside
     *         the confirm degraded it — the caller falls back to the nudge-only exit.
     */
    /** [DET-FROZEN-COUNTER-001][DET-USER-YES-IS-NOT-A-COORDINATE-001] The radius an approximate save
     *  claims: never below the floor a zone needs to mean anything, never above the ceiling the
     *  config asserts, and never smaller than either witness of the doubt — the centre's own
     *  accuracy or the bound the caller measured. ONE formula, because two paths now save zones
     *  (the unattended timeout and the user's "Sí" over an untrustworthy anchor) and a second copy
     *  is how a radius gets fixed in one and forgotten in the other. */
    private fun approximateZoneRadius(center: GpsPoint, doubtMeters: Double): Float =
        honestZoneRadius(
            centerAccuracyMeters = center.accuracy,
            doubtMeters = doubtMeters,
            floorMeters = config.honestCloseMinZoneRadiusMeters,
            ceilingMeters = config.unattendedZoneMaxRadiusMeters,
        )

    private suspend fun saveUnattendedZone(
        reason: UnattendedSaveReason,
        center: GpsPoint,
        doubtMeters: Double,
        vehicleId: String?,
        location: GpsPoint,
        now: Long,
    ): Boolean {
        val radius = approximateZoneRadius(center, doubtMeters)
        PaparcarLogger.d(
            DIAG,
            "  ◯ unattended zone (${reason.key}) — r=${radius}m (doubt=${doubtMeters.toInt()}m, centerAcc=${center.accuracy}) instead of losing the park [DET-FROZEN-COUNTER-001]"
        )
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        // runConfirm returns "session should end", not "saved" — a hard save failure also ends
        // the session. Only a real confirmed_* outcome counts as the zone being kept; anything
        // else falls back to the caller's nudge-only exit so the ask still happens.
        val ended = runConfirm(
            location = center,
            reliability = config.reliabilityUnattendedSave,
            vehicleId = vehicleId,
            pathLabel = "unattended_zone_${reason.key}",
            zoneRadiusMeters = radius,
        )
        val savedOk = ended && sessionOutcome.isConfirmed
        logDetection { sid ->
            DetectionEvent.Decision(
                sid, now,
                outcome = if (savedOk) "UNATTENDED_ZONE_SAVED" else "UNATTENDED_ZONE_SAVE_FAILED",
                pathLabel = "unattended_zone_${reason.key}",
                distanceMeters = doubtMeters,
                radiusMeters = radius,
                location = location,
            )
        }
        return savedOk
    }

    /**
     * [DET-WALK-ENTERED-ANCHOR-ZONE-001] The nudge-only exit of an unattended timeout: the session
     * could not honestly place anything, so it ASKS. One place instead of six copies, and the three
     * strings each reason emits (notification source, session outcome, trace label) travel together
     * on [UnattendedSaveReason] so a future branch cannot invent a fourth spelling — the field
     * traces are read by their exact wording.
     *
     * The copy the user actually sees lives in `showMarkParkingNudge`; nothing here leaks internal
     * mechanics into it.
     */
    private suspend fun nudgeUnattended(
        reason: UnattendedSaveReason,
        vehicleId: String?,
        location: GpsPoint,
        now: Long,
        distanceMeters: Double? = null,
    ) {
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        notificationPort.showMarkParkingNudge(source = reason.nudgeSource, vehicleId = vehicleId)
        sessionOutcome = SessionOutcome.AbortedUnattended(reason.key)
        logDetection { sid ->
            DetectionEvent.Decision(
                sid, now,
                outcome = reason.decisionOutcome,
                pathLabel = "unattended_timeout",
                distanceMeters = distanceMeters,
                location = location,
            )
        }
    }

    /**
     * @return whether the session should END (confirmed or hard-failed). `false` only when the
     *         repark-plausibility guard rejected the auto-confirm and this session degraded to a
     *         user prompt — the loop keeps collecting so a user "Sí" (reliability 1.0, guard
     *         bypassed) can still save, and the response-timeout cleans up if ignored. [DET-SOLID-001]
     */
    private suspend fun runConfirm(
        location: GpsPoint,
        reliability: Float,
        vehicleId: String?,
        pathLabel: String,
        /** Non-null → save an APPROXIMATE ZONE of this radius instead of an exact point — the
         *  unattended-timeout fallback that keeps the parking instead of losing it when a guard
         *  distrusts the exact anchor. [DET-FROZEN-COUNTER-001] */
        zoneRadiusMeters: Float? = null,
    ): Boolean {
        var sessionShouldEnd = true
        withContext(NonCancellable) {
            PaparcarLogger.d(DIAG, "    → confirmParking(reliability=$reliability, path=$pathLabel, zoneRadius=$zoneRadiusMeters) START")
            // [CONFIRM-NO-NOTIF-CLEANUP] Notification responsibility lives here: the auto-detection
            // path owns the unified state-B "Vehículo aparcado · Cancelar" card so the user has a
            // revert window if AR / steps misfired. See showParkingSavedConfirm call in onSuccess.
            confirmParking(
                location,
                reliability,
                vehicleId = vehicleId,
                tripMaxSpeedMps = _detectionState.value.maxSpeedMps,
                armEvidence = _detectionState.value.session.armEvidence,
                // [DET-ASSERTION-OUTRANKS-INFERENCE-001] The SUSTAINED figure, not the peak above:
                // the guard's job is to tell a real re-park from a walk-away, and one stray sample
                // is not a drive. [DET-MOTOR-PROOF-001]
                sessionSawDriving = sustainedDriveWitnessed(
                    _detectionState.value.provenDrivingBandMs,
                    config.sustainedDriveProofMs,
                ),
                // [DET-PIN-PROVENANCE-001] The confirmation path IS the provenance: "steps+egress",
                // "kinematic+egress", "vehicle-exit", "unattended_timeout", "user".
                detectionPath = pathLabel,
                zoneRadiusMeters = zoneRadiusMeters,
                // [DET-STEP-BUDGET-ORIGIN-001] The step baseline seals where the BODY is at
                // confirm — for an egress confirm that's the latest processed fix (already
                // 100+ m from the pin), NOT the anchor. Sealing "at the pin" made the walk
                // home read as a ride (field 2026-07-22, Glorieta).
                sealPoint = _detectionState.value.session.previousFix ?: location,
            )
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
                    sessionOutcome = SessionOutcome.Confirmed(pathLabel)
                    logDetection { sid ->
                        DetectionEvent.Decision(sid, nowMs(), outcome = "CONFIRMED", pathLabel = pathLabel, confidence = reliability, location = location)
                    }
                }
                .onFailure { e ->
                    if (e is PaparcarError.Parking.ImplausibleRepark) {
                        // [DET-SOLID-001] The guard says this auto-confirm would relocate a fresh
                        // nearby park without the session ever seeing driving — likely pedestrian.
                        // Degrade to the confirmation prompt instead of silently saving OR silently
                        // failing: a real (rare) ultra-short repark is one tap away, and the
                        // response-timeout aborts the session if the prompt is ignored.
                        PaparcarLogger.w(DIAG, "    ⊘ implausible repark → degrading to user prompt ($pathLabel) [DET-SOLID-001]")
                        val vehicleName = runCatching {
                            vehicleRepository.observeActiveVehicle().first()
                                ?.let { it.displayName(fallback = "").takeIf { n -> n.isNotBlank() } }
                        }.getOrNull()
                        notificationPort.showParkingConfirmation(IMPLAUSIBLE_REPARK_PROMPT_SCORE, vehicleName)
                        _detectionState.update {
                            it.copy(confirmation = it.confirmation.degradedToPrompt(shownAt = nowMs()))
                        }
                        logDetection { sid ->
                            DetectionEvent.Decision(
                                sid, nowMs(), outcome = "CONFIRM_DEGRADED_PROMPT", pathLabel = pathLabel,
                                // [DET-PROMPT-STATES-ITS-REASON-001] The SIXTH producer, and the only
                                // one outside the evaluator: it degrades on a rejected save, not on a
                                // doubted proof, and it read identically in the trace until now.
                                location = location, reason = PromptReason.IMPLAUSIBLE_REPARK.key,
                            )
                        }
                        sessionShouldEnd = false
                        return@onFailure
                    }
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
                    sessionOutcome = SessionOutcome.ConfirmFailed(pathLabel)
                    logDetection { sid ->
                        DetectionEvent.Decision(sid, nowMs(), outcome = "CONFIRM_FAILED", pathLabel = pathLabel, location = location)
                    }
                }
            PaparcarLogger.d(DIAG, "    ← confirmParking(reliability=$reliability, path=$pathLabel) END")
        }
        return sessionShouldEnd
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
            parkingDecisionInput(
                state = state,
                location = location,
                now = now,
                activeVehicleType = attributedVehicleType,
                elapsedSinceHighMs = elapsed,
                hadVehicleExit = phase.hadVehicleExit,
                // In the candidate phase by construction: High confidence only arrives after the
                // sustained-stop tier. [DET-HUMAN-POWERED-EARLY-CLOSE-001]
                restCertified = true,
            )
        )
        PaparcarLogger.d(
            DIAG,
            "  ⏳ CANDIDATE phase — elapsed=${elapsed}ms steps=${state.stepCount}/${config.minStepsToConfirm} → decision=$decision"
        )

        return when (decision) {
            is ParkingDecision.Confirmed -> {
                PaparcarLogger.d(DIAG, "  ▶ CANDIDATE confirmed via ${decision.pathLabel} — entering confirmParking(reliability=${decision.reliability})")
                val locationToConfirm = refinedParkLocation(state, location)
                // [DET-C-02] May hold instead of confirming now; returns true only on immediate confirm.
                beginConfirm(
                    location = locationToConfirm,
                    reliability = decision.reliability,
                    vehicleId = attributedVehicleId,
                    pathLabel = decision.pathLabel,
                    now = now,
                )
            }
            ParkingDecision.Rejected -> {
                // Window expired without the egress conjunction — discard. Phase falls back to
                // Notified (preserving shownAt so the response-timeout still applies — the user can
                // still tap the visible prompt). [FIX BUG-COORD-105][REFACTOR-200]
                PaparcarLogger.d(
                    DIAG,
                    "  ⊘ CANDIDATE expired without egress proof — discarding, steps ${state.stepCount} " +
                        "kept but no longer fresh [BUG-GARAGE-COLA-001][DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001]"
                )
                _detectionState.update {
                    it.copy(
                        confirmation = it.confirmation.notified(phase.shownAt),
                        // [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] A VERDICT MAY NOT DESTROY A
                        // MEASUREMENT. This used to be `stepCount = 0` — "the window expired, so
                        // those steps were phantom jiggle, wipe them" — which is the right thing to
                        // say to the NEXT candidate and the wrong thing to say to every other
                        // reader: the anchor lock, the walk-reach ceilings and, above all, the
                        // 15-minute unattended verdict that reads the same counter to justify
                        // saving a zone. Those steps HAPPENED; what expired is their power to
                        // confirm. So the count stands and the freshness line moves: a later
                        // candidate must earn `minStepsToConfirm` NEW steps beyond this mark.
                        // Only measured driving still zeroes both, exactly like `walkFixesSinceDriving`.
                        stepsAtLastDiscard = it.stepCount,
                    )
                }
                logDetection { sid -> DetectionEvent.Candidate(sid, now, action = "DISCARDED", phase = "Candidate→Notified", location = location) }
                false
            }
            is ParkingDecision.Prompt -> {
                degradeToPrompt(decision.pathLabel, decision.reason, location, now)
                false
            }
            // [DET-HUMAN-POWERED-EARLY-CLOSE-001] Terminal: nothing this candidate (or any next one
            // on the same stop) could produce is a car park. Reached when the human-powered evidence
            // lands AFTER the candidate opened — an AR `ON_BICYCLE` ENTER is delivered up to ~2 min
            // late, so the stop can mature before the ride is known to have been muscle-powered.
            ParkingDecision.CloseHumanPowered -> {
                closeHumanPoweredRide(location, attributedVehicleId, now)
                true
            }
            ParkingDecision.Inconclusive -> false
        }
    }

    /**
     * [DET-SOLID-001] All confirm conditions hold but the evidence is too weak for a silent
     * save (ENTER-only arm, session never saw driving — falsifiable by bus/taxi). Ask the user
     * via the existing prompt machinery: phase → [ConfirmationPhase.Notified] (promptShownAt
     * feeds the response-timeout), a "Sí" flows through the user-confirm precedence (reliability
     * 1.0, every guard bypassed), and silence aborts at `confirmationResponseTimeoutMs`.
     */
    private suspend fun degradeToPrompt(
        pathLabel: String,
        // [DET-PROMPT-STATES-ITS-REASON-001] WHICH of the six causes degraded this confirm. Not
        // defaulted: every caller knows its own reason, and a default would quietly resurrect the
        // anonymous prompt this ticket exists to remove.
        reason: PromptReason,
        location: GpsPoint,
        now: Long,
    ) {
        PaparcarLogger.d(DIAG, "  ？ confirm degraded to user prompt ($pathLabel, reason=${reason.key}) [DET-SOLID-001][DET-PROMPT-STATES-ITS-REASON-001]")
        val alreadyPrompted = _detectionState.value.confirmation.phase.promptShownAt != null
        if (!alreadyPrompted) {
            val vehicleName = runCatching {
                vehicleRepository.observeActiveVehicle().first()
                    ?.let { it.displayName(fallback = "").takeIf { n -> n.isNotBlank() } }
            }.getOrNull()
            notificationPort.showParkingConfirmation(WEAK_EVIDENCE_PROMPT_SCORE, vehicleName)
            // [DET-AR-FIRST-001 F4] The posting itself must be visible in parkdiag: this path
            // bypasses NotifyParkingConfirmation, and the 2026-07-10 19:19 session read as
            // "prompt never shown" in forensics when it HAD been posted right here.
            PaparcarLogger.d(DIAG, "  ▶ weak-evidence prompt notification POSTED (score=$WEAK_EVIDENCE_PROMPT_SCORE, vehicle=$vehicleName) [DET-AR-FIRST-001]")
            _detectionState.update { it.copy(confirmation = it.confirmation.notified(shownAt = now)) }
            logDetection { sid ->
                DetectionEvent.Decision(
                    sid, now, outcome = "CONFIRM_DEGRADED_PROMPT", pathLabel = pathLabel,
                    location = location, reason = reason.key,
                )
            }
        }
    }

    /**
     * [DET-HUMAN-POWERED-EARLY-CLOSE-001] The ONE place that assembles the pure decision's inputs.
     * Three lanes ask the same question (fast steps+egress confirm, the candidate phase, and the
     * stop-matured check as High is reached) and each used to build the 16-field input by hand — a
     * copy-paste triple where a signal added to one lane silently missed the others.
     *
     * Only three things genuinely differ per lane and they are the parameters: how long ago the
     * candidate reached High, whether a vehicle-exit was seen at that moment, and whether a
     * sustained stop has been certified.
     */
    private fun parkingDecisionInput(
        state: ParkingDetectionState,
        location: GpsPoint,
        now: Long,
        activeVehicleType: VehicleType?,
        elapsedSinceHighMs: Long,
        hadVehicleExit: Boolean,
        restCertified: Boolean,
    ) = ParkingDecisionInput(
        // [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] The confirm evaluator asks "may this pin
        // NOW?", so it gets the FRESH count. The unattended verdict asks "did the user walk away
        // from the car at all?" and keeps reading the full `stepCount` — same counter, two
        // questions, and conflating them is what let a discard erase a real egress.
        stepCount = state.freshStepCount,
        hasEgressDisplacement = hasEgressDisplacement(state, location),
        hadVehicleExit = hadVehicleExit,
        elapsedSinceHighMs = elapsedSinceHighMs,
        vehicleType = attributedVehicleType,
        sessionDurationMs = state.sessionDurationMs(now),
        maxSpeedKmh = state.maxSpeedKmh,
        sustainedDrivingMs = state.provenDrivingBandMs, // [DET-MOTOR-PROOF-001]
        evidenceLabel = state.session.armEvidence,
        hasKinematicEgress = hasKinematicEgressSignal(state),
        lastSpeedMps = state.session.lastSpeedMps,
        egressBornAtAnchor = isEgressBornAtAnchor(state),
        anchorWalkEntered = isAnchorWalkEntered(state),
        anchorGapEntered = state.anchorGapEnteredAtCapture,
        egressExceedsWalkReach = egressExceedsWalkReach(state, location),
        humanPoweredRide = humanPoweredRide(state, attributedVehicleType, now),
        restCertified = restCertified,
        // [DET-ASSERTION-OUTRANKS-INFERENCE-001] Would confirming HERE move a pin the user
        // asserted minutes ago and metres away, on a session that never measured a drive? Then
        // nothing this evaluator can prove outranks it.
        assertedPinBlocksRelocation = currentAssertedPin?.let { pin ->
            assertionBlocksRelocation(
                pinReliability = pin.detectionReliability,
                pinLocation = pin.location,
                candidate = location,
                nowMs = now,
                sessionSawDriving = sustainedDriveWitnessed(
                    state.provenDrivingBandMs,
                    config.sustainedDriveProofMs,
                ),
                userConfirmedReliability = config.reliabilityUserConfirmed,
                freshWindowMs = config.reparkPlausibilityWindowMs,
                radiusMeters = config.reparkPlausibilityRadiusMeters,
            )
        } ?: false,
    )

    /**
     * [DET-HUMAN-POWERED-EARLY-CLOSE-001] Terminal side effect of [ParkingDecision.CloseHumanPowered]:
     * the same offer the unattended timeout used to make 15 minutes later, made the moment the
     * verdict exists. Reuses [UnattendedSaveReason.HUMAN_POWERED] on purpose — one vocabulary in the
     * trace (`aborted_unattended_human_powered`, `UNATTENDED_HUMAN_POWERED_NUDGE`), so a field
     * comparison against every previous bicycle session still lines up.
     *
     * The nudge itself is a symptom of a bug this ticket does NOT fix: the departure was already
     * committed (spot published, session released, geofence removed) before anything proved a
     * drive, so the user's car is un-pinned and asking is the only way back. Once
     * DET-HANDOFF-NOT-MANUAL-001 §B stops committing without proof, a human-powered ride should end
     * SILENTLY — the old pin was never wrong.
     */
    private suspend fun closeHumanPoweredRide(
        location: GpsPoint,
        vehicleId: String?,
        now: Long,
    ) {
        PaparcarLogger.d(
            DIAG,
            "  ⊘ human-powered ride at a matured stop — closing NOW instead of idling to the " +
                "response timeout [DET-HUMAN-POWERED-EARLY-CLOSE-001]",
        )
        nudgeUnattended(UnattendedSaveReason.HUMAN_POWERED, vehicleId, location, now)
    }

    /** [ANCHOR-LOCK-001] Whether the park anchor is LOCKED: pedestrian steps were counted while
     *  stopped, so the user provably exited the car — later Doppler speed on the phone belongs
     *  to the PEDESTRIAN, not the car. A locked anchor is neither re-captured at later stops nor
     *  cleared by walking-range speed; only a REAL drive (≥ minimumTripSpeedMps, credible
     *  accuracy — the errand case: user came back and drove off) unlocks. */
    private fun isAnchorLocked(s: ParkingDetectionState): Boolean =
        s.bestStopLocation != null && s.stepCount >= config.anchorLockEgressSteps

    /** [DET-ANCHOR-FREEZE-001] LOCKED (step proof) or FROZEN (matured end-of-drive stop) — either
     *  way the anchor is pinned to the car: later stops never re-capture it and only re-measured
     *  real driving clears it. Locked and frozen are independent proofs of the same fact ("the
     *  car rests HERE"), so every consumer treats them identically. */
    private fun isAnchorPinned(s: ParkingDetectionState): Boolean =
        isAnchorLocked(s) || (s.bestStopLocation != null && s.anchorFrozen)

    /** [DET-CREDIBLE-DRIVE-001][DET-CONFIRM-FRESHNESS-001] The anchor belongs to a stop the user
     *  WALKED into — the pedestrian's standing spot, never the car's rest. The walk-fix budget
     *  alone over-triggers: the car's own final parking maneuver (decelerating 13.5→1.4 m/s into
     *  the spot, mediocre accuracy) spends it too (field 2026-07-23, Vista Hermosa: a perfect
     *  anchor tainted walk-entered → silent confirm degraded to a 1 AM prompt AND the unattended
     *  save refused — the FN's root). A real walk-in FIRES STEP EVENTS on a live counter, a
     *  maneuver fires none — so the MANEUVER EXEMPTION requires ALL of: zero step events since
     *  driving, a counter proven alive by capture, and an entry stretch short enough to be a glide
     *  ([ParkingDetectionConfig.maneuverEntryMaxWalkFixes]). The length cap is load-bearing: a
     *  counter alive EARLIER can go mute for a long walk (Camelias-Oppo: 73 steps counted, then
     *  ZERO events for the ~13-fix walk back to the house — step silence cannot be trusted across
     *  a long stretch, so that taint stands). */
    private fun isAnchorWalkEntered(s: ParkingDetectionState): Boolean {
        if (s.anchorWalkFixesAtCapture <= config.anchorFreezeMaxWalkFixes) return false
        val maneuverEntry = s.anchorStepEventsAtCapture == 0 &&
            s.anchorSawStepsAtCapture &&
            s.anchorWalkFixesAtCapture <= config.maneuverEntryMaxWalkFixes
        return !maneuverEntry
    }

    /** [DET-KINEMATIC-EGRESS-001] The GPS-measured egress walk: the anchor froze at the end of
     *  the drive and enough quality pedestrian-band fixes followed. Fed into the pure decision as
     *  [ParkingDecisionInput.hasKinematicEgress]; the decision itself still demands egress
     *  displacement and measured in-session driving. */
    private fun hasKinematicEgressSignal(s: ParkingDetectionState): Boolean =
        s.anchorFrozen && s.bestStopLocation != null &&
            s.kinematicEgressFixes >= config.kinematicEgressMinWalkFixes

    /** [DET-AR-FIRST-001 F3] Person/car discriminator for movement away from the park anchor:
     *  TRUE when the displacement from the anchor has OUTRUN what the steps counted since that
     *  stop could walk (`steps × anchorStrideMeters` + both accuracy envelopes + the egress noise
     *  floor) — physics says a vehicle moved, whatever the Doppler band says. FALSE while the
     *  steps cover the displacement (a person on foot — or not decidable yet: with a generous
     *  stride the pro-person bias is deliberate, see [ParkingDetectionConfig.anchorStrideMeters]).
     *  A phantom-step jam creep outruns its 1–3 jiggle steps within a couple of fixes; a real
     *  walk-away keeps pace with its own count (the counting gate feeds steps during the walk). */
    private fun movementOutrunsSteps(s: ParkingDetectionState, current: GpsPoint): Boolean {
        val anchor = s.bestStopLocation ?: return false
        return outrunsPedestrianReach(
            base = anchor,
            fix = current,
            steps = s.stepCount,
            strideMeters = config.anchorStrideMeters,
            floorMeters = config.minEgressDisplacementMeters,
        )
    }

    /** [DET-CONFIRM-FRESHNESS-001] Settle-time freshness check for a held confirm: the current fix
     *  sits farther from the HELD pin than the steps counted for this stop could walk (stride +
     *  both accuracy envelopes + the generous egress-birth floor — the same physics as
     *  [egressExceedsWalkReach], measured against the pending pin so it needs no live anchor).
     *  TRUE means a vehicle covered that ground after the tentative confirm: the evidence the hold
     *  was opened on is no longer true, and finalizing it would pin a stop the car provably left.
     *  A degraded fix inflates the reach through its own accuracy — fails conservative. */
    private fun heldConfirmOutrunByVehicle(
        pending: PendingConfirm,
        s: ParkingDetectionState,
        current: GpsPoint,
    ): Boolean {
        return outrunsPedestrianReach(
            base = pending.location,
            fix = current,
            steps = s.stepCount,
            strideMeters = config.anchorStrideMeters,
            floorMeters = config.egressBirthFloorMeters,
        )
    }

    /** [DET-CONFIRM-FRESHNESS-001] The fix sits provably OUTSIDE the anchor's accuracy envelopes
     *  (plus the egress noise floor): the position has measurably left the anchor. A Doppler blip
     *  while standing AT the anchor can never qualify, whatever its declared speed — its distance
     *  never escapes its own accuracy. */
    private fun escapesAnchorEnvelope(s: ParkingDetectionState, current: GpsPoint): Boolean {
        val anchor = s.bestStopLocation ?: return false
        // The same envelope with NO step credit: this asks whether the position has measurably
        // left the anchor at all, not whether a walker could have covered the gap.
        return outrunsPedestrianReach(
            base = anchor,
            fix = current,
            steps = 0,
            strideMeters = config.anchorStrideMeters,
            floorMeters = config.minEgressDisplacementMeters,
        )
    }

    /** [DET-EGRESS-PEDESTRIAN-CEILING-001] The CONFIRM counterpart of [movementOutrunsSteps]: TRUE
     *  when the current fix sits farther from the park anchor than a pedestrian egress could reach.
     *  Same physics — `steps × anchorStrideMeters` + both accuracy envelopes — but on a much more
     *  GENEROUS floor ([ParkingDetectionConfig.egressBirthFloorMeters], not the tight per-fix
     *  `minEgressDisplacementMeters`): a genuine egress under-logs steps and loses GPS (field trace
     *  Calle Gavia: 68 m walked on 8 logged steps), so a tight ceiling would strand real parks. Its
     *  only job is to rule out VEHICLE-scale displacement — the car driving ~500 m off a drop-off /
     *  pick-up stop while the frozen anchor's steps+egress try to pin a phantom park (field
     *  2026-07-18, Calle Abeto). `hasEgressDisplacement` is the floor on the egress; this is the
     *  ceiling. */
    private fun egressExceedsWalkReach(s: ParkingDetectionState, current: GpsPoint): Boolean {
        val anchor = s.bestStopLocation ?: return false
        return outrunsPedestrianReach(
            base = anchor,
            fix = current,
            steps = s.stepCount,
            strideMeters = config.anchorStrideMeters,
            floorMeters = config.egressBirthFloorMeters,
        )
    }

    /** [DET-CREDIBLE-DRIVE-001] Displacement-corroborated driving: the position has RUN from the
     *  anchor at sustained vehicle pace since the anchor's stop began. Believes no single fix —
     *  not its speed field, not its accuracy: the corroboration is the track itself. Floor sits
     *  beyond both accuracy envelopes plus a pathology margin (GPS recovery swings reach ~68 m
     *  and double back — field 2026-07-15, Camelias-Oppo); the rate window
     *  [minimumTripSpeedMps, sustainedDepartureMaxRateMps] excludes the walk home (≤2 m/s
     *  average) and the cache teleport (absurd rates). The current fix must itself be moving
     *  above walking pace — a pedestrian-band fix never carries the verdict, however far the
     *  anchor sits (that judgment belongs to the egress/ceiling machinery). This is what
     *  unfreezes the anchor when MIUI starves every individual fix of credible accuracy
     *  (field 2026-07-15, Enamorados: 10.12 m/s @ acc 52.4 — the 1.11 km FP's root). */
    private fun isSustainedDepartureFromAnchor(s: ParkingDetectionState, current: GpsPoint, now: Long): Boolean {
        val anchor = s.bestStopLocation ?: return false
        val sinceMs = s.anchorCapturedAtStop ?: return false
        val departure = sustainedDepartureFromAnchor(
            anchor = anchor,
            anchorStoppedSinceMs = sinceMs,
            fix = current,
            nowMs = now,
            movingBarMps = config.clearBestStopSpeedMps,
            floorMeters = config.sustainedDepartureFloorMeters,
            minRateMps = config.minimumTripSpeedMps,
            maxRateMps = config.sustainedDepartureMaxRateMps,
        ) ?: return false
        // The geometry is pure now; the LOG stays here, firing at the same instant it always did and
        // still carrying its numbers — the physics returns the MEASUREMENT rather than a boolean
        // precisely so the line does not have to be reworded or moved. `parkdiag` is byte-identical.
        PaparcarLogger.d(
            DIAG,
            "  ⇢ SUSTAINED DEPARTURE — position ran ${departure.distanceMeters.toInt()} m from the anchor at " +
                "${(departure.rateMps * 10).toInt() / 10.0} m/s avg — credible drive by displacement [DET-CREDIBLE-DRIVE-001]"
        )
        return true
    }

    /** [DET-CREDIBLE-DRIVE-001] Displacement corroboration for a MUTE-counter ambiguous-band
     *  fix: the position provably hopped from the previous fix — beyond BOTH accuracy envelopes
     *  plus [ParkingDetectionConfig.credibleDriveHopMarginMeters] — at a ground rate no walker
     *  sustains (≥ [ParkingDetectionConfig.clearBestStopSpeedMps]). Declared Doppler speed is
     *  what the mute band may not trust; a measured hop is independent evidence. Field-calibrated
     *  on both sides: the Galeote deceleration passes (23.7 m / 5 s against 9.9 m of joint
     *  accuracy — the car rolling to the kerb), the Camelias walk-back recovery swing fails every
     *  hop (its envelopes balloon exactly when it "moves": best case 11.9 m against 14.1 m of
     *  noise) — so the drag-to-home laundering stays impossible. */
    private fun isCorroboratedVehicleHop(prev: GpsPoint?, curr: GpsPoint): Boolean =
        isCorroboratedVehicleHop(
            prev = prev,
            curr = curr,
            hopMarginMeters = config.credibleDriveHopMarginMeters,
            minRateMps = config.clearBestStopSpeedMps,
        )

    /** [DET-DRIVE-PROOF-001] Track-level corroboration for the session's "measured driving"
     *  statistic. TRUE when the position PROVABLY covered a trip's worth of ground ending at
     *  the current (credible, driving-speed) fix: judged against a look-back fix aged
     *  [ParkingDetectionConfig.driveProofWindowMinMs]..[driveProofWindowMaxMs], the net
     *  displacement must escape both accuracy envelopes (plus the [isCorroboratedVehicleHop]
     *  pathology margin), reach [ParkingDetectionConfig.minimumTripDistanceMeters], stay under
     *  [ParkingDetectionConfig.sustainedDepartureMaxRateMps] (cache teleports claim absurd
     *  rates), and the window's own late-half fixes must have LEFT the look-back position — a
     *  real drive progresses through its window, while a mirage is flat-then-jump (the phone
     *  sat at home for every in-window fix and "moved" only at the burst; field 2026-07-27).
     *  Field-calibrated on both correct traces: Calle Gavia's whole drive is ONE 36-s hop of
     *  255 m with no in-window witnesses (sparse stream — passes), and the MIUI-starved
     *  Enamorados leg proves itself across 25-s windows of ~200 m even though NO single hop
     *  ever escapes its joint accuracy envelopes. The at-home mirage has no window at all: its
     *  burst died 10 s into the session. */
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

    private fun corroboratesDrive(history: List<GpsPoint>, curr: GpsPoint): Boolean =
        corroboratesDrive(history, curr, driveProofBounds)

    /** [DET-DRIVE-PROOF-001] Bounded ring behind [corroboratesDrive]: keeps fixes young enough
     *  to serve a future look-back window, hard-capped so a hot stream cannot grow the state. */
    private fun pruneRecentFixes(history: List<GpsPoint>, curr: GpsPoint): List<GpsPoint> =
        pruneRecentFixes(history, curr, driveProofBounds)

    /** [DET-ANCHOR-EGRESS-001] The egress must be BORN at the anchor — the ceiling the
     *  displacement gate never had (it only checks a floor, and at 1.11 km from the anchor it is
     *  trivially satisfied). TRUE while the recorded egress birth ([ParkingDetectionState.egressOriginFix])
     *  sits within walking-consistency of the pinned anchor: both accuracy envelopes, the steps
     *  already counted at birth, a fixed margin — or the hard floor, whichever is larger (sparse
     *  streams can put an honest birth ~100 m out; a wrong-stop anchor sits hundreds of meters to
     *  kilometers away — field 2026-07-15, Camino de los Enamorados). No anchor or no recorded
     *  egress → nothing to judge → true. */
    private fun isEgressBornAtAnchor(s: ParkingDetectionState): Boolean {
        val anchor = s.bestStopLocation ?: return true
        val origin = s.egressOriginFix ?: return true
        val d = io.apptolast.paparcar.domain.util.haversineMeters(
            anchor.latitude, anchor.longitude,
            origin.latitude, origin.longitude,
        )
        val allowance = anchor.accuracy + origin.accuracy +
            s.egressOriginStepCount * config.anchorStrideMeters +
            config.egressBirthMarginMeters
        return d <= maxOf(allowance, config.egressBirthFloorMeters)
    }

    /** [DET-ANCHOR-EGRESS-001 · Rule A] The position an AUTO confirm should pin. The stop anchor
     *  is measured sitting IN the car (roof multipath with optimistic claimed accuracy — field
     *  2026-07-15, Camelias: a 75-s in-car cluster converged at acc 3 m inside the house); the
     *  egress birth is measured seconds after the first step, phone in open air at the car door.
     *  When the birth carries pin-grade accuracy AND sits within the accuracy envelopes of the
     *  anchor, it is the better witness of "where the car is" — bounded, so it can never move
     *  the pin beyond GPS-noise scale. Anything weaker keeps today's anchor. */
    private fun refinedParkLocation(s: ParkingDetectionState, fallback: GpsPoint): GpsPoint {
        val anchor = s.bestStopLocation ?: return s.bestFix(fallback)
        val origin = s.egressOriginFix ?: return anchor
        // Steps are the witness that the birth is the DOOR and not mid-walk. A kinematic
        // (mute-counter) birth is recorded off a fix that is already moving — it feeds the
        // consistency ceiling but must never move the pin ("pin at the frozen anchor, not
        // along the walk" is exactly what the freeze promises mute hardware).
        if (s.egressOriginStepCount == 0) return anchor
        if (origin.accuracy > config.egressBirthRefineMaxAccuracyMeters) return anchor
        val d = io.apptolast.paparcar.domain.util.haversineMeters(
            anchor.latitude, anchor.longitude,
            origin.latitude, origin.longitude,
        )
        // The anchor↔birth gap must be EXPLAINED by the steps taken at birth plus fix noise —
        // that is the physical claim "this is still the car, seen from outside it". Anything
        // larger means one of the two is off in a way walking does not account for: keep the
        // anchor (conservative — on a sparse stream the first stepped fix can already be meters
        // into the walk, and a pin must never follow the pedestrian).
        val maxMove = s.egressOriginStepCount * config.anchorStrideMeters +
            anchor.accuracy + origin.accuracy
        if (d > maxMove) return anchor
        if (origin !== anchor) {
            PaparcarLogger.d(
                DIAG,
                "  ⚓→⚑ pin refined to egress birth (${d.toInt()} m from stop anchor, " +
                    "birthAcc=${origin.accuracy} anchorAcc=${anchor.accuracy}) [DET-ANCHOR-EGRESS-001 Rule A]"
            )
        }
        return origin
    }

    /**
     * Updates `stoppedSince` / `stoppedFixes` when the vehicle is stopped, or resets
     * them when it starts moving again. Returns the total stopped duration in ms.
     *
     * At driving speed ([ParkingDetectionConfig.clearBestStopSpeedMps]) the following are
     * also cleared to prevent stale signals from polluting the next genuine stop:
     * [bestStopLocation], [vehicleExitConfirmed], and the
     * [phase] (back to [ConfirmationPhase.Idle]). With a LOCKED anchor [ANCHOR-LOCK-001]
     * the clear bar rises to real driving ([ParkingDetectionConfig.minimumTripSpeedMps]).
     */
    private fun updateStopTracking(location: GpsPoint, now: Long): Long {
        return if (location.speed < config.stoppedSpeedThresholdMps) {
            _detectionState.update { s ->
                // [DET-STOP-MUST-BE-STILL-IN-SPACE-001] A stop is a claim about POSITION, and the
                // declared `speed` field is not position. Field 2026-08-22 Góndola: three fixes
                // reporting 0.0 m/s, 122 m apart over 9.6 s (12.8 m/s of measured ground), matured
                // the stop and froze the anchor in the side-street mouth — 70 m short of the spot,
                // while the car was still rolling in. The same reasoning [DET-CREDIBLE-DRIVE-001]
                // already refuses to trust declared Doppler for the mute band; a stop may not
                // trust it either. Judged against the stop's OWN origin fix (never against the
                // last driving fix — the fix that OPENS a stop is a hop by construction, and
                // discarding it would starve a sparse stream of its only anchor), and it takes
                // car-grade ground rate, so a 55-m GPS drift across a 60-s wait stays a stop.
                val stopOrigin = s.stoppedFixes.firstOrNull()
                val stillnessRefuted = stopOrigin != null && isCorroboratedVehicleHop(stopOrigin, location)
                if (stillnessRefuted && stopOrigin != null) {
                    val moved = io.apptolast.paparcar.domain.util.haversineMeters(
                        stopOrigin.latitude, stopOrigin.longitude,
                        location.latitude, location.longitude,
                    )
                    PaparcarLogger.d(
                        DIAG,
                        "  ⚓✗ stop REFUTED by its own track — ${moved.toInt()}m from the stop origin " +
                            "in ${(location.timestamp - stopOrigin.timestamp) / 1000}s while reporting " +
                            "${location.speed} m/s (envelopes ${stopOrigin.accuracy}+${location.accuracy}m); " +
                            "the car was still moving — not evidence of rest " +
                            "[DET-STOP-MUST-BE-STILL-IN-SPACE-001]"
                    )
                }
                val startedAt = s.stoppedSince ?: now
                val withinInitialWindow = (now - startedAt) < config.initialStopWindowMs
                // [DET-GAP-ANCHOR-001] A stop OPENING on the far side of a GPS hole: the previous
                // processed fix was still at REAL driving speed and this one arrived more than
                // anchorGapMaxFixGapMs later — the car's deceleration to rest happened entirely
                // inside the hole, so this position may be a drive-past point (a light, a stale
                // OEM fix), not the park. Speed-only on the pre-gap fix on purpose: Doppler speed
                // stays credible at accuracies that would fail the driving-accuracy bar (the
                // field fix: 17 m/s at 44 m), and requiring accuracy would exempt exactly the
                // degraded streams that produce the hole.
                // [DET-GAP-ANCHOR-ZONE-001] Keep the hole's SIZE, not just its existence: it is the
                // only bound on how far the phone could have walked from the car before the stream
                // came back, and throwing it away is what made every gap-born anchor look
                // unboundable.
                val newStopGapMs = if (s.stoppedSince == null) {
                    val holeMs = s.session.previousFix?.let { location.timestamp - it.timestamp } ?: 0L
                    if (s.session.previousFix != null &&
                        s.session.previousFix.speed >= config.minimumTripSpeedMps &&
                        holeMs > config.anchorGapMaxFixGapMs
                    ) holeMs else 0L
                } else {
                    s.stopEnteredAfterGapMs
                }
                if (newStopGapMs > 0L && s.stoppedSince == null) {
                    PaparcarLogger.d(
                        DIAG,
                        "  ⚓⚠ stop opened after a ${newStopGapMs}ms GPS hole " +
                            "with the car last seen DRIVING (${s.session.previousFix?.speed} m/s) — any anchor bound to this " +
                            "stop is GAP-ENTERED: rest unwitnessed, no silent pin; the hole bounds the doubt to " +
                            "${(newStopGapMs / 1000.0 * config.maxPedestrianSpeedMps).toInt()}m on foot " +
                            "[DET-GAP-ANCHOR-001][DET-GAP-ANCHOR-ZONE-001]"
                    )
                }
                // Freeze bestStopLocation after the initial-stop window (default 30 s). [LOC-001]
                // A PINNED anchor (locked by steps OR frozen by a matured end-of-drive stop) is
                // never re-captured at a LATER stop: the car provably rests at the anchor, so a
                // new stop is the pedestrian standing still, never the car. Same-stop refinement
                // (better fixes arriving right after the door slam) stays allowed.
                // [ANCHOR-LOCK-001][DET-ANCHOR-FREEZE-001]
                val pinnedToOtherStop = isAnchorPinned(s) && s.anchorCapturedAtStop != startedAt
                // [DET-ANCHOR-FREEZE-001] While no step has been counted, every fix of the SAME
                // continuous stop is still the parked car — accuracy refinement stays open for
                // the whole stop, not just the initial window. The 30-s cutoff kept a 260-m
                // approach-drift fix as the anchor while the real-spot 9.8-m fix arrived at
                // second 71 of the same stop (field 2026-07-11, Avenida Sanlúcar). The first
                // counted step ends the privilege: from there the better fix may be the walking
                // user, and the lock machinery takes over.
                val sameStopPreEgress = s.anchorCapturedAtStop == startedAt && s.stepCount == 0
                // [DET-STOP-MUST-BE-STILL-IN-SPACE-001] …and a fix the stop's own track refutes may
                // not become the anchor either. This is the load-bearing half: the anchor is read
                // from the raw fix here, NOT from `stoppedFixes`, so filtering that list alone would
                // have left the Góndola side-street mouth as the pin (its 6.0 m beat the 10.8 m of
                // the fix that opened the stop). Withholding capture is strictly SUBTRACTIVE — a
                // refuted fix can only fail to become the anchor, never displace a good one.
                val mayCapture = !pinnedToOtherStop && !stillnessRefuted &&
                    (withinInitialWindow || sameStopPreEgress)
                val newBestStop = when {
                    !mayCapture -> s.bestStopLocation
                    s.bestStopLocation == null || location.accuracy < s.bestStopLocation.accuracy -> location
                    else -> s.bestStopLocation
                }
                // [DET-STOP-MUST-BE-STILL-IN-SPACE-001] The refuted fix becomes the sole spatial
                // ORIGIN the next fixes are measured against, and the quorum starts over from it.
                // It is not evidence of REST: it cannot be the anchor this beat, and the freeze
                // still needs a full quorum of fixes that agree with it.
                //
                // Only the origin advances — `stoppedSince` deliberately does NOT. Restarting the
                // stop clock reopened `initialStopWindowMs` mid-stop, which let a re-capture happen
                // where master would never have allowed one; both Enamorados replays (the starved
                // MIUI stream whose anchor must stay disowned so the ceiling can prompt) caught it.
                // A creeping stop keeps its clock; what it loses is the right to call itself proven.
                val stoppedFixesNow = when {
                    stillnessRefuted -> listOf(location)
                    withinInitialWindow && s.stoppedFixes.size < config.maxStoppedFixes ->
                        s.stoppedFixes + location
                    else -> s.stoppedFixes
                }
                // [DET-ANCHOR-FREEZE-001] End-of-drive maturation. Three conditions, each load-
                // bearing: measured driving happened; the ANCHOR belongs to THIS stop (freezing
                // an anchor from an earlier stop would assert the car rests somewhere it left);
                // and the stop was DRIVE-ENTERED (walking-range fixes since the last resolved CAR
                // movement stayed within budget — the front-door stand arrives after a stretch of
                // them, the real park after none). Once frozen, only re-measured real driving
                // moves the anchor; a traffic light that matures unfreezes harmlessly when
                // driving resumes.
                val anchorStopOfRecord = if (newBestStop !== s.bestStopLocation) startedAt else s.anchorCapturedAtStop
                // [DET-SHORT-TRIP-FREEZE-001] Rest is proven by TIME (≥ anchorFreezeStopMs) OR by
                // EVIDENCE (≥ anchorFreezeStableFixes stopped fixes) — a short trip's destination
                // stop rarely lasts 60 s before the user walks off, but N dense stopped fixes prove
                // the car came to rest here. The other guards (drive-entered, this-stop) are unchanged.
                val restProvenByTime = (now - startedAt) >= config.anchorFreezeStopMs
                // The PRIOR count, not the one including this fix: the freeze fires on the fix whose
                // predecessors already reached the quorum. Counting this one too moves the freeze a
                // beat earlier and pins the Calle Gavia traffic stop (replay caught it).
                val restProvenByFixes = s.stoppedFixes.size >= config.anchorFreezeStableFixes
                val matured = !s.anchorFrozen && s.session.driveAuthorized &&
                    newBestStop != null && anchorStopOfRecord == startedAt &&
                    s.walkFixesSinceDriving <= config.anchorFreezeMaxWalkFixes &&
                    // [DET-STOP-MUST-BE-STILL-IN-SPACE-001] Not on the very beat the track refutes:
                    // this is what keeps the TIME path honest too, since a stop that opened 60 s ago
                    // and has been creeping ever since would otherwise mature on the clock alone.
                    !stillnessRefuted &&
                    (restProvenByTime || restProvenByFixes)
                if (matured) {
                    val how = if (restProvenByTime) "time=${now - startedAt}ms" else "stableFixes=${s.stoppedFixes.size}"
                    PaparcarLogger.d(
                        DIAG,
                        "  ⚓ anchor FROZEN — drive-entered stop matured ($how, " +
                            "walkFixes=${s.walkFixesSinceDriving}); only real driving " +
                            "(≥${config.minimumTripSpeedMps} m/s) can move it [DET-ANCHOR-FREEZE-001][DET-SHORT-TRIP-FREEZE-001]"
                    )
                }
                // [DET-ANCHOR-EGRESS-001] Egress birth, stopped flavour: the first counted step
                // with an anchor set — record where that walk began (typically at the car door).
                // Deliberately NOT gated on pinned: when pinning arrives late relative to the
                // walk, "first fix after pinned" is already meters into it; the 0→steps
                // transition is the earliest anchored witness of the walk start.
                val recordEgressBirth = s.egressOriginFix == null && newBestStop != null && s.stepCount > 0
                // Within the birth window a better-accuracy fix may sharpen the recorded birth —
                // ONLY while the step count proves the user is still standing at it (the bound
                // that keeps a slow walk from dragging the birth along, BUG-REPARK-WALK replay).
                val refineEgressBirth = !recordEgressBirth && s.egressOriginFix != null &&
                    (location.timestamp - s.egressOriginFix.timestamp) <= config.egressBirthWindowMs &&
                    s.stepCount <= s.egressOriginStepCount + config.egressBirthRefineMaxExtraSteps &&
                    location.accuracy < s.egressOriginFix.accuracy
                s.copy(
                    stoppedSince = startedAt,
                    stoppedFixes = stoppedFixesNow,
                    bestStopLocation = newBestStop,
                    anchorCapturedAtStop = anchorStopOfRecord,
                    // [DET-CREDIBLE-DRIVE-001] Stamp how much walking led into the anchor's stop
                    // the moment it (re)binds to THIS stop — the walk-entered taint reads it.
                    anchorWalkFixesAtCapture = if (anchorStopOfRecord != s.anchorCapturedAtStop)
                        s.walkFixesSinceDriving else s.anchorWalkFixesAtCapture,
                    // [DET-CONFIRM-FRESHNESS-001] Same-moment corroboration snapshot: how many
                    // step EVENTS the "walk in" actually produced, and whether the counter could
                    // have testified at all. Read together by isAnchorWalkEntered.
                    anchorStepEventsAtCapture = if (anchorStopOfRecord != s.anchorCapturedAtStop)
                        s.stepEventsSinceDriving else s.anchorStepEventsAtCapture,
                    anchorSawStepsAtCapture = if (anchorStopOfRecord != s.anchorCapturedAtStop)
                        s.sessionSawSteps else s.anchorSawStepsAtCapture,
                    // [DET-WALK-ENTERED-ANCHOR-ZONE-001] …and the MEASURED size of that walk-in:
                    // how far the run's first fix sits from the anchor it led to. Sealed at the same
                    // instant as the two above so the three witnesses describe the same capture.
                    anchorWalkInSpanMeters = if (anchorStopOfRecord != s.anchorCapturedAtStop) {
                        val origin = s.walkRunOriginFix
                        if (origin != null && newBestStop != null) {
                            io.apptolast.paparcar.domain.util.haversineMeters(
                                origin.latitude, origin.longitude,
                                newBestStop.latitude, newBestStop.longitude,
                            )
                        } else {
                            0.0
                        }
                    } else {
                        s.anchorWalkInSpanMeters
                    },
                    // [DET-GAP-ANCHOR-001] The gap taint binds with the anchor: stamped from the
                    // stop's own opening measurement, so same-stop accuracy refinements keep it.
                    stopEnteredAfterGapMs = newStopGapMs,
                    anchorGapMsAtCapture = if (anchorStopOfRecord != s.anchorCapturedAtStop)
                        newStopGapMs else s.anchorGapMsAtCapture,
                    anchorFrozen = s.anchorFrozen || matured,
                    egressOriginFix = if (recordEgressBirth || refineEgressBirth) location else s.egressOriginFix,
                    egressOriginStepCount = if (recordEgressBirth) s.stepCount else s.egressOriginStepCount,
                    session = s.session.observed(location),
                    // Reset the reposition counter on every stopped fix. [PARKING-001]
                    consecutiveRepositionFixes = 0,
                )
            }
            now - (_detectionState.value.stoppedSince ?: 0L)
        } else {
            val isDriving = isCredibleMovingFix(
                location, config.clearBestStopSpeedMps, config.minGpsAccuracyForDriving,
            )
            // [ANCHOR-LOCK-001] Real driving — unambiguous even for a phone on a pedestrian.
            // Field incident 2026-07-04: brisk walking away from the parked car produced Doppler
            // 2.5–3.6 m/s fixes (above clearBestStopSpeedMps) that wiped the true anchor; the park
            // then re-anchored where the user next stood still, 55 m away. Once egress steps are
            // observed, only THIS bar clears the anchor.
            val isRealDrive = isCredibleMovingFix(
                location, config.minimumTripSpeedMps, config.minGpsAccuracyForDriving,
            )
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
                val anchorPinned = isAnchorPinned(it)
                // [DET-AR-FIRST-001 F3] Person/car discriminator in the ambiguous band (above
                // clearBestStopSpeedMps, below real driving). The old rule cleared the anchor on
                // ANY credible ambiguous fix unless 8 steps had already locked it — a race the
                // anchor lost whenever the user exited the car immediately (field 2026-07-10,
                // Camelias: 3 steps at the kerb, the walk cleared the true anchor, the pin
                // re-anchored inside the house). Now the physics decide:
                //  - real driving speed → CAR, always wins (clears anchor + flushes steps);
                //  - sustained departure (the position provably RAN from the anchor at vehicle
                //    pace) → CAR even when no single fix is credible [DET-CREDIBLE-DRIVE-001];
                //  - pinned anchor (step lock OR end-of-drive freeze) → PERSON below real driving;
                //  - MUTE counter (zero steps) → the ambiguous band alone can NEVER prove CAR
                //    by its DECLARED speed: "outruns zero steps" is how the walk back from a
                //    reposition laundered the walk odometer and froze the anchor at the house
                //    door (field 2026-07-15, Camelias-Oppo) [DET-CREDIBLE-DRIVE-001]. But a hop
                //    the position PROVABLY made (beyond both accuracy envelopes, at vehicle
                //    ground rate) is independent evidence — without it, the car's own
                //    deceleration to the kerb reads as a walk-in and falsely taints the true
                //    anchor (field 2026-07-16, Galeote: 23.7 m in 5 s against 9.9 m of noise,
                //    counted as pedestrian). A recovery swing never escapes its own ballooning
                //    envelopes, so the Camelias laundering stays impossible;
                //  - displacement outruns the counted steps → CAR (jam creep with jiggle steps);
                //  - steps cover the displacement → PERSON: the anchor holds.
                val outruns = movementOutrunsSteps(it, location)
                val sustainedDeparture = isSustainedDepartureFromAnchor(it, location, now)
                val corroboratedMuteHop = it.stepCount == 0 && isDriving &&
                    isCorroboratedVehicleHop(it.session.previousFix, location)
                // [DET-CONFIRM-FRESHNESS-001] Stepless departure from a PINNED anchor: the position
                // keeps escaping the anchor's accuracy envelopes at ≥ clearBestStopSpeedMps while a
                // step counter PROVEN alive this session has counted NOTHING for this stop. A person
                // covering that ground feeds steps within a couple of fixes — a live counter's
                // silence is evidence of the CAR (the sub-real-driving traffic-light / parking-search
                // creep, field 2026-07-23 "Bodegas Osborne": 160 m at 6–16 km/h never moved the
                // frozen anchor and the egress walk then confirmed AT the light). A MUTE counter
                // (no step event all session) never trips this — the Camelias-Oppo walk-back
                // laundering stays impossible. Any step event resets the run.
                val steplessQualifies = anchorPinned && it.sessionSawSteps && it.stepCount == 0 &&
                    location.speed >= config.clearBestStopSpeedMps &&
                    escapesAnchorEnvelope(it, location)
                val newPinnedStepless =
                    if (steplessQualifies) it.pinnedSteplessMovingFixes + 1 else it.pinnedSteplessMovingFixes
                val steplessDeparture = newPinnedStepless >= config.frozenAnchorSteplessDepartureFixes
                // The precedence itself lives in `physics/EffectiveDriving.kt`, verbatim — its ORDER
                // is the content, and every row there won an argument with a real trip. The signals
                // are computed here because they read this session's state; the ranking between them
                // is physics and is now directly testable [07 §3.2].
                val effectiveDriving = effectiveDriving(
                    isRealDrive = isRealDrive,
                    sustainedDeparture = sustainedDeparture,
                    steplessDeparture = steplessDeparture,
                    anchorPinned = anchorPinned,
                    corroboratedMuteHop = corroboratedMuteHop,
                    stepsCounted = it.stepCount,
                    hasAnchor = it.bestStopLocation != null,
                    displacementOutrunsSteps = outruns,
                    isDriving = isDriving,
                )
                if (steplessDeparture && !isRealDrive && !sustainedDeparture) {
                    PaparcarLogger.d(
                        DIAG,
                        "  ⚓⤒ anchor UNPINNED — $newPinnedStepless stepless moving fixes beyond the " +
                            "anchor envelope with a LIVE counter silent → CAR creep, re-anchoring at " +
                            "the next stop [DET-CONFIRM-FRESHNESS-001]"
                    )
                }
                if (corroboratedMuteHop && !isRealDrive) {
                    PaparcarLogger.d(
                        DIAG,
                        "  ⤳ mute ambiguous fix corroborated as CAR by displacement " +
                            "(speed=${location.speed} acc=${location.accuracy}) [DET-CREDIBLE-DRIVE-001]"
                    )
                }
                if (anchorPinned && isDriving && !isRealDrive) {
                    val proof = if (isAnchorLocked(it)) "LOCKED (steps=${it.stepCount})" else "FROZEN (end-of-drive stop)"
                    PaparcarLogger.d(
                        DIAG,
                        "  🔒 anchor $proof — ignoring walking-range speed " +
                                "${location.speed} m/s (< ${config.minimumTripSpeedMps}) [ANCHOR-LOCK-001][DET-ANCHOR-FREEZE-001]"
                    )
                } else if (!anchorPinned && it.bestStopLocation != null && isDriving && !isRealDrive && !outruns) {
                    PaparcarLogger.d(
                        DIAG,
                        "  ♟ anchor HELD — steps=${it.stepCount} cover the displacement " +
                                "(speed=${location.speed} m/s ambiguous band) [DET-AR-FIRST-001]"
                    )
                }
                val newConsecutive = if (isRepositionCandidate) it.consecutiveRepositionFixes + 1 else 0
                // Reposition burst = slow CAR maneuver. Steps veto it — and so does a FROZEN
                // anchor: a brisk mute-counter walk (≥1.7 m/s, good accuracy) matches the burst's
                // signature exactly and outruns its zero steps, which is how the walk home would
                // clear the end-of-drive anchor. The frozen bar is real driving, nothing less.
                // [DET-AR-FIRST-001][DET-ANCHOR-FREEZE-001]
                val isRepositionBurst = newConsecutive >= config.repositionFixCount && !anchorPinned &&
                    (it.bestStopLocation == null || outruns)
                val shouldClearBestStop = effectiveDriving || isRepositionBurst
                if (isRepositionBurst && !effectiveDriving) {
                    PaparcarLogger.d(
                        DIAG,
                        "  ⟲ reposition-burst detected " +
                                "(consecutive=$newConsecutive speed=${location.speed} acc=${location.accuracy}) " +
                                "— clearing bestStopLocation [PARKING-001]"
                    )
                }
                // [REFACTOR-200] the conversation restarts on driving. Walking pace preserves
                // the current phase so the response-timeout from a prior prompt still ticks
                // — that's how BUG-STUCK-SESSION's "walked home" abort fires.
                val nextConfirmation =
                    if (effectiveDriving) it.confirmation.stopEnded() else it.confirmation
                // [DET-KINEMATIC-EGRESS-001] The egress walk, measured by GPS: quality
                // pedestrian-band fixes while the anchor is frozen. Cleared with the anchor.
                val newKinematicEgressFixes = when {
                    shouldClearBestStop -> 0
                    // [DET-KINEMATIC-EGRESS-001] The PEDESTRIAN band — speed BELOW the trip bar
                    // with the same accuracy gate. It shares the gate, not the question.
                    it.anchorFrozen &&
                        location.speed < config.minimumTripSpeedMps &&
                        isCredibleFixAccuracy(location, config.minGpsAccuracyForDriving) ->
                        it.kinematicEgressFixes + 1
                    else -> it.kinematicEgressFixes
                }
                // [DET-ANCHOR-EGRESS-001] Egress birth, moving flavour: the first pedestrian-band
                // evidence (step already counted, or the kinematic walk starting) with an anchor
                // set — where the egress walk was born.
                val recordEgressBirth = !shouldClearBestStop && it.egressOriginFix == null &&
                    it.bestStopLocation != null && (it.stepCount > 0 || newKinematicEgressFixes > 0)
                val refineEgressBirth = !shouldClearBestStop && !recordEgressBirth &&
                    it.egressOriginFix != null &&
                    (location.timestamp - it.egressOriginFix.timestamp) <= config.egressBirthWindowMs &&
                    it.stepCount <= it.egressOriginStepCount + config.egressBirthRefineMaxExtraSteps &&
                    location.accuracy < it.egressOriginFix.accuracy
                it.copy(
                    stoppedSince = null,
                    stoppedFixes = emptyList(),
                    confirmation = nextConfirmation,
                    bestStopLocation = if (shouldClearBestStop) null else it.bestStopLocation,
                    anchorCapturedAtStop = if (shouldClearBestStop) null else it.anchorCapturedAtStop,
                    anchorFrozen = if (shouldClearBestStop) false else it.anchorFrozen,
                    // [DET-GAP-ANCHOR-001] The stop is over; its gap flag dies with it. The
                    // anchor's stamped taint clears only with the anchor itself.
                    stopEnteredAfterGapMs = 0L,
                    anchorGapMsAtCapture = if (shouldClearBestStop) 0L else it.anchorGapMsAtCapture,
                    vehicleExitConfirmed = if (effectiveDriving) false else it.vehicleExitConfirmed,
                    consecutiveRepositionFixes = newConsecutive,
                    stepCount = if (effectiveDriving) 0 else it.stepCount,
                    // [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] The freshness line travels with
                    // the counter it indexes: measured driving is the only thing that clears both.
                    stepsAtLastDiscard = if (effectiveDriving) 0 else it.stepsAtLastDiscard,
                    // [DET-ANCHOR-FREEZE-001] The "entered on foot" odometer: a resolved CAR
                    // movement (driving verdict or reposition maneuver) zeroes it; anything else
                    // moving is pedestrian-band and counts.
                    walkFixesSinceDriving = if (effectiveDriving || isRepositionBurst) 0 else it.walkFixesSinceDriving + 1,
                    // [DET-WALK-ENTERED-ANCHOR-ZONE-001] Where that odometer started counting. A
                    // resolved CAR movement zeroes both; the first pedestrian-band fix after it
                    // marks the origin, and later fixes of the same run leave it alone.
                    walkRunOriginFix = when {
                        effectiveDriving || isRepositionBurst -> null
                        it.walkRunOriginFix == null -> location
                        else -> it.walkRunOriginFix
                    },
                    anchorWalkInSpanMeters = if (shouldClearBestStop) 0.0 else it.anchorWalkInSpanMeters,
                    // [DET-CONFIRM-FRESHNESS-001] The raw step-event odometer travels with the
                    // walk-fix odometer: both measure "since the last resolved CAR movement".
                    stepEventsSinceDriving = if (effectiveDriving || isRepositionBurst) 0 else it.stepEventsSinceDriving,
                    // [DET-CONFIRM-FRESHNESS-001] Cleared with the anchor (the run resolved as CAR
                    // or the anchor is gone); otherwise carries the accumulated stepless run.
                    pinnedSteplessMovingFixes = if (shouldClearBestStop) 0 else newPinnedStepless,
                    kinematicEgressFixes = newKinematicEgressFixes,
                    egressOriginFix = when {
                        shouldClearBestStop -> null
                        recordEgressBirth || refineEgressBirth -> location
                        else -> it.egressOriginFix
                    },
                    egressOriginStepCount = when {
                        shouldClearBestStop -> 0
                        recordEgressBirth -> it.stepCount
                        else -> it.egressOriginStepCount
                    },
                    session = it.session.observed(location),
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
     *
     * @return true when the session must END here: a certified sustained stop is the moment a
     *   human-powered ride's verdict is complete ([ParkingDecision.CloseHumanPowered]).
     *   [DET-HUMAN-POWERED-EARLY-CLOSE-001][DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001]
     */
    private suspend fun evaluateConfidence(
        location: GpsPoint,
        stoppedDuration: Long,
        state: ParkingDetectionState,
        now: Long,
        activeVehicleId: String?,
        activeVehicleType: VehicleType?,
    ): Boolean {
        // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §B] The sustained rest is a MEASUREMENT — read the
        // clock, never infer it from the score.
        //
        // DET-HUMAN-POWERED-EARLY-CLOSE-001 asked this question inside `advanceHigh`, reasoning
        // that "High IS the certified sustained stop, its only route is the 5-minute tier". That is
        // true of the scorer's SLOW path — and the fast path pre-empts it: with an AR vehicle exit
        // and 30 s stopped, `CalculateParkingConfidenceUseCase` returns Medium (0,65) and never
        // looks at the tiers, so High becomes unreachable for the rest of the session. Since a
        // human-powered ride also has its Low/Medium prompt SUPPRESSED, such a session had no
        // prompt (no response timeout), no High (no close) and no candidate: nothing could ever end
        // it. Field 2026-08-20: 102 minutes of foreground service and 967 fixes with one AR exit
        // 15 seconds after parking, and the only exit it ever found was an unrelated walk clearing
        // the egress floor 79 minutes later.
        //
        // The rest is certified by the same number the tier used (`slowPath5MinMs`) — no new clock,
        // just read directly. Asked BEFORE the tier dispatch so every tier reaches it.
        if (stoppedDuration >= config.slowPath5MinMs) {
            val restVerdict = evaluateParkingDecision(
                parkingDecisionInput(
                    state = state,
                    location = location,
                    now = now,
                    activeVehicleType = attributedVehicleType,
                    elapsedSinceHighMs = 0L,
                    hadVehicleExit = state.vehicleExitConfirmed,
                    restCertified = true,
                )
            )
            if (restVerdict == ParkingDecision.CloseHumanPowered) {
                closeHumanPoweredRide(location, attributedVehicleId, now)
                return true
            }
        }

        val signals = ParkingSignals(
            speed = location.speed,
            stoppedDurationMs = stoppedDuration,
            gpsAccuracy = location.accuracy,
            activityExit = state.vehicleExitConfirmed,
        )
        val confidence = calculateParkingConfidence(signals)
        PaparcarLogger.d(DIAG, "  ⚖ scoring=$confidence (signals: speed=${signals.speed} stopped=${signals.stoppedDurationMs}ms accuracy=${signals.gpsAccuracy} exit=${signals.activityExit})")

        // [REFACTOR-200] phase advancement via explicit transitions.
        return when (confidence) {
            is ParkingConfidence.NotYet -> false

            is ParkingConfidence.Low,
            is ParkingConfidence.Medium -> {
                advanceLowMedium(confidence, state, now, humanPoweredRide(state, attributedVehicleType, now))
                false
            }

            is ParkingConfidence.High -> {
                advanceHigh(confidence, state, now)
                false
            }
        }
    }

    private suspend fun advanceLowMedium(
        confidence: ParkingConfidence,
        state: ParkingDetectionState,
        now: Long,
        /** [DET-HUMAN-POWERED-EARLY-CLOSE-001] The ride was muscle-powered: "¿has aparcado?" is the
         *  wrong question to put on screen, and the High tier a few minutes later ends the session
         *  with the honest one. Asking anyway is what the 2026-08-19 bicycle session did at 22:46. */
        humanPowered: Boolean,
    ) {
        when (val phase = state.confirmation.phase) {
            is ConfirmationPhase.Idle -> {
                _detectionState.update { it.copy(confirmation = it.confirmation.lowReached(now)) }
                PaparcarLogger.d(DIAG, "  → phase: Idle → LowReached(firstReachedAt=$now) [BUG-DETECT-310502]")
            }

            is ConfirmationPhase.LowReached -> {
                val hasExit = state.vehicleExitConfirmed
                val timeoutReached = (now - phase.firstReachedAt) >= config.lowNotifTimeoutMs
                if (humanPowered) {
                    // [DET-HUMAN-POWERED-EARLY-CLOSE-001] Suppressed, not deferred: the phase stays
                    // LowReached (no `shownAt` claiming a prompt nobody saw), so if the veto lifts —
                    // an AR `IN_VEHICLE` ENTER superseding the bicycle stamp — the very next fix
                    // shows the prompt normally, its timeout still measured from `firstReachedAt`.
                    PaparcarLogger.d(
                        DIAG,
                        "  ⊘ Low/Medium notif suppressed — human-powered ride, the matured stop " +
                            "will close the session instead [DET-HUMAN-POWERED-EARLY-CLOSE-001]",
                    )
                } else if (hasExit || timeoutReached) {
                    val reason = if (hasExit)
                        "exit=${state.vehicleExitConfirmed}"
                    else
                        "timeout=${now - phase.firstReachedAt}ms"
                    PaparcarLogger.d(DIAG, "  → showing parking-confirmation notif (Low/Medium, $reason)")
                    _detectionState.update { it.copy(confirmation = it.confirmation.notified(now)) }
                    notifyParkingConfirmation(confidence)
                    // [DET-FROZEN-COUNTER-001] The prompt instant must exist in the remote trace:
                    // the 2026-07-25 00:35 Redmi prompt was invisible in forensics — the 15-min
                    // response window it opened could only be inferred backwards from the timeout.
                    logDetection { sid ->
                        DetectionEvent.Decision(
                            sid, now, outcome = "PROMPT_SHOWN", pathLabel = "low_medium($reason)",
                            confidence = (confidence as? ParkingConfidence.Medium)?.score,
                            location = state.session.previousFix,
                        )
                    }
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

    /**
     * @return true when the session must END instead of opening (or re-opening) a candidate.
     *   [DET-HUMAN-POWERED-EARLY-CLOSE-001]
     */
    private suspend fun advanceHigh(
        confidence: ParkingConfidence,
        state: ParkingDetectionState,
        now: Long,
    ) {
        // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §B] The close question used to live HERE, on the
        // premise that High is the only certified sustained stop. It is not — the scorer's fast
        // path caps at Medium whenever AR delivered a vehicle exit, which made this the one door a
        // suppressed-prompt session could never reach. It now lives in [evaluateConfidence], asked
        // off the measured stop clock before any tier dispatch, so every route to a matured rest
        // passes through it exactly once. A High reached through the slow path has stopped for
        // `slowPath5MinMs` by construction, so nothing is lost by not re-asking here.
        when (val phase = state.confirmation.phase) {
            is ConfirmationPhase.Idle, is ConfirmationPhase.LowReached -> {
                // Prompt was never shown — fire it as part of this transition.
                PaparcarLogger.d(DIAG, "  ▶ HIGH reached — entering CANDIDATE phase + showing notif, vehicleExit=${state.vehicleExitConfirmed}")
                _detectionState.update { it.copy(confirmation = it.confirmation.candidate(now, state.vehicleExitConfirmed, now)) }
                notifyParkingConfirmation(confidence)
                logDetection { sid -> DetectionEvent.Candidate(sid, now, action = "OPENED", phase = "from ${phase::class.simpleName}") }
                // [DET-FROZEN-COUNTER-001] Same PROMPT_SHOWN marker as the Low/Medium lane, so
                // every response-timeout window has its opening instant in the remote trace.
                logDetection { sid ->
                    DetectionEvent.Decision(
                        sid, now, outcome = "PROMPT_SHOWN", pathLabel = "high_candidate",
                        confidence = (confidence as? ParkingConfidence.High)?.score,
                        location = state.session.previousFix,
                    )
                }
            }

            is ConfirmationPhase.Notified -> {
                // Prompt already shown at phase.shownAt — preserve it so the response timeout
                // keeps ticking from the original prompt instant.
                PaparcarLogger.d(DIAG, "  ▶ HIGH reached after Notified(shownAt=${phase.shownAt}) — entering CANDIDATE phase (suppressing duplicate notif) [BUG-STUCK-SESSION]")
                _detectionState.update { it.copy(confirmation = it.confirmation.candidate(now, state.vehicleExitConfirmed, phase.shownAt)) }
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
