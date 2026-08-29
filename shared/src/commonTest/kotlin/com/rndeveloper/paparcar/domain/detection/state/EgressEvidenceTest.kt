package com.rndeveloper.paparcar.domain.detection.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [09 §5] The transitions of the third sub-state.
 *
 * The counting gate and the cadence reading were reachable only through the coordinator's step
 * collector, and the three RESET rules were interleaved line by line inside a 40-field `copy` where
 * they read as one rule applied consistently. They are three, and the differences decide field
 * cases.
 */
class EgressEvidenceTest {

    private val ceiling = 2.5f          // egressStepMaxSpeedMps
    private val motorBar = 11f          // motorProofSpeedMps
    private val freshness = 5_000L      // pedalCadenceFixFreshnessMs

    private fun EgressEvidence.step(
        stepAtMs: Long = 10_000L,
        driveAuthorized: Boolean = true,
        stopped: Boolean = false,
        anchorPresent: Boolean = false,
        anchorPinned: Boolean = false,
        speed: Float = 0f,
        credible: Boolean = true,
        lastFixSeenAtMs: Long = 9_000L,
    ) = onStepEvent(
        stepAtMs = stepAtMs,
        driveAuthorized = driveAuthorized,
        stopped = stopped,
        anchorPresent = anchorPresent,
        anchorPinned = anchorPinned,
        lastFixSpeedMps = speed,
        lastFixCredible = credible,
        lastFixSeenAtMs = lastFixSeenAtMs,
        pedestrianCeilingMps = ceiling,
        motorProofSpeedMps = motorBar,
        cadenceFixFreshnessMs = freshness,
    )

    // ── The triple counting gate ──────────────────────────────────────────────

    /** Before any drive every step counts — that is what feeds the false-ENTER abort guard. */
    @Test
    fun should_count_every_step_before_the_session_has_driven() {
        assertEquals(1, EgressEvidence().step(driveAuthorized = false, speed = 30f).stepCount)
    }

    /** Stopped post-drive: the canonical "user got out" signal. */
    @Test
    fun should_count_steps_while_stopped_after_a_drive() {
        assertEquals(1, EgressEvidence().step(stopped = true).stepCount)
    }

    /**
     * [DET-AR-FIRST-001 F3] Moving with an ANCHOR set at pedestrian pace: those steps ARE the egress
     * walk. Gating them on the stop clock starved the count the moment the walk began (field
     * 2026-07-10, Camelias: three steps at the kerb, then zero for the whole walk into the house).
     */
    @Test
    fun should_count_the_egress_walk_while_moving_at_pedestrian_pace_with_an_anchor() {
        assertEquals(1, EgressEvidence().step(anchorPresent = true, speed = 1.4f).stepCount)
    }

    /**
     * [DET-STEP-SPEED-GATE-001] …and NOT at driving pace. A car crawling in stop-and-go traffic
     * keeps the anchor set while moving fast, and its vibration used to accumulate phantom steps
     * that faked steps+egress (field 2026-07-12, Avenida de los Mástiles).
     */
    @Test
    fun should_refuse_the_step_when_the_anchor_is_set_but_the_position_moves_at_driving_pace() {
        assertEquals(0, EgressEvidence().step(anchorPresent = true, speed = 8f).stepCount)
    }

    /** No anchor, no stop, already drove: nothing to attribute the step to. */
    @Test
    fun should_refuse_the_step_when_nothing_makes_it_an_egress() {
        assertEquals(0, EgressEvidence().step(speed = 8f).stepCount)
    }

    /**
     * Counted or gated, EVERY event proves the sensor alive, feeds the raw odometer and interrupts a
     * stepless-departure run — a person is moving their feet, so a pinned anchor's movement may
     * still be them. [DET-CONFIRM-FRESHNESS-001]
     */
    @Test
    fun should_witness_the_sensor_even_when_the_step_does_not_count() {
        val after = EgressEvidence(pinnedSteplessMovingFixes = 4).step(speed = 8f)
        assertEquals(0, after.stepCount, "the gate refused it")
        assertTrue(after.sensorAlive)
        assertEquals(1, after.stepEventsSinceDriving)
        assertEquals(0, after.pinnedSteplessMovingFixes)
    }

    // ── The pedal cadence, and its four bounds ────────────────────────────────

    /** [DET-MOTOR-PROOF-001] Feet in rhythm while the position moves faster than any walk. */
    @Test
    fun should_read_a_step_beside_a_fast_fresh_fix_as_pedalling() {
        val after = EgressEvidence().step(speed = 4f)
        assertEquals(1, after.fastMotionStepEvents)
        assertEquals(1, after.fastMotionStepFixes)
    }

    /**
     * [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] The bound that was missing: above the motor bar the
     * concurrency proves the OPPOSITE of pedalling. A phantom step next to a motorway fix was read
     * as pedalling at 131 km/h.
     */
    @Test
    fun should_refuse_the_cadence_above_the_speed_no_bicycle_reaches() {
        assertEquals(0, EgressEvidence().step(speed = 36f).fastMotionStepEvents)
    }

    /**
     * [DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001] The bound that had no notion of WHEN. Once the
     * anchor is pinned the session has already witnessed where the car came to rest, so feet next to
     * a fast fix are the user walking away on a noisy stream — the expected shape of an egress.
     * Field 2026-08-22, Góndola→Camelias: a 75 km/h car trip latched the cadence 36 s AFTER the
     * anchor froze and was judged a bicycle.
     */
    @Test
    fun should_refuse_the_cadence_once_the_anchor_is_pinned() {
        assertEquals(0, EgressEvidence().step(anchorPinned = true, speed = 4f).fastMotionStepEvents)
    }

    /** A step judged against a stale fix is judged against nothing. */
    @Test
    fun should_refuse_the_cadence_when_the_fix_is_too_old_to_speak() {
        assertEquals(
            0,
            EgressEvidence().step(stepAtMs = 20_000L, lastFixSeenAtMs = 9_000L, speed = 4f).fastMotionStepEvents,
        )
    }

    /** …and against an incredible one. */
    @Test
    fun should_refuse_the_cadence_when_the_fix_does_not_know_where_it_is() {
        assertEquals(0, EgressEvidence().step(credible = false, speed = 4f).fastMotionStepEvents)
    }

    /** One fix's burst can be one pothole: distinct FIXES are counted separately from events. */
    @Test
    fun should_credit_one_fix_once_however_many_steps_it_carries() {
        val after = EgressEvidence().step(speed = 4f).step(stepAtMs = 10_100L, speed = 4f)
        assertEquals(2, after.fastMotionStepEvents)
        assertEquals(1, after.fastMotionStepFixes)
    }

    // ── A verdict may not destroy a measurement ──────────────────────────────

    /**
     * [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] A discarded candidate moves the freshness line;
     * the count stands, because the anchor lock, the walk-reach ceilings and the 15-minute
     * unattended verdict all read it and need the whole truth.
     */
    @Test
    fun should_move_the_freshness_line_and_keep_the_count_when_a_candidate_is_discarded() {
        val after = EgressEvidence(stepCount = 12).candidateDiscarded()
        assertEquals(12, after.stepCount)
        assertEquals(12, after.stepsAtLastDiscard)
        assertEquals(0, after.freshStepCount)
        assertEquals(3, after.copy(stepCount = 15).freshStepCount, "later steps confirm again")
    }

    // ── The three reset rules that are not one rule ──────────────────────────

    /** Measured driving ends the previous egress outright, so jam jiggle cannot cross stops. */
    @Test
    fun should_clear_the_whole_egress_when_the_car_provably_drove() {
        val before = EgressEvidence(
            stepCount = 9, stepsAtLastDiscard = 4, stepEventsSinceDriving = 9,
            vehicleExitHint = true, pinnedSteplessMovingFixes = 3,
        )
        val after = before.onFix(
            effectiveDriving = true, repositionBurst = false, anchorCleared = true, steplessMovingFixes = 7,
        )
        assertEquals(0, after.stepCount)
        assertEquals(0, after.stepsAtLastDiscard)
        assertEquals(0, after.stepEventsSinceDriving)
        assertFalse(after.vehicleExitHint)
        assertEquals(0, after.pinnedSteplessMovingFixes)
    }

    /**
     * **The distinction this file exists for.** A reposition burst is a resolved CAR movement for
     * the RAW odometer — it measures "since the last car movement" — but NOT for the counter: the
     * user shuffled the car, they did not drive away, so the egress steps they already took still
     * stand. Collapsing the two rules loses a park every time someone re-parks a few metres.
     */
    @Test
    fun should_zero_only_the_raw_odometer_when_the_car_was_merely_repositioned() {
        val before = EgressEvidence(stepCount = 9, stepsAtLastDiscard = 4, stepEventsSinceDriving = 9, vehicleExitHint = true)
        val after = before.onFix(
            effectiveDriving = false, repositionBurst = true, anchorCleared = false, steplessMovingFixes = 2,
        )
        assertEquals(9, after.stepCount)
        assertEquals(4, after.stepsAtLastDiscard)
        assertTrue(after.vehicleExitHint)
        assertEquals(0, after.stepEventsSinceDriving)
    }

    /** The stepless run belongs to the ANCHOR, not to the drive: only the anchor going away clears it. */
    @Test
    fun should_tie_the_stepless_run_to_the_anchor_and_not_to_the_drive() {
        val carrying = EgressEvidence().onFix(
            effectiveDriving = false, repositionBurst = false, anchorCleared = false, steplessMovingFixes = 5,
        )
        assertEquals(5, carrying.pinnedSteplessMovingFixes)

        val cleared = carrying.onFix(
            effectiveDriving = false, repositionBurst = false, anchorCleared = true, steplessMovingFixes = 5,
        )
        assertEquals(0, cleared.pinnedSteplessMovingFixes)
    }

    // ── The activity recogniser ──────────────────────────────────────────────

    /**
     * [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] An EXIT is evidence of the BOARDING it must have
     * followed, so it supersedes a cycling stamp — but only FORWARD. AR delivers transitions out of
     * wall-clock order, and an EXIT stamped older than a boarding already known would AGE the
     * evidence.
     */
    @Test
    fun should_never_let_a_late_delivered_exit_age_the_boarding_it_already_knows_about() {
        val known = EgressEvidence().onVehicleRide(atMs = 9_000L)
        val after = known.onVehicleExit(atMs = 4_000L)
        assertTrue(after.vehicleExitHint)
        assertEquals(9_000L, after.vehicleRideAtMs)
    }

    /** …and a genuinely later EXIT does move it. */
    @Test
    fun should_move_the_boarding_stamp_forward_when_the_exit_is_newer() {
        assertEquals(12_000L, EgressEvidence().onVehicleRide(9_000L).onVehicleExit(12_000L).vehicleRideAtMs)
    }
}
