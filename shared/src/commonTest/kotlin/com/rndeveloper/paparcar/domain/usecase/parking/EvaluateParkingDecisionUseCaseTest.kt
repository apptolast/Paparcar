package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.ArmLabel
import com.rndeveloper.paparcar.domain.detection.physics.DrivingEvidence
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.VehicleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit coverage for the pure candidate-phase decision. [DET-D-04]
 *
 * These exercise the wall-clock-driven paths (`windowElapsed`) that were impossible to drive
 * through the coordinator's real-Clock collect loop, plus the Prague replay (steps without egress).
 */
class EvaluateParkingDecisionUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val evaluate = EvaluateParkingDecisionUseCase(config)

    // window for the no-exit slow path (5 min) and exit path (2 min)
    private val slowWindow = config.confirmationObservationWindowMs
    private val exitWindow = config.vehicleExitObservationWindowMs

    private fun input(
        stepCount: Int = 0,
        hasEgressDisplacement: Boolean = false,
        hadVehicleExit: Boolean = false,
        elapsedSinceHighMs: Long = 0L,
        vehicleType: VehicleType? = VehicleType.CAR,
        sessionDurationMs: Long = 60_000L,
        maxSpeedKmh: Float = 60f,
        // [DET-MOTOR-PROOF-001] `sessionSawDriving` reads sustained band time now, not the peak.
        // Default derived from the peak so every pre-existing scenario keeps its intent: a test
        // that gave the session a real driving peak meant "the stream witnessed the drive".
        sustainedDrivingMs: Long = if (maxSpeedKmh >= 18f) 60_000L else 0L,
        // [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] Null on purpose by default: a null verdict makes
        // the policy fall back to `sustainedDrivingMs`, which is what every scenario written before
        // the value object was expressing. Tests about the verdict itself pass it explicitly.
        drivingEvidence: DrivingEvidence? = null,
        evidenceLabel: ArmLabel? = null,
        hasKinematicEgress: Boolean = false,
        lastSpeedMps: Float = 0f,
        egressExceedsWalkReach: Boolean = false,
        anchorGapEntered: Boolean = false,
        // [DET-PROMPT-STATES-ITS-REASON-001] The three remaining prompt causes, needed to prove each
        // one reaches the trace under its OWN name.
        egressBornAtAnchor: Boolean = true,
        anchorWalkEntered: Boolean = false,
        humanPoweredRide: Boolean = false,
        assertedPinBlocksRelocation: Boolean = false,
        // [DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001] The one field this helper never
        // mentioned, so every scenario here took it from the data class. The value is unchanged —
        // the fast lane genuinely runs with no stop behind it — but it is stated now, and the four
        // tests that care already say so through `.copy(restCertified = …)`.
        restCertified: Boolean = false,
    ) = ParkingDecisionInput(
        // Named, not positional: inserting a field into the middle of the data class used to bind
        // the wrong argument to the wrong parameter and still compile.
        stepCount = stepCount,
        hasEgressDisplacement = hasEgressDisplacement,
        hadVehicleExit = hadVehicleExit,
        elapsedSinceHighMs = elapsedSinceHighMs,
        vehicleType = vehicleType,
        sessionDurationMs = sessionDurationMs,
        maxSpeedKmh = maxSpeedKmh,
        sustainedDrivingMs = sustainedDrivingMs,
        drivingEvidence = drivingEvidence,
        evidenceLabel = evidenceLabel,
        hasKinematicEgress = hasKinematicEgress,
        lastSpeedMps = lastSpeedMps,
        egressExceedsWalkReach = egressExceedsWalkReach,
        anchorGapEntered = anchorGapEntered,
        egressBornAtAnchor = egressBornAtAnchor,
        anchorWalkEntered = anchorWalkEntered,
        humanPoweredRide = humanPoweredRide,
        assertedPinBlocksRelocation = assertedPinBlocksRelocation,
        restCertified = restCertified,
    )

    // ── [DET-ASSERTION-OUTRANKS-INFERENCE-001] The user's own word outranks every proof here ─

    @Test
    fun should_rejectTheCandidate_when_itWouldRelocateAPinTheUserAsserted() {
        // Field 2026-08-24 20:51, Oppo/Calle Fragua. The sentry-wake session holds a full
        // steps+egress proof (57 steps of walking away from the car) and a weak arm label, which
        // is exactly the shape that produced CONFIRM_DEGRADED_PROMPT/weak_evidence and a second
        // pin 14 m from the one the user had confirmed 2 min 53 s earlier. Neither a confirm nor a
        // prompt: asking a question whose every answer damages the state is itself the bug.
        val decision = evaluate(
            input(
                stepCount = config.minStepsToConfirm + 49,
                hasEgressDisplacement = true,
                maxSpeedKmh = 19f,
                sustainedDrivingMs = 0L, // one 5,33 m/s fix out of 25 is not a drive
                evidenceLabel = ArmLabel.SELF_OBSERVED,
                assertedPinBlocksRelocation = true,
            ),
        )
        assertIs<ParkingDecision.Rejected>(decision)
    }

    @Test
    fun should_promptAsBefore_when_theSameSessionHoldsNoAssertedPin() {
        // The control: identical evidence, no user-asserted pin in the way. The weak-evidence
        // prompt is still the right answer — this ticket removes ONE question, not the mechanism.
        val decision = evaluate(
            input(
                stepCount = config.minStepsToConfirm + 49,
                hasEgressDisplacement = true,
                maxSpeedKmh = 19f,
                sustainedDrivingMs = 0L,
                evidenceLabel = ArmLabel.SELF_OBSERVED,
                assertedPinBlocksRelocation = false,
            ),
        )
        val prompt = assertIs<ParkingDecision.Prompt>(decision)
        assertEquals(PromptReason.WEAK_EVIDENCE, prompt.reason)
    }

    // ── Kinematic egress: GPS-measured walk from the frozen anchor [DET-KINEMATIC-EGRESS-001] ─

    @Test
    fun should_confirm_via_kinematicEgress_when_sessionMeasuredDriving() {
        // Mute step counter (field 2026-07-11, Redmi): zero steps, but the frozen anchor watched
        // a sustained quality walk away. Confirms at its own reliability tier + path label.
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
            )
        )
        val confirmed = assertIs<ParkingDecision.Confirmed>(decision)
        assertEquals("kinematic+egress", confirmed.pathLabel)
        assertEquals(config.reliabilityKinematicEgress, confirmed.reliability, 0.0001f)
    }

    // ── Rolling veto: never confirm while the car is still moving [DET-STEP-SPEED-GATE-001] ─────

    @Test
    fun should_confirm_steps_egress_when_user_exits_fast_and_is_stationary_or_walking() {
        // A user who parks and jumps out in < 25 s must NOT become a false negative: at the moment
        // steps+egress fires they are stationary or walking away (lastSpeedMps below the pedestrian
        // ceiling), so the rolling veto does not apply. [caso "usuario rápido"]
        val stationary = evaluate(input(stepCount = 8, hasEgressDisplacement = true, lastSpeedMps = 0f))
        assertEquals("steps+egress", assertIs<ParkingDecision.Confirmed>(stationary).pathLabel)

        val walkingAway = evaluate(input(stepCount = 8, hasEgressDisplacement = true, lastSpeedMps = 1.4f))
        assertEquals("steps+egress", assertIs<ParkingDecision.Confirmed>(walkingAway).pathLabel)
    }

    @Test
    fun should_reject_steps_egress_when_car_is_still_rolling_in_traffic() {
        // FP Avenida de los Mástiles (field 2026-07-12): phantom steps + growing displacement while
        // the car crawled through traffic. steps+egress is blind to instantaneous speed, so it used
        // to CONFIRM mid-route. With the last fix above the pedestrian ceiling (4 m/s) the proofs are
        // present but the car is rolling → decisive Rejected, not a saved pin.
        val decision = evaluate(input(stepCount = 8, hasEgressDisplacement = true, lastSpeedMps = 4f))
        assertEquals(ParkingDecision.Rejected, decision)
    }

    @Test
    fun should_notConfirmViaKinematics_when_sessionNeverMeasuredDriving() {
        // A seeded arm whose stream never saw the trip: the freeze can mature on a pedestrian
        // stand, so kinematics alone must not confirm — stay inconclusive, keep asking.
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 4f,
            )
        )
        assertIs<ParkingDecision.Inconclusive>(decision)
    }

    @Test
    fun should_prompt_notConfirm_when_kinematicEgressOnHumanPoweredProfile() {
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
                vehicleType = VehicleType.BIKE,
            )
        )
        assertIs<ParkingDecision.Prompt>(decision)
    }

    @Test
    fun should_preferStepsPath_when_bothProofsPresent() {
        // Steps are ground truth — when they speak, the label and reliability are theirs.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
            )
        )
        val confirmed = assertIs<ParkingDecision.Confirmed>(decision)
        assertEquals("steps+egress", confirmed.pathLabel)
        assertEquals(config.reliabilityVehicleExit, confirmed.reliability, 0.0001f)
    }

    @Test
    fun should_notConfirmViaKinematics_without_egressDisplacement() {
        // DET-C-01: egress displacement is mandatory for every confirm path, this one included.
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = false,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
            )
        )
        assertIs<ParkingDecision.Inconclusive>(decision)
    }

    // ── Weak-evidence policy [DET-SOLID-001] ────────────────────────────────────

    @Test
    fun should_prompt_when_only_evidence_is_vehicle_enter_and_session_never_drove() {
        // ENTER-only arm (falsifiable by bus/taxi) + the session's own stream never saw driving
        // → all confirm conditions hold but the save must ask, not assert.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 4f,
                sessionDurationMs = 60_000L, // short session — mismatch guard (>=8 min) not in play
                evidenceLabel = ArmLabel.VERIFIED_ENTER,
            )
        )
        assertIs<ParkingDecision.Prompt>(decision)
        assertEquals("steps+egress", decision.pathLabel)
    }

    @Test
    fun should_confirm_when_enter_evidence_is_corroborated_by_observed_driving() {
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 30f, // the session itself witnessed the drive
                evidenceLabel = ArmLabel.VERIFIED_ENTER,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    @Test
    fun should_confirm_with_speed_evidence_even_without_observed_driving() {
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 4f,
                evidenceLabel = ArmLabel.VERIFIED_SPEED,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    // ── [DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001] ─────────────────────────────────────────────

    // ── [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] ────────────────────────────────────────────────

    @Test
    fun should_prompt_when_the_driving_evidence_is_weak_even_on_a_strong_arm() {
        // §6.0 of the redesign, closed: the path that plants the most pins — steps+egress — could
        // confirm in silence without consulting the drive proof at all, because `verified_speed` is
        // an arm the weak-evidence policy trusts. Now the verdict is asked as well, and the
        // parafarmacia numbers (1 credible fix, 72 m of excursion, no band time) cannot buy a pin
        // through ANY arm that does not itself carry a measurement.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 27.8f,
                sustainedDrivingMs = 60_000L, // the clock alone would have said "yes"
                drivingEvidence = DrivingEvidence.Weak(credibleFixes = 1, why = "field 2026-08-29"),
                evidenceLabel = ArmLabel.VERIFIED_ENTER,
            )
        )
        assertEquals(PromptReason.WEAK_EVIDENCE, assertIs<ParkingDecision.Prompt>(decision).reason)
    }

    @Test
    fun should_confirm_when_the_driving_evidence_is_measured() {
        // The other half of the same rule: the real 21:47 trip of that night.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 58f,
                sustainedDrivingMs = 0L, // …and the verdict OUTRANKS the raw clock read
                drivingEvidence = DrivingEvidence.Measured(
                    credibleFixes = 86,
                    excursionMeters = 3098.0,
                    sustainedBandMs = 30_001L,
                ),
                evidenceLabel = ArmLabel.VERIFIED_LATE,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    @Test
    fun should_prompt_when_enter_at_car_arm_and_session_never_drove() {
        // FIELD REPLAY — 2026-08-29 23:56, Redmi, pin `c6a57fad`, "La Parafarmacia".
        //
        // The session armed `enter_at_car` (AR IN_VEHICLE ENTER inside its own fence, delivered
        // 89 s late; the arm's own log line reads "waiting for ride proof"). The ride proof never
        // came: `DriveProof.proven` stayed null for the whole session — there is not one
        // `drive PROVEN` line between the arm and the confirm — and `hasEverMoved` printed `false`
        // on every state line. The net displacement from arm to pin was 29 m, from fixes carrying
        // 16 m of accuracy; the one fix that read 7.71 m/s had its 71.6 m undone 64.8 m backwards
        // 3.5 s later. Eight walking steps and an egress displacement then satisfied `steps+egress`
        // and it saved SILENTLY at reliability 0.9.
        //
        // It reached the confirm because the weak-evidence policy read a hand-kept list of weak
        // LABELS and `enter_at_car` was not on it. `verified_enter`, `verified_late`,
        // `self_observed` and `arrival_handoff` were — each added the day it produced its own field
        // FP. The set failed open, so the newest arm was strong by default.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 27.8f, // the mirage fix: 7.71 m/s, and the session peak because of it
                sustainedDrivingMs = 0L, // …but nothing ever HELD the band: no drive was proven
                evidenceLabel = ArmLabel.ENTER_AT_CAR,
            )
        )
        val prompt = assertIs<ParkingDecision.Prompt>(decision)
        assertEquals("steps+egress", prompt.pathLabel)
        assertEquals(PromptReason.WEAK_EVIDENCE, prompt.reason)
    }

    @Test
    fun should_confirm_when_enter_at_car_arm_and_session_measured_driving() {
        // The other half of the same rule, so the fix above cannot be mistaken for "enter_at_car
        // never confirms". The arm is identical; the session's own stream held the driving band.
        // That is measured movement, and measured movement confirms.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 60f,
                sustainedDrivingMs = 60_000L,
                evidenceLabel = ArmLabel.ENTER_AT_CAR,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    @Test
    fun should_confirm_when_inherited_drive_arm_even_without_observed_driving() {
        // [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] Regression guard for the arm that
        // carries a drive MEASURED by the session it superseded. The successor's own stream sees
        // only the last hop's manoeuvring speed, so gating it on `sessionSawDriving` would make the
        // one arm holding a real measurement the one that has to ask.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 4f,
                sustainedDrivingMs = 0L,
                evidenceLabel = ArmLabel.INHERITED_DRIVE,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    @Test
    fun should_confirm_when_manual_arm_even_without_observed_driving() {
        // [DET-ASSERTION-OUTRANKS-INFERENCE-001][DET-HANDOFF-NOT-MANUAL-001] The user's own word is
        // an assertion, not an inference, and this policy has always trusted it to save in silence.
        // Guarded explicitly because the fix replaced the label set that used to grant it by
        // omission — and a permission granted by omission is the thing that broke here.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 0f,
                sustainedDrivingMs = 0L,
                evidenceLabel = ArmLabel.MANUAL,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    @Test
    fun should_prompt_when_the_arm_label_is_unknown_and_session_never_drove() {
        // The predicate must fail CLOSED. The old one failed open: an unlisted label was strong by
        // default, which is the whole mechanism of the parafarmacia FP. An unrecognised (or absent)
        // arm is the least trustworthy thing this evaluator can be handed — it asks.
        //
        // [DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001] The unknown word cannot
        // reach the evaluator as a word any more, so the test asks at the door that now owns the
        // question: the parse answers `null`, and a null asks. Same doctrine, one boundary earlier.
        val unknown = ArmLabel.ofPersisted("some_arm_invented_after_this_ticket")
        assertEquals(null, unknown, "an unrecognised word must not resolve to any arm")
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 4f,
                sustainedDrivingMs = 0L,
                evidenceLabel = unknown,
            )
        )
        assertEquals(PromptReason.WEAK_EVIDENCE, assertIs<ParkingDecision.Prompt>(decision).reason)
    }

    @Test
    fun should_prompt_when_self_observed_arm_and_session_never_drove() {
        // [DET-UNVERIFIED-CONFIRM-001] Field 2026-08-13 (Calle Góndola): sentry-wake arm
        // (self_observed), the FIRST cold-start fix carried a Doppler mirage (24.8 km/h at claimed
        // acc 2.9 m) that disarmed the anti-walking aborts, then 270 walking steps + egress
        // silently re-pinned 7 m from the previous park. The drive-proof-gated speed statistic
        // stayed 0: nothing external vouched for a drive and the stream never proved one — the
        // save must ask, never pin.
        val decision = evaluate(
            input(
                stepCount = 270,
                hasEgressDisplacement = true,
                maxSpeedKmh = 0f,
                evidenceLabel = ArmLabel.SELF_OBSERVED,
            )
        )
        assertEquals("steps+egress", assertIs<ParkingDecision.Prompt>(decision).pathLabel)
    }

    @Test
    fun should_confirm_when_self_observed_arm_but_session_measured_driving() {
        // Same arm evidence with a PROVEN drive (drive-proof unlocked the speed statistic): the
        // session itself is a valid witness → the ordinary silent steps+egress confirm stands.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 30f,
                evidenceLabel = ArmLabel.SELF_OBSERVED,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    @Test
    fun should_prompt_when_arrival_handoff_arm_and_session_never_drove() {
        // [DET-HANDOFF-NOT-MANUAL-001] Field 2026-08-19 22:32: the safety net deduced a departure
        // (the phone had left the parked car's neighbourhood — on a BICYCLE) and handed the trip to
        // live detection stamped `manual`, i.e. wearing the user's own word, which this policy
        // trusts enough to save in silence. The handoff witnessed no drive: it must ask.
        val decision = evaluate(
            input(
                stepCount = 102,
                hasEgressDisplacement = true,
                maxSpeedKmh = 0f,
                evidenceLabel = ArmLabel.ARRIVAL_HANDOFF,
            )
        )
        assertEquals("steps+egress", assertIs<ParkingDecision.Prompt>(decision).pathLabel)
    }

    @Test
    fun should_confirm_when_arrival_handoff_arm_but_session_measured_driving() {
        // The handoff is weak evidence, not a veto: once the session's own stream holds the driving
        // band, the ordinary silent confirm stands — that is the normal case (the net was right and
        // the user really drove away).
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 30f,
                evidenceLabel = ArmLabel.ARRIVAL_HANDOFF,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    // ── Motor proof: sustained band time, not the peak [DET-MOTOR-PROOF-001] ────

    @Test
    fun should_prompt_when_weak_arm_and_driving_band_was_only_a_burst() {
        // Field 2026-08-18 20:32 (Oppo, session 1787077943062): a 6-min bicycle ride touched
        // 18,1 km/h on 2 of 124 fixes (~6 s in band) — the PEAK cleared the old threshold by
        // 0,1 km/h and steps+egress pinned the bike rack in silence with the car 540 m away.
        // Sustained band time is what a motor produces and a bicycle does not: same inputs now ask.
        val decision = evaluate(
            input(
                stepCount = 116,
                hasEgressDisplacement = true,
                maxSpeedKmh = 18.1f,
                sustainedDrivingMs = 6_000L,
                sessionDurationMs = 6 * 60_000L,
                evidenceLabel = ArmLabel.SELF_OBSERVED,
            )
        )
        assertEquals("steps+egress", assertIs<ParkingDecision.Prompt>(decision).pathLabel)
    }

    @Test
    fun should_confirm_when_weak_arm_but_band_held_across_one_sustained_hop() {
        // The worst legitimate car trace on file (Calle Gavia, skeletal MIUI stream): the whole
        // drive is ONE 36-s hop of 255 m. Sustained-band time must keep confirming it — the
        // threshold separates bursts (bikes: seconds) from holds (cars: tens of seconds).
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 25f,
                sustainedDrivingMs = 36_000L,
                evidenceLabel = ArmLabel.SELF_OBSERVED,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    @Test
    fun should_notConfirmViaKinematics_when_band_time_was_only_a_burst() {
        // The kinematic confirm reads the same gate: a burst-only session (whatever its peak)
        // has not witnessed a motor, so kinematics alone must keep asking.
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
                sustainedDrivingMs = 6_000L,
            )
        )
        assertIs<ParkingDecision.Inconclusive>(decision)
    }

    @Test
    fun should_prompt_when_ride_was_human_powered_even_with_sustained_band() {
        // [DET-MOTOR-PROOF-001] The pedal-cadence veto arrives through `humanPoweredRide`: a fast
        // bicycle CAN hold the band (the 2026-08-16 ride peaked at 38 km/h for 59 min), so when
        // the cadence signature spoke, no amount of band time may confirm silently.
        val decision = evaluate(
            input(stepCount = 8, hasEgressDisplacement = true, maxSpeedKmh = 38f)
                .copy(humanPoweredRide = true)
        )
        assertEquals("steps+egress", assertIs<ParkingDecision.Prompt>(decision).pathLabel)
    }

    // ── Early close: the verdict fires when the evidence is in [DET-HUMAN-POWERED-EARLY-CLOSE-001] ─

    @Test
    fun should_close_when_the_ride_was_human_powered_and_the_stop_matured() {
        // Field 2026-08-19 22:32: a bicycle ride home with no egress steps. Every branch above has
        // had its chance and none can ever fire on this session — so the answer is not "wait", it
        // is "end". The old behaviour idled here (Inconclusive) through three 5-min candidate
        // windows until a 15-min clock read this very same flag.
        val decision = evaluate(
            input(stepCount = 0, hasEgressDisplacement = false, maxSpeedKmh = 18f)
                .copy(humanPoweredRide = true, restCertified = true)
        )
        assertIs<ParkingDecision.CloseHumanPowered>(decision)
    }

    @Test
    fun should_not_close_a_human_powered_ride_before_a_stop_has_matured() {
        // The fast steps+egress lane runs with no stop behind it (`restCertified = false`): a
        // cyclist pausing at a light with a few phantom steps must not have the session closed
        // mid-ride. Rest is the only thing that turns "muscle-powered" into "the ride is over".
        val decision = evaluate(
            input(stepCount = 0, hasEgressDisplacement = false, maxSpeedKmh = 18f)
                .copy(humanPoweredRide = true, restCertified = false)
        )
        assertIs<ParkingDecision.Inconclusive>(decision)
    }

    @Test
    fun should_keep_asking_rather_than_closing_when_a_human_powered_ride_has_all_the_proofs() {
        // The close never steals a Prompt: with steps + egress the user is one tap from saving the
        // park, which beats an early close that would make them place the pin by hand.
        // [DET-BIKE-NOT-A-CAR-001] behaviour, unchanged.
        val decision = evaluate(
            input(stepCount = 8, hasEgressDisplacement = true, maxSpeedKmh = 38f)
                .copy(humanPoweredRide = true, restCertified = true)
        )
        assertEquals("steps+egress", assertIs<ParkingDecision.Prompt>(decision).pathLabel)
    }

    @Test
    fun should_not_close_a_motorised_session_at_a_matured_stop() {
        // The terminal close is exclusive to human-powered rides: a car waiting at a matured stop
        // keeps its observation window (Inconclusive → Rejected), which is how queue/traffic stops
        // are told apart from parks.
        val decision = evaluate(
            input(stepCount = 0, hasEgressDisplacement = false, maxSpeedKmh = 60f)
                .copy(restCertified = true)
        )
        assertIs<ParkingDecision.Inconclusive>(decision)
    }

    // ── Human-powered opt-out [DET-SOLID-001 C2] ────────────────────────────────

    @Test
    fun should_prompt_never_confirm_for_bike_profile() {
        // A bike crossing 18 km/h once looks like a car to every speed signal — always ask.
        val decision = evaluate(
            input(stepCount = 8, hasEgressDisplacement = true, vehicleType = VehicleType.BIKE, maxSpeedKmh = 26f)
        )
        assertIs<ParkingDecision.Prompt>(decision)
    }

    @Test
    fun should_prompt_never_confirm_for_scooter_profile() {
        val decision = evaluate(
            input(stepCount = 8, hasEgressDisplacement = true, vehicleType = VehicleType.SCOOTER, maxSpeedKmh = 26f)
        )
        assertIs<ParkingDecision.Prompt>(decision)
    }

    @Test
    fun should_keep_auto_confirm_for_motorcycle_profile() {
        val decision = evaluate(
            input(stepCount = 8, hasEgressDisplacement = true, vehicleType = VehicleType.MOTORCYCLE, maxSpeedKmh = 45f)
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    @Test
    fun should_confirm_enter_only_when_strong_evidence_flag_is_off() {
        val relaxed = EvaluateParkingDecisionUseCase(ParkingDetectionConfig(autoConfirmRequiresStrongEvidence = false))
        val decision = relaxed(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 4f,
                sessionDurationMs = 60_000L,
                evidenceLabel = ArmLabel.VERIFIED_ENTER,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    // ── Steps path ──────────────────────────────────────────────────────────────

    @Test
    fun should_confirm_via_steps_when_steps_and_egress_present() {
        val decision = evaluate(input(stepCount = 8, hasEgressDisplacement = true))
        assertIs<ParkingDecision.Confirmed>(decision)
        assertEquals("steps+egress", decision.pathLabel)
        assertEquals(config.reliabilityVehicleExit, decision.reliability)
    }

    @Test
    fun should_stay_inconclusive_when_steps_present_but_no_egress_and_window_open() {
        // The Prague false positive at the decision level: 8 steps from a bouncing phone but the
        // car never moved → no egress → must NOT confirm while the window is still open.
        val decision = evaluate(input(stepCount = 8, hasEgressDisplacement = false, elapsedSinceHighMs = 10_000L))
        assertEquals(ParkingDecision.Inconclusive, decision)
    }

    @Test
    fun should_reject_when_steps_present_but_no_egress_and_window_elapsed() {
        // Same Prague inputs, but the (no-exit) 5-min window has now expired → discard the candidate.
        val decision = evaluate(input(stepCount = 8, hasEgressDisplacement = false, elapsedSinceHighMs = slowWindow))
        assertEquals(ParkingDecision.Rejected, decision)
    }

    // ── Pedestrian egress ceiling [DET-EGRESS-PEDESTRIAN-CEILING-001] ────────────

    @Test
    fun should_reject_steps_egress_when_displacement_outran_the_steps() {
        // FP Calle Abeto (field 2026-07-18): stopped to pick a passenger up. ~26 incidental steps
        // were counted while parked and the car then drove ~500 m away — a displacement no pedestrian
        // could cover in those steps. steps+egress would have pinned a phantom park at the stop. The
        // egress floor is cleared but the ceiling is breached (a vehicle moved, not a person) →
        // decisive Rejected, and the real park after it is free to anchor.
        val decision = evaluate(
            input(stepCount = 26, hasEgressDisplacement = true, lastSpeedMps = 0f, egressExceedsWalkReach = true)
        )
        assertEquals(ParkingDecision.Rejected, decision)
    }

    @Test
    fun should_confirm_steps_egress_when_displacement_keeps_pace_with_steps() {
        // Regression guard: a genuine walk-away keeps pace with its own step count (outruns=false),
        // so the ceiling never touches a real park. Same inputs as the canonical steps+egress confirm.
        val decision = evaluate(
            input(stepCount = 26, hasEgressDisplacement = true, lastSpeedMps = 0f, egressExceedsWalkReach = false)
        )
        assertEquals("steps+egress", assertIs<ParkingDecision.Confirmed>(decision).pathLabel)
    }

    @Test
    fun should_still_confirm_via_kinematics_even_when_displacement_outruns_the_zero_steps() {
        // The kinematic path proves egress from pedestrian-BAND fixes (a departing car cannot make
        // them) and legitimately carries ~0 steps, so `outruns` is trivially true for it. The ceiling
        // must NOT veto it — only the step- and window-based paths.
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
                egressExceedsWalkReach = true,
            )
        )
        assertEquals("kinematic+egress", assertIs<ParkingDecision.Confirmed>(decision).pathLabel)
    }

    @Test
    fun should_not_confirm_via_exit_window_when_displacement_is_vehicular() {
        // The vehicle-exit + window path also leans on the egress floor; a car-scale displacement
        // must not confirm it either (the car simply drove off a stop). No kinematic proof → blocked.
        val decision = evaluate(
            input(
                hadVehicleExit = true,
                hasEgressDisplacement = true,
                elapsedSinceHighMs = exitWindow,
                egressExceedsWalkReach = true,
            )
        )
        assertEquals(ParkingDecision.Rejected, decision)
    }

    // ── Vehicle-exit + window path ───────────────────────────────────────────────

    @Test
    fun should_confirm_via_exit_window_when_exit_and_egress_and_window_elapsed() {
        val decision = evaluate(
            input(hadVehicleExit = true, hasEgressDisplacement = true, elapsedSinceHighMs = exitWindow),
        )
        assertIs<ParkingDecision.Confirmed>(decision)
        assertEquals("vehicleExit+window+egress", decision.pathLabel)
    }

    @Test
    fun should_reject_when_exit_and_window_elapsed_but_no_egress() {
        // A spurious AR exit during a long traffic stop: window elapses, but the user never walked
        // away → no egress → discard, never confirm. [DET-C-01]
        val decision = evaluate(
            input(hadVehicleExit = true, hasEgressDisplacement = false, elapsedSinceHighMs = exitWindow),
        )
        assertEquals(ParkingDecision.Rejected, decision)
    }

    @Test
    fun should_stay_inconclusive_when_exit_and_egress_but_window_not_elapsed() {
        // Exit + egress but no steps and the 2-min window hasn't elapsed yet → keep waiting.
        val decision = evaluate(
            input(hadVehicleExit = true, hasEgressDisplacement = true, elapsedSinceHighMs = exitWindow - 1),
        )
        assertEquals(ParkingDecision.Inconclusive, decision)
    }

    @Test
    fun should_select_longer_window_when_no_vehicle_exit() {
        // No exit → slow window (5 min). At 2.5 min (past the exit window but not the slow one) a
        // no-exit candidate with no proof is still Inconclusive, proving the window selection.
        val decision = evaluate(input(hadVehicleExit = false, hasEgressDisplacement = true, elapsedSinceHighMs = exitWindow + 1))
        assertEquals(ParkingDecision.Inconclusive, decision)
        assertTrue(exitWindow + 1 < slowWindow, "sanity: 2-min+1ms is still under the 5-min slow window")
    }

    // ── No-proof paths ───────────────────────────────────────────────────────────

    @Test
    fun should_stay_inconclusive_when_no_proof_and_window_open() {
        assertEquals(ParkingDecision.Inconclusive, evaluate(input(elapsedSinceHighMs = 1_000L)))
    }

    @Test
    fun should_reject_when_no_proof_and_window_elapsed() {
        assertEquals(ParkingDecision.Rejected, evaluate(input(elapsedSinceHighMs = slowWindow)))
    }

    // ── Scooter mismatch guard ───────────────────────────────────────────────────

    @Test
    fun should_suppress_confirm_when_car_profile_on_sustained_slow_trip() {
        // CAR profile, 10-min trip never above 20 km/h, with steps+egress that would otherwise
        // confirm → mismatch suppresses it. Window still open → Inconclusive.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                elapsedSinceHighMs = 1_000L,
                vehicleType = VehicleType.CAR,
                sessionDurationMs = 10 * 60_000L,
                maxSpeedKmh = 20f,
            ),
        )
        assertEquals(ParkingDecision.Inconclusive, decision)
    }

    // ── Gap-entered anchor: rest unwitnessed after a GPS hole [DET-GAP-ANCHOR-001] ──────────────

    @Test
    fun should_prompt_notPin_when_anchor_stop_opened_after_gps_hole() {
        // Field 2026-07-29, Redmi Av. Sanlúcar: last moving fix at 61 km/h, a 100-s MIUI hole, one
        // speed-0 fix mid-route, then the egress walk home — steps+egress all held, but the anchor
        // was a drive-past point 315 m before the real park. The proofs stand, the ANCHOR doesn't:
        // ask, never pin.
        val decision = evaluate(
            input(stepCount = 8, hasEgressDisplacement = true, anchorGapEntered = true),
        )
        assertEquals("steps+egress", assertIs<ParkingDecision.Prompt>(decision).pathLabel)
    }

    @Test
    fun should_prompt_via_kinematics_too_when_anchor_is_gap_entered() {
        // The kinematic path trusts the same anchor — a gap taint degrades it identically.
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
                anchorGapEntered = true,
            ),
        )
        assertEquals("kinematic+egress", assertIs<ParkingDecision.Prompt>(decision).pathLabel)
    }

    @Test
    fun should_not_suppress_when_non_car_vehicle_even_if_slow() {
        // Same slow trip but not a CAR → mismatch guard doesn't apply → steps+egress confirm.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                vehicleType = VehicleType.MOTORCYCLE,
                sessionDurationMs = 10 * 60_000L,
                maxSpeedKmh = 20f,
            ),
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    // ── Every prompt names its cause [DET-PROMPT-STATES-ITS-REASON-001] ───────────────────────
    //
    // Field 2026-08-20 23:56 (Oppo, session 1787263007358): a 63 km/h drive saved nothing and two of
    // its three verdicts were `CONFIRM_DEGRADED_PROMPT` with no way to tell WHICH of five causes
    // fired. The case only closed because the third verdict — the unattended timeout — names its
    // cause. `pathLabel` says how the park was proven; these say why it was not trusted.

    /** A confirm that would otherwise be silent, so each test below flips exactly one cause. */
    private fun confirmableInput(
        egressBornAtAnchor: Boolean = true,
        anchorWalkEntered: Boolean = false,
        anchorGapEntered: Boolean = false,
        humanPoweredRide: Boolean = false,
        evidenceLabel: ArmLabel? = ArmLabel.VERIFIED_SPEED,
        maxSpeedKmh: Float = 60f,
    ) = input(
        stepCount = 8,
        hasEgressDisplacement = true,
        maxSpeedKmh = maxSpeedKmh,
        evidenceLabel = evidenceLabel,
        egressBornAtAnchor = egressBornAtAnchor,
        anchorWalkEntered = anchorWalkEntered,
        anchorGapEntered = anchorGapEntered,
        humanPoweredRide = humanPoweredRide,
    )

    @Test
    fun should_name_the_cause_when_the_ride_was_human_powered() {
        val decision = evaluate(confirmableInput(humanPoweredRide = true))
        assertEquals(PromptReason.HUMAN_POWERED, assertIs<ParkingDecision.Prompt>(decision).reason)
    }

    @Test
    fun should_name_the_cause_when_the_profile_is_a_bicycle() {
        // The profile lane reaches the same reason as the measured one: both say "not a car".
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                vehicleType = VehicleType.BIKE,
                maxSpeedKmh = 60f,
                evidenceLabel = ArmLabel.VERIFIED_SPEED,
            ),
        )
        assertEquals(PromptReason.HUMAN_POWERED, assertIs<ParkingDecision.Prompt>(decision).reason)
    }

    @Test
    fun should_name_the_cause_when_the_arm_is_weak_and_nothing_witnessed_a_drive() {
        val decision = evaluate(
            confirmableInput(
                evidenceLabel = ArmLabel.SELF_OBSERVED,
                maxSpeedKmh = 0f,
            ),
        )
        assertEquals(PromptReason.WEAK_EVIDENCE, assertIs<ParkingDecision.Prompt>(decision).reason)
    }

    @Test
    fun should_name_the_cause_when_the_egress_was_not_born_at_the_anchor() {
        val decision = evaluate(confirmableInput(egressBornAtAnchor = false))
        assertEquals(
            PromptReason.EGRESS_NOT_AT_ANCHOR,
            assertIs<ParkingDecision.Prompt>(decision).reason,
        )
    }

    @Test
    fun should_name_the_cause_when_the_anchor_was_walk_entered() {
        val decision = evaluate(confirmableInput(anchorWalkEntered = true))
        assertEquals(
            PromptReason.ANCHOR_WALK_ENTERED,
            assertIs<ParkingDecision.Prompt>(decision).reason,
        )
    }

    @Test
    fun should_name_the_cause_when_the_anchor_stop_opened_through_a_gps_hole() {
        val decision = evaluate(confirmableInput(anchorGapEntered = true))
        assertEquals(
            PromptReason.ANCHOR_GAP_ENTERED,
            assertIs<ParkingDecision.Prompt>(decision).reason,
        )
    }

    @Test
    fun should_report_the_widest_doubt_first_when_several_causes_hold_at_once() {
        // Determinism is the point: the same shape must always report the same reason or the
        // telemetry cannot be grouped. Human-powered is a claim about the WHOLE RIDE, so it outranks
        // a weak arm, which in turn outranks the three that only doubt where the anchor sits.
        assertEquals(
            PromptReason.HUMAN_POWERED,
            assertIs<ParkingDecision.Prompt>(
                evaluate(
                    confirmableInput(
                        humanPoweredRide = true,
                        egressBornAtAnchor = false,
                        anchorWalkEntered = true,
                        anchorGapEntered = true,
                        evidenceLabel = ArmLabel.SELF_OBSERVED,
                        maxSpeedKmh = 0f,
                    ),
                ),
            ).reason,
        )
        assertEquals(
            PromptReason.WEAK_EVIDENCE,
            assertIs<ParkingDecision.Prompt>(
                evaluate(
                    confirmableInput(
                        egressBornAtAnchor = false,
                        anchorWalkEntered = true,
                        evidenceLabel = ArmLabel.SELF_OBSERVED,
                        maxSpeedKmh = 0f,
                    ),
                ),
            ).reason,
        )
    }

    @Test
    fun should_keep_every_cause_distinguishable_in_the_trace() {
        // The whole defect in one assertion: six producers, six different strings. If two ever
        // collapse again, a field trace stops being attributable.
        val keys = PromptReason.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "two causes share a trace key: $keys")
        assertTrue(keys.none { it.isBlank() }, "an empty key is an anonymous prompt again")
    }
}
