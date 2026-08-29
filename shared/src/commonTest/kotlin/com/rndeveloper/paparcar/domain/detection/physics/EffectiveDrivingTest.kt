package com.rndeveloper.paparcar.domain.detection.physics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-AR-FIRST-001 F3][07 §3.2] Characterization of the person/car precedence.
 *
 * **Why this file exists.** Until P1.9 this `when` lived inside a 700-line `update {}` block, and
 * its eight rows were only reachable through the whole coordinator. Its own comment says every row
 * won an argument with a real trip — and yet **nothing failed if two rows were swapped**. That is
 * the same hole `DET-PRECEDENCE-MUST-BE-TESTABLE-001` found one level up, and it is fixed the same
 * way: pin each ADJACENT PAIR with a case where both rows apply and they disagree.
 *
 * A test that would pass under any order is worth nothing, so every test below sets up exactly the
 * conflict between two neighbours and asserts which one wins.
 */
class EffectiveDrivingTest {

    /** All signals off, `isDriving` false: the "nothing is happening" baseline. */
    private fun verdict(
        isRealDrive: Boolean = false,
        realDriveCorroborated: Boolean = true,
        sustainedDeparture: Boolean = false,
        steplessDeparture: Boolean = false,
        anchorPinned: Boolean = false,
        corroboratedMuteHop: Boolean = false,
        stepsCounted: Int = 10,
        hasAnchor: Boolean = true,
        displacementOutrunsSteps: Boolean = false,
        isDriving: Boolean = false,
    ) = effectiveDriving(
        isRealDrive, realDriveCorroborated, sustainedDeparture, steplessDeparture, anchorPinned,
        corroboratedMuteHop, stepsCounted, hasAnchor, displacementOutrunsSteps, isDriving,
    )

    // ── The rows, each reachable on its own ───────────────────────────────────

    @Test
    fun should_say_car_when_the_fix_is_real_driving() {
        assertTrue(verdict(isRealDrive = true))
    }

    // ── [DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001] Rows 1a / 1b ──────────

    @Test
    fun should_say_person_when_a_lone_trip_speed_fix_meets_a_pinned_anchor() {
        // Field 2026-08-27, Calle del Vivero. The car really stopped (four fixes at 0,0 m/s, 2,2 m
        // accuracy) and the lock had already ignored 2,65 and 4,25 m/s. Then ONE fix at 6,45 m/s —
        // 37 m in 5 s, a person walking fast — cleared the anchor, and the next fix five seconds
        // later read 0,0 m/s twelve metres away. The pin ended 35 m from the car.
        assertFalse(verdict(isRealDrive = true, realDriveCorroborated = false, anchorPinned = true))
    }

    @Test
    fun should_say_car_when_the_second_trip_speed_fix_corroborates_the_first() {
        // The discriminating twin: the car genuinely pulls away, so the run continues and the
        // anchor clears one fix (~5 s) later than it used to. This is the whole cost of the guard.
        assertTrue(verdict(isRealDrive = true, realDriveCorroborated = true, anchorPinned = true))
    }

    @Test
    fun should_say_car_when_a_lone_trip_speed_fix_has_no_pinned_anchor_to_overturn() {
        // Row 1a: with nothing witnessed to overturn there is no reason to demand a run, and
        // demanding one would delay every ordinary departure.
        assertTrue(verdict(isRealDrive = true, realDriveCorroborated = false, anchorPinned = false))
    }

    @Test
    fun should_say_person_when_nothing_suggests_movement() {
        assertFalse(verdict())
    }

    // ── Adjacent pairs: the order IS the content ──────────────────────────────

    /**
     * **Row 1 beats row 4.** Real driving speed outranks a pinned anchor — otherwise a locked anchor
     * would survive the user actually driving away, and the pin would stay at the old spot forever.
     */
    @Test
    fun should_let_real_driving_beat_a_pinned_anchor() {
        assertTrue(verdict(isRealDrive = true, anchorPinned = true))
    }

    /**
     * **Row 2 beats row 4.** The sustained departure exists precisely for the case where the anchor
     * is pinned and no single fix is credible — field 2026-07-15, Enamorados. Under the opposite
     * order the OEM-starved drive never unfreezes the anchor and the pin lands 1.11 km away.
     */
    @Test
    fun should_let_a_sustained_departure_beat_a_pinned_anchor() {
        assertTrue(verdict(sustainedDeparture = true, anchorPinned = true))
    }

    /**
     * **Row 3 beats row 4.** The stepless departure is only ever evaluated with a pinned anchor, so
     * if the pinned row ran first this row would be dead code and the Bodegas Osborne creep
     * (2026-07-23: 160 m at 6–16 km/h) would never move the frozen anchor again.
     */
    @Test
    fun should_let_a_stepless_departure_beat_a_pinned_anchor() {
        assertTrue(verdict(steplessDeparture = true, anchorPinned = true, stepsCounted = 0))
    }

    /**
     * **Row 4 beats row 5.** A pinned anchor outranks even a corroborated hop: once egress steps
     * have locked the anchor the user is on foot, and only REAL driving may clear it
     * [ANCHOR-LOCK-001].
     */
    @Test
    fun should_let_a_pinned_anchor_beat_a_corroborated_hop() {
        assertFalse(verdict(anchorPinned = true, corroboratedMuteHop = true, stepsCounted = 0, isDriving = true))
    }

    /**
     * **Row 5 beats row 6, and this is the pair that matters most.** Read in isolation the two look
     * contradictory — "mute counter means CAR" against "mute counter means PERSON" — and they are
     * not: row 5 is the *measured* escape hatch of row 6.
     *
     * Swap them and the Galeote deceleration (2026-07-16: 23.7 m in 5 s against 9.9 m of joint
     * noise, the car rolling to the kerb) reads as a walk-in and taints a correct anchor.
     */
    @Test
    fun should_let_a_corroborated_hop_beat_the_mute_counter_rule() {
        assertTrue(verdict(corroboratedMuteHop = true, stepsCounted = 0, isDriving = true))
    }

    /**
     * **Row 6 stands when the hop is NOT corroborated** — the other half of the same pair. This is
     * what makes the Camelias-Oppo laundering impossible: with zero steps and no measured hop, a
     * declared ambiguous-band speed decides nothing, however fast it claims to be.
     */
    @Test
    fun should_say_person_when_the_mute_band_has_no_corroborated_hop() {
        assertFalse(verdict(corroboratedMuteHop = false, stepsCounted = 0, isDriving = true))
    }

    /**
     * **Row 7 beats row 8.** With an anchor and steps that cover the displacement, the ambiguous
     * band alone must not clear it — field 2026-07-10, Camelias: three steps at the kerb, and the
     * walk cleared the true anchor.
     */
    @Test
    fun should_let_covering_steps_beat_the_bare_ambiguous_band() {
        assertFalse(verdict(stepsCounted = 3, hasAnchor = true, displacementOutrunsSteps = false, isDriving = true))
    }

    /**
     * …and the same setup flips to CAR the moment the displacement OUTRUNS those steps: jam creep
     * with a couple of jiggle steps. Row 7's guard is the `!outruns`, not the anchor.
     */
    @Test
    fun should_say_car_when_the_displacement_outruns_the_counted_steps() {
        assertTrue(verdict(stepsCounted = 3, hasAnchor = true, displacementOutrunsSteps = true, isDriving = true))
    }

    /**
     * **Row 8** is the fall-through: no anchor to argue about, so the band decides on its own.
     */
    @Test
    fun should_believe_the_band_when_there_is_no_anchor_to_argue_about() {
        assertTrue(verdict(stepsCounted = 3, hasAnchor = false, isDriving = true))
        assertFalse(verdict(stepsCounted = 3, hasAnchor = false, isDriving = false))
    }
}
