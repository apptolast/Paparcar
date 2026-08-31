package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.ArmLabel
import com.rndeveloper.paparcar.domain.detection.DetectionPath
import com.rndeveloper.paparcar.domain.detection.physics.DrivingEvidence
import com.rndeveloper.paparcar.domain.detection.physics.sustainedDriveWitnessed
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.VehicleType

/**
 * Outcome of [EvaluateParkingDecisionUseCase] for a stop that has reached the CANDIDATE phase.
 * Mirror of [DepartureDecision] — the parking half now speaks the same corroboration language as
 * the departure half. [DET-D-01]
 *
 * - [Confirmed]    — two independent signals agree (always including egress displacement, the one
 *                    signal impossible to fake at a traffic stop). Carries the path that decided and
 *                    the reliability to stamp on the saved session.
 * - [Rejected]     — the observation window expired without the egress conjunction → discard the
 *                    candidate (likely a queue / traffic stop).
 * - [Inconclusive] — keep the candidate open; the window has not elapsed and no proof has arrived.
 */
/**
 * WHY a confirm degraded into a question. [DET-PROMPT-STATES-ITS-REASON-001]
 *
 * A second axis, orthogonal to `pathLabel`: the path says HOW the park was proven, this says why
 * that proof was not trusted enough to save silently. Six causes shared one string
 * (`CONFIRM_DEGRADED_PROMPT`) until field 2026-08-20 23:56 (Oppo, session `1787263007358`), where a
 * 63 km/h drive saved nothing and two of its three verdicts were unattributable — the case only
 * closed because the THIRD, the unattended timeout, names its cause through [UnattendedSaveReason].
 * This is that enum's sibling, deliberately the same shape so both families of trace read alike.
 *
 * [key] rides the diagnostics `reason` column — the one `HonestClose`, `Released` and
 * `GeofenceRegistration` already use. The `outcome` string stays `CONFIRM_DEGRADED_PROMPT` on
 * purpose: renaming it would silently break every saved trace and every memory note that quotes it,
 * the same reason [UnattendedSaveReason] refused to rename two of its historical labels.
 */
enum class PromptReason(val key: String) {
    /** The arm's only vehicle proof was an AR ENTER (falsifiable by bus/taxi) and the session's own
     *  stream never witnessed driving. [DET-SOLID-001][DET-UNVERIFIED-CONFIRM-001] */
    WEAK_EVIDENCE("weak_evidence"),

    /** Profile says bike/scooter, or the ride measured as human-powered. [DET-BIKE-NOT-A-CAR-001] */
    HUMAN_POWERED("human_powered"),

    /** The egress was born away from the anchor: the park is probably real, the ANCHOR is not.
     *  [DET-ANCHOR-EGRESS-001] */
    EGRESS_NOT_AT_ANCHOR("egress_not_at_anchor"),

    /** The anchor was captured at a stop the user walked into. [DET-CREDIBLE-DRIVE-001] */
    ANCHOR_WALK_ENTERED("anchor_walk_entered"),

    /** The anchor's stop opened through a GPS hole — the rest was never witnessed.
     *  [DET-GAP-ANCHOR-001] */
    ANCHOR_GAP_ENTERED("anchor_gap_entered"),

    /** The repark guard refused the save: it would relocate a fresh nearby park without the session
     *  ever seeing driving. [DET-SOLID-001] */
    IMPLAUSIBLE_REPARK("implausible_repark"),
}

sealed interface ParkingDecision {
    data class Confirmed(val pathLabel: String, val reliability: Float) : ParkingDecision
    data object Rejected : ParkingDecision
    data object Inconclusive : ParkingDecision
    /** All confirm conditions hold, but something makes the save untrustworthy enough to ask instead
     *  — [reason] says which of the six. [DET-SOLID-001][DET-PROMPT-STATES-ITS-REASON-001] */
    data class Prompt(val pathLabel: String, val reason: PromptReason) : ParkingDecision

    /**
     * [DET-HUMAN-POWERED-EARLY-CLOSE-001] TERMINAL: the movement was made under human power and
     * the stop has matured — this session can never produce a car park, so it ends NOW.
     *
     * Distinct from [Rejected], which discards one candidate and keeps the stop alive for the next
     * one. A bicycle's next candidate is another bicycle's candidate: field 2026-08-19 22:32, the
     * ride ended at home at 22:42 and the session then recycled CANDIDATE↔Notified three times
     * (22:50 → 22:55 → 23:00, each discard zeroing `stepCount` so the loop could not have confirmed
     * anything even in principle) until the 15-min response timeout finally read the SAME
     * human-powered flag it had held all along. 19 minutes of foreground service + 2-5 s GPS to
     * reach a verdict that was available at the first matured stop.
     */
    data object CloseHumanPowered : ParkingDecision
}

/**
 * Pure inputs for one candidate-phase decision. Deliberately primitives (not the coordinator's
 * private state) so the decision is a pure function of corroboration signals — replayable from a
 * recorded trace without any coroutine / Flow / sensor machinery. [DET-D-02]
 */
data class ParkingDecisionInput(
    /** Pedestrian steps counted while stopped post-drive. */
    val stepCount: Int,
    /** Whether the current fix is ≥ `minEgressDisplacementMeters` from the pinned park anchor. */
    val hasEgressDisplacement: Boolean,
    /** Snapshot of `vehicleExitConfirmed` at candidate entry — selects the observation window. */
    val hadVehicleExit: Boolean,
    /** Wall-clock ms elapsed since the candidate reached HIGH confidence. */
    val elapsedSinceHighMs: Long,
    /** Active vehicle profile (for the scooter mismatch guard); null until locked. */
    val vehicleType: VehicleType?,
    /** Session wall-clock duration (for the mismatch guard). */
    val sessionDurationMs: Long,
    /** Session top speed in km/h (for the mismatch guard — a CEILING on slow trips, never a
     *  proof of motor). */
    val maxSpeedKmh: Float,
    /** [DET-MOTOR-PROOF-001] Cumulative ms the session spent in the credible driving band across
     *  consecutive in-band fixes, drive-proof-gated (zero until the track proved a drive) — see
     *  `ParkingDetectionState.provenDrivingBandMs`. This is what `sessionSawDriving` reads now: a
     *  PEAK cannot separate motor from muscle (a 6-min bicycle ride touched 18,1 km/h on ~6 s of
     *  its 6 minutes and pinned the bike rack in silence, field 2026-08-18 20:32; the 2026-08-16
     *  ride peaked at 38 km/h), while sustained band time separates clean — the worst legitimate
     *  car trace on file (Calle Gavia, skeletal stream) still held one 36-s hop. */
    val sustainedDrivingMs: Long = 0L,
    /**
     * [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] The session's ONE verdict on whether it watched a car
     * drive, built by `DetectionSessionState.drivingEvidence`. Only
     * [com.rndeveloper.paparcar.domain.detection.physics.DrivingEvidence.Measured] authorises a
     * silent pin.
     *
     * Defaults to `null` for the replay/legacy callers that predate it; a null reads as "this input
     * carries no verdict", and the policy then falls back to [sustainedDrivingMs] alone, which is
     * exactly the pre-ticket behaviour. New call sites must pass it.
     */
    val drivingEvidence: DrivingEvidence? = null,
    /**
     * Arm-evidence label of the session; null for legacy callers. [DET-SOLID-001]
     *
     * [DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001] An [ArmLabel] and no longer a
     * flat string. It was kept flat "so the input stays replayable", but the producer already held
     * the typed value and stringified it here for the policy below to classify it again by
     * spelling. A replay parses the recorded word once, with [ArmLabel.ofPersisted], and everything
     * downstream reads a type.
     */
    val evidenceLabel: ArmLabel? = null,
    /** [DET-KINEMATIC-EGRESS-001] The frozen end-of-drive anchor has watched the phone WALK away:
     *  ≥ `kinematicEgressMinWalkFixes` quality pedestrian-band fixes since the freeze. GPS-measured
     *  egress for mute-step-counter hardware — the same inference the freeze already trusts to
     *  protect the anchor ("this movement is the person, not the car"), now allowed to confirm.
     *  Only counts when the session itself measured driving (a seeded arm whose stream never saw
     *  the trip must keep asking). */
    val hasKinematicEgress: Boolean = false,
    /** [DET-STEP-SPEED-GATE-001] Speed (m/s) of the most recent GPS fix. The evaluator only ever
     *  saw `maxSpeedKmh` (session PEAK), so it could confirm steps+egress while the car was still
     *  ROLLING — the in-motion false positive at Avenida de los Mástiles (field 2026-07-12). No
     *  path may auto-confirm while this is above the pedestrian ceiling (`egressStepMaxSpeedMps`). */
    val lastSpeedMps: Float = 0f,
    /** [DET-ANCHOR-EGRESS-001] FALSE when the egress evidence was BORN outside walking-consistency
     *  of the pinned anchor (see `CoordinatorParkingDetector.isEgressBornAtAnchor`): the walk
     *  cannot be an egress FROM that anchor, so the anchor belongs to an intermediate stop
     *  (field 2026-07-15: frozen at a traffic light 1.11 km before the real park, confirmed
     *  kinematic+egress at the light). The displacement gate only ever had a FLOOR — this is its
     *  ceiling. Defaults to true for legacy callers. */
    val egressBornAtAnchor: Boolean = true,
    /** [DET-CREDIBLE-DRIVE-001] TRUE when the anchor was captured at a stop the user WALKED into
     *  (walk fixes above `anchorFreezeMaxWalkFixes` led to it) — the pedestrian's standing spot,
     *  not the car's rest. Field 2026-07-15, Camelias-Oppo: a mute step counter let the walk back
     *  from a reposition read as driving; the anchor bound to the house door 37 m from the car and
     *  steps+egress confirmed there. All proofs may hold — the user DID park — but not where this
     *  anchor says: ask, never pin. Defaults to false for legacy callers. */
    val anchorWalkEntered: Boolean = false,
    /** [DET-GAP-ANCHOR-001] TRUE when the anchor bound to a stop that OPENED through a GPS hole:
     *  the fix that started the stop arrived > `anchorGapMaxFixGapMs` after a fix still at real
     *  driving speed. The car was last seen MOVING and its arrival at rest was never witnessed —
     *  the anchor may be a drive-past point the stream happened to sample (field 2026-07-29,
     *  Redmi Av. Sanlúcar: a 100-s MIUI hole ended in one speed-0 fix mid-route; the egress walk
     *  home then satisfied steps+egress and pinned 315 m before the real park). Same class as
     *  [anchorWalkEntered]: the proofs hold, the ANCHOR does not — ask, never pin. Defaults to
     *  false for legacy callers. */
    val anchorGapEntered: Boolean = false,
    /** [DET-EGRESS-PEDESTRIAN-CEILING-001] TRUE when the displacement from the anchor exceeds what a
     *  pedestrian egress could reach (`CoordinatorParkingDetector.egressExceedsWalkReach`: steps ×
     *  stride + both accuracy envelopes + a generous walk-reach floor): the distance can only have
     *  been covered by a VEHICLE, not a person on foot. [hasEgressDisplacement] is only a FLOOR
     *  (d ≥ minEgress); a car driving away from a brief drop-off / pick-up stop satisfies it just as
     *  well as a pedestrian walking away — the two "independent" proofs (steps, egress) are then both
     *  producible without anyone leaving the car (field 2026-07-18, Calle Abeto: stopped to pick a
     *  passenger up, ~26 phantom/incidental steps counted while parked + the car driving ~500 m away
     *  read as steps+egress → false pin, and the real park after it was lost). This is the pedestrian
     *  CEILING on the egress floor. The floor is deliberately generous (a real egress under-logs
     *  steps and loses GPS: field trace Calle Gavia walked 68 m on 8 logged steps) so it only ever
     *  rules out vehicle-scale distance. Vetoes the step- and window-based paths; the kinematic path
     *  stands on its own pedestrian-band fixes. Defaults to false for legacy callers. */
    val egressExceedsWalkReach: Boolean = false,
    /** [DET-BIKE-NOT-A-CAR-001] The session's own movement was made under HUMAN power — AR reported
     *  `ON_BICYCLE` and no later boarding superseded it (see `EvaluateHumanPoweredRideUseCase`).
     *  [vehicleType] answers "what do you drive"; this answers "what are you on right now", and the
     *  field FP of 2026-08-16 turned on the difference: a `CAR` profile on a bicycle re-pinned the
     *  car 4,8 km away. Defaults to false for legacy callers. */
    val humanPoweredRide: Boolean = false,
    /** [DET-HUMAN-POWERED-EARLY-CLOSE-001] The confidence scorer has certified a SUSTAINED stop —
     *  the caller is at (or entering) the CANDIDATE phase, which High confidence only grants after
     *  the 5-minute stopped tier. It is the pure expression of "the ride is over", and the terminal
     *  [ParkingDecision.CloseHumanPowered] needs it: without it, a cyclist pausing at a light with
     *  a few steps counted would be closed mid-ride by the fast-confirm lane, which runs with
     *  `elapsedSinceHighMs = 0` and no stop behind it. Defaults to false — a caller that has not
     *  certified rest must not get the terminal verdict. */
    val restCertified: Boolean = false,
    /** [DET-ASSERTION-OUTRANKS-INFERENCE-001] The vehicle already holds an ACTIVE pin the USER
     *  asserted, fresh and within walking reach of this candidate, and this session has not
     *  measured driving — computed by the caller through
     *  [com.rndeveloper.paparcar.domain.detection.assertionBlocksRelocation]. Nothing this evaluator
     *  can prove is stronger than that assertion, so the candidate is discarded rather than
     *  confirmed OR asked about. Defaults to false for legacy callers. */
    val assertedPinBlocksRelocation: Boolean = false,
)

/**
 * The candidate-phase decision, extracted from `CoordinatorParkingDetector.evaluateCandidatePhase`
 * as a pure function so the 9-path precedence collapses into one testable place. [DET-D-02]
 *
 * **Invariant (DET-C-01):** egress displacement is mandatory for every [ParkingDecision.Confirmed].
 * STILL, dwell-time and AR-exit-time on their own never confirm — they only keep the candidate
 * [ParkingDecision.Inconclusive] until the window expires, then [ParkingDecision.Rejected].
 *
 * Behaviour is identical to the pre-extraction coordinator; the orchestrator still owns the side
 * effects (running the confirm, mutating the phase, posting notifications).
 */
class EvaluateParkingDecisionUseCase(private val config: ParkingDetectionConfig) {

    operator fun invoke(input: ParkingDecisionInput): ParkingDecision {
        // [DET-EGRESS-PEDESTRIAN-CEILING-001] The egress displacement exceeded a pedestrian's reach
        // from the anchor → a vehicle covered it, not a walk. hasEgressDisplacement is only a floor;
        // the car's own departure from a drop-off / pick-up stop clears it. The reach ceiling is
        // generous (a real egress under-logs steps and loses GPS), so this only invalidates the
        // egress conjunction for the step- and window-based paths on vehicle-scale distance. The
        // kinematic path is exempt: it proves egress from pedestrian-BAND fixes, which a departing
        // car cannot produce, and legitimately carries few or no steps.
        val egressIsVehicular = input.egressExceedsWalkReach

        val hasStepsProof = input.stepCount >= config.minStepsToConfirm &&
            input.hasEgressDisplacement && !egressIsVehicular
        val window = if (input.hadVehicleExit)
            config.vehicleExitObservationWindowMs
        else
            config.confirmationObservationWindowMs
        val windowElapsed = input.elapsedSinceHighMs >= window

        // [DET-SOLID-001] The session's own stream witnessed real driving. Gates both the
        // weak-evidence policy below and the kinematic egress proof: a seeded arm whose stream
        // never measured the trip has no business confirming silently by ANY path.
        // [DET-MOTOR-PROOF-001] "Witnessed driving" means witnessed a MOTOR: sustained time in
        // the driving band, not the session peak — a bicycle clears any peak threshold a car
        // must also clear (18,1 km/h for ~6 s pinned the bike rack, field 2026-08-18; 38 km/h
        // on 2026-08-16), but cannot HOLD the band the way even the weakest car trace does.
        //
        // [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] And "witnessed a motor" is now ONE verdict rather
        // than this clock read alone. The clock answers *how long* the band was held; it cannot
        // answer *how many credible fixes said so* or *whether the position actually went
        // anywhere*, and the parafarmacia FP cleared none of the three while this line, on its own,
        // was the only question the silent path ever asked. `DrivingEvidence` asks all three at
        // once, in the one place that can see them. A null verdict means the caller predates the
        // value object (replays, hand-built inputs): fall back to the clock, which is exactly the
        // behaviour those callers were written against.
        val sessionSawDriving = input.drivingEvidence?.mayConfirmSilently
            ?: sustainedDriveWitnessed(input.sustainedDrivingMs, config.sustainedDriveProofMs)

        // [DET-KINEMATIC-EGRESS-001] GPS-measured walk away from the frozen end-of-drive anchor.
        // Steps outrank it (they fire earlier); this is the mute-counter peer. Requires measured
        // in-session driving — the freeze alone can mature on a seeded post-trip session whose
        // anchor is wherever the user's body stood.
        val hasKinematicProof = input.hasKinematicEgress && input.hasEgressDisplacement && sessionSawDriving

        // Scooter mismatch guard: a CAR profile on a sustained slow trip looks like a moped —
        // suppress auto-confirm and leave it to the user prompt. [BUG-SCOOTER-001]
        // [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001] The `== CAR` here was not the
        // same question as the two above it: it asks whether a SLOW trip contradicts the profile,
        // which is false for a motorcycle for a reason that has nothing to do with parking or with
        // muscle. Spelled out as a constant, a type added tomorrow would silently have joined the
        // wrong side of whichever of the three it happened to resemble.
        val isMismatch = input.vehicleType?.slowTripContradictsProfile == true &&
            input.sessionDurationMs >= config.mismatchMinSessionDurationMs &&
            input.maxSpeedKmh <= config.mismatchMaxSpeedKmh

        // [DET-STEP-SPEED-GATE-001] The car is still ROLLING at the moment of decision (last fix
        // above the pedestrian ceiling). steps+egress is blind to instantaneous speed, so a phone
        // bouncing in stop-and-go traffic faked a confirm mid-route (FP Avenida de los Mástiles,
        // field 2026-07-12). A genuine park confirms while stationary or walking away — never
        // while rolling. Vetoes EVERY auto-confirm path; a walking user (< ceiling) is unaffected.
        val isRolling = input.lastSpeedMps > config.egressStepMaxSpeedMps

        val confirmNow = when {
            isRolling -> false
            isMismatch -> false
            // [DET-C-01] Egress is mandatory for every path.
            !input.hasEgressDisplacement -> false
            // [DET-EGRESS-PEDESTRIAN-CEILING-001] A vehicle-scale displacement is not a pedestrian
            // egress; it may only confirm through the kinematic path (pedestrian-band fixes), never
            // the step- or vehicle-exit-window paths.
            egressIsVehicular && !hasKinematicProof -> false
            hasStepsProof -> true
            hasKinematicProof -> true
            // [DET-NO-CLOCK-PLANTS-A-PIN-001] The weakest confirm path in the system: no steps, no
            // pedestrian-band fixes — just an AR EXIT and a clock running out with the position 18 m
            // from the anchor. A clock running out means *no further evidence arrived*, and that is
            // not evidence. This path now needs the session to have MEASURED a drive; the other two
            // carry their own physical proof of an egress and do not.
            windowElapsed && input.hadVehicleExit && sessionSawDriving -> true
            else -> false
        }

        // [DET-SOLID-001] Weak-evidence policy: the arm's only vehicle proof is an AR ENTER
        // (falsifiable by bus/taxi) AND the session's own stream never witnessed driving speed —
        // all confirm conditions hold, but the save is not trustworthy enough to be silent.
        // `verified_late` (the departure worker's post-arm upgrade) is weak for the same reason:
        // its verdict can rest on the same ENTER fall-through, and it must never override a
        // prompt the policy already chose (field incident 2026-07-04: the late upgrade silently
        // saved a park the user had been ASKED about and never answered). A session that
        // witnessed real driving confirms silently regardless.
        // [DET-UNVERIFIED-CONFIRM-001] `self_observed` is weaker still: NOTHING external vouched
        // for a drive — the session's own stream is the only witness, so without `sessionSawDriving`
        // (drive-proof gated) there is no witness at all. It was missing from this set, so a
        // sentry-wake arm whose FIRST cold-start fix carried a Doppler mirage (24.8 km/h at claimed
        // acc 2.9 m, phone in a pocket at the parked car) flipped `hasEverReachedDrivingSpeed`,
        // disarmed the anti-walking aborts, and 270 walking steps + egress silently re-pinned 7 m
        // from the previous park (field 2026-08-13, Calle Góndola). Doctrine: the event nominates,
        // only MEASURED movement confirms — no proven drive, no silent pin. Ask instead.
        // [DET-HANDOFF-NOT-MANUAL-001] `arrival_handoff` belongs in this set for the same reason
        // `self_observed` does — and it is the label that exposed the hole. The safety net's handoff
        // arms on a DEDUCED departure (the phone left the parked car's neighbourhood, which a walk
        // or a bicycle satisfies just as well) and it used to arrive stamped `manual`, i.e. wearing
        // the user's own word, which this policy trusts to save in silence. It never witnessed a
        // drive: without `sessionSawDriving` it asks.
        // [DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001] The set above USED to be spelled here as a
        // literal `setOf(LABEL_VERIFIED_ENTER, LABEL_VERIFIED_LATE, LABEL_SELF_OBSERVED,
        // LABEL_ARRIVAL_HANDOFF)` — the labels someone had already been burned by. That shape fails
        // OPEN: an arm absent from the list is strong by default, so every new arm confirms in
        // silence until the day it produces its first false positive and earns its line. It did.
        // Field 2026-08-29 23:56 (Redmi, pin `c6a57fad`): an `enter_at_car` arm — the log line for
        // which reads "waiting for ride proof" — was not on the list, so with `DriveProof.proven`
        // null for the entire session, 8 walking steps and 29 m of net displacement at 16 m accuracy
        // silently pinned "La Parafarmacia" at reliability 0.9.
        //
        // Now it asks `ArmEvidence` itself, where the classification is a `when` over the sealed
        // hierarchy that a new arm cannot skip, and the default is to ASK. The doctrine reads the
        // same either way — the event nominates, only MEASURED movement confirms — but only one of
        // the two spellings makes the compiler enforce it.
        //
        // [DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001] …and the `when` is asked
        // of the label ITSELF now. The line below used to call a label-side twin of that `when`,
        // hand-kept a few lines under it: same question, two answers, nothing binding them.
        val armCarriesItsOwnDrive = input.evidenceLabel?.confirmsSilentlyWithoutMeasuredDrive == true
        val weakEvidenceOnly = config.autoConfirmRequiresStrongEvidence &&
            !armCarriesItsOwnDrive &&
            !sessionSawDriving

        // [DET-SOLID-001][C2] Human-powered profiles never auto-confirm: a bike/scooter crossing
        // 18 km/h once (downhill, sprint) makes the whole session look like a car to every
        // speed-based signal, and the mismatch guard (CAR-only, ≥8 min) cannot help. Always ask.
        // MOTORCYCLE is a real motor vehicle with its own geofence — keeps auto-confirm.
        // [DET-BIKE-NOT-A-CAR-001] …and the same is true when the PROFILE says car but the RIDE was
        // a bicycle. The profile is a property of the garage, not of this trip.
        // [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001] The profile half of this OR was
        // the third place spelling out the same pair of types. It reads the type's own answer now;
        // the ride half stays exactly as it was, because the two questions are genuinely different.
        val humanPowered = input.vehicleType?.isHumanPowered == true || input.humanPoweredRide

        // [DET-DETECTION-PATH-IS-A-TYPE-001] The path is a TYPE now, and it carries its own
        // reliability. It used to be three string literals here and a fourth comparison below
        // (`if (pathLabel == "kinematic+egress")`), with everything unmatched falling to the
        // MAXIMUM — so a path added tomorrow would be born stamped 0.90 without its author ever
        // choosing that. Reliability is now the path's own answer.
        val path: DetectionPath = when {
            hasStepsProof -> DetectionPath.StepsEgress
            hasKinematicProof -> DetectionPath.KinematicEgress
            else -> DetectionPath.VehicleExitWindow
        }
        val pathLabel = path.label

        // [DET-ANCHOR-EGRESS-001][DET-CREDIBLE-DRIVE-001][DET-GAP-ANCHOR-001] An egress born away
        // from the anchor, an anchor captured at a walk-entered stop, or an anchor whose stop opened
        // through a GPS hole (rest unwitnessed — the anchor may be a drive-past point) invalidates
        // the ANCHOR, not the park: every proof may hold and the user probably DID park — just not
        // where the anchor says. Ask, never pin.
        //
        // [DET-PROMPT-STATES-ITS-REASON-001] This was one `||` and the five causes reached the trace
        // as a single anonymous `CONFIRM_DEGRADED_PROMPT`. Now it is an ORDERED first-match, so the
        // label is deterministic when several hold at once: two sessions of the same shape must
        // always report the same reason or the telemetry cannot be grouped. The order runs from the
        // claim about the WHOLE RIDE (nothing about this trip was a car) through the claim about the
        // ARM, down to the three that only doubt the anchor's position — widest doubt first, because
        // that is the one worth acting on.
        val promptReason: PromptReason? = when {
            humanPowered -> PromptReason.HUMAN_POWERED
            weakEvidenceOnly -> PromptReason.WEAK_EVIDENCE
            !input.egressBornAtAnchor -> PromptReason.EGRESS_NOT_AT_ANCHOR
            input.anchorWalkEntered -> PromptReason.ANCHOR_WALK_ENTERED
            input.anchorGapEntered -> PromptReason.ANCHOR_GAP_ENTERED
            else -> null
        }

        return when {
            // [DET-ASSERTION-OUTRANKS-INFERENCE-001] The user has ALREADY told us where the car is,
            // just now and just here. Every proof this evaluator can assemble is an inference, and
            // an inference never deposes an assertion — the rule the honest close has enforced
            // since [DET-WALK-FLOOR-001], applied to the lane that lacked it.
            //
            // Rejected, deliberately NOT Prompt. Field 2026-08-24 20:51, Oppo/Calle Fragua: 2 min
            // 53 s after the user answered "Sí" (pin `a9709e31`, acc 1,25 m), a sentry-wake session
            // with ONE fix above the driving bar out of 25 and 57 steps of walking away degraded to
            // `CONFIRM_DEGRADED_PROMPT/weak_evidence` and asked AGAIN. The user answered truthfully
            // — they WERE parked — and the app read that "Sí" as "pin it HERE", replacing a 1,25 m
            // pin with a 2,08 m one 14 m away, at the spot the walk started from. Asking is not
            // free: a question whose only possible answers both damage the state must not be asked.
            // Discarding the candidate keeps the user's pin and leaves the session alive; if it
            // later measures real driving the predicate stands down and a genuine re-park confirms.
            input.assertedPinBlocksRelocation -> ParkingDecision.Rejected
            confirmNow && promptReason != null -> ParkingDecision.Prompt(pathLabel, promptReason)
            confirmNow -> ParkingDecision.Confirmed(
                pathLabel = pathLabel,
                // Every branch of `path` is a live-confirm path, so this is never null — but the
                // fallback is spelled out rather than `!!`, and it is the SAFE value, not the max.
                reliability = path.confirmReliability(config) ?: config.reliabilityKinematicEgress,
            )
            // [DET-STEP-SPEED-GATE-001] Proofs present but the car is still rolling → this is a
            // traffic false positive (FP Avenida de los Mástiles), not a park. Reject the candidate
            // decisively rather than leave it open to re-confirm on the next moving fix.
            isRolling && (hasStepsProof || hasKinematicProof) -> ParkingDecision.Rejected
            // [DET-EGRESS-PEDESTRIAN-CEILING-001] Steps reached and the displacement floor cleared,
            // but the displacement OUTRAN the steps → the car drove off a drop-off / pick-up stop
            // (FP Calle Abeto, field 2026-07-18). Same class as the rolling FP: reject decisively so
            // a car merely leaving a brief stop never pins, and re-anchor when it actually parks.
            egressIsVehicular && !hasKinematicProof &&
                input.stepCount >= config.minStepsToConfirm && input.hasEgressDisplacement ->
                ParkingDecision.Rejected
            // [DET-HUMAN-POWERED-EARLY-CLOSE-001] The ride was made under human power and the stop
            // has matured: every branch above has already had its chance (a human-powered session
            // WITH all the proofs asks — that is the Prompt above, unchanged), so what remains is a
            // session that cannot ever confirm a car park. Emit the verdict now instead of idling
            // until a clock the answer never depended on. Deliberately placed after the confirm
            // branches and before [windowElapsed]: the observation window is a device for deciding
            // undecided candidates, and this one is decided.
            input.humanPoweredRide && input.restCertified -> ParkingDecision.CloseHumanPowered
            windowElapsed -> ParkingDecision.Rejected
            else -> ParkingDecision.Inconclusive
        }
    }
}
