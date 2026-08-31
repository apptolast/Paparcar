package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The twelve questions the anchor answers — eight of which had never been asked by a test.
 *
 * `AnchorPredicates` was carved out of the coordinator on the strength of one argument: a predicate
 * does not need to be a use case, because *"it is still directly testable, without the ceremony of
 * an injected class"* [DET-VERDICT-NOT-PREDICATE-001]. The exemption was collected; the price was
 * not paid. A census of the suite found `refinedParkLocation`, `isAnchorPinned`, `isAnchorLocked`,
 * `isEgressBornAtAnchor`, `sustainedDepartureFrom`, `hasKinematicEgressSignal`,
 * `movementOutrunsSteps` and `escapesAnchorEnvelope` with **zero** mentions across 1830 tests —
 * `isAnchorPinned` with twelve call sites in production and not one in a test.
 *
 * They are not incidental helpers. This is the anchor doctrine itself: *the anchor is LOCKED by
 * egress steps or FROZEN at the end of the drive, so the walk does not drag the pin*. And
 * [refinedParkLocation] is, literally, the function that decides **where the pin lands** — the
 * single output the user sees and the one that has burned in the field more than any other.
 *
 * ## What these tests are for
 *
 * Not line coverage. Each one pins a DISCRIMINATION the code makes on purpose and that a plausible
 * simplification would erase:
 *
 *  - the floor ([hasEgressDisplacement]) and the ceiling ([egressExceedsWalkReach]) are different
 *    questions on different scales, and one test shows a single fix answering them oppositely;
 *  - [escapesAnchorEnvelope] is [movementOutrunsSteps] **with the step credit removed**, and one
 *    test shows the same state and the same fix parting ways on exactly that;
 *  - [refinedParkLocation] has four separate reasons to keep the anchor, and merging any two of
 *    them would let the pin follow the pedestrian.
 *
 * Distances are built north of a fixed base, so a metre in the helper is a metre on the ground.
 *
 * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class AnchorPredicatesTest {

    private val config = ParkingDetectionConfig()

    /**
     * A point [metersNorth] of the base. Latitude only, so the haversine distance back to the base
     * is the number written at the call site — the thresholds under test are all in metres and the
     * tests are unreadable if the reader has to convert degrees in their head.
     */
    private fun point(
        metersNorth: Double = 0.0,
        accuracy: Float = 8f,
        at: Long = 1_000L,
        speed: Float = 0f,
    ) = GpsPoint(
        latitude = BASE_LAT + metersNorth / METERS_PER_DEGREE_LAT,
        longitude = BASE_LON,
        accuracy = accuracy,
        timestamp = at,
        speed = speed,
    )

    private fun stateWith(
        anchor: GpsPoint? = point(),
        steps: Int = 0,
        frozenByRest: Boolean = false,
        capturedAtStop: Long? = 0L,
        kinematicEgressFixes: Int = 0,
        egressBirth: EgressBirth? = null,
        stopWindowFixes: List<GpsPoint> = emptyList(),
    ) = DetectionSessionState(
        anchorTrust = AnchorTrust(
            anchor = anchor,
            capturedAtStop = capturedAtStop,
            frozenByRest = frozenByRest,
            kinematicEgressFixes = kinematicEgressFixes,
            egressBirth = egressBirth,
            stopWindowFixes = stopWindowFixes,
        ),
        egress = EgressEvidence(stepCount = steps),
    )

    // ── isAnchorLocked · the step proof ───────────────────────────────────────

    /**
     * No anchor means no lock, however many steps were counted. Fail-negative is the safe direction
     * under the asymmetric-error principle: a lock the session cannot point at is not a lock.
     */
    @Test
    fun should_not_lock_the_anchor_when_there_is_no_anchor_to_lock() {
        val state = stateWith(anchor = null, steps = 100)
        assertFalse(state.isAnchorLocked(config))
    }

    @Test
    fun should_not_lock_the_anchor_when_the_egress_steps_fall_short() {
        val state = stateWith(steps = config.anchorLockEgressSteps - 1)
        assertFalse(state.isAnchorLocked(config))
    }

    /** The threshold is inclusive — the boundary is where a `>` typo would live. */
    @Test
    fun should_lock_the_anchor_at_exactly_the_egress_step_threshold() {
        val state = stateWith(steps = config.anchorLockEgressSteps)
        assertTrue(state.isAnchorLocked(config))
    }

    // ── isAnchorPinned · locked OR frozen, treated identically ────────────────

    /**
     * Locked and frozen are independent proofs of the same fact ("the car rests HERE"), and the
     * whole point of [isAnchorPinned] is that no consumer gets to care which one it has. Four tests,
     * because a reading that collapses the `or` into either side alone still passes three of them.
     */
    @Test
    fun should_pin_the_anchor_when_the_egress_steps_locked_it() {
        val state = stateWith(steps = config.anchorLockEgressSteps, frozenByRest = false)
        assertTrue(state.isAnchorPinned(config))
    }

    @Test
    fun should_pin_the_anchor_when_the_end_of_drive_rest_froze_it_without_a_single_step() {
        val state = stateWith(steps = 0, frozenByRest = true)
        assertTrue(state.isAnchorPinned(config))
    }

    @Test
    fun should_not_pin_a_freeze_that_has_no_anchor_behind_it() {
        val state = stateWith(anchor = null, frozenByRest = true)
        assertFalse(state.isAnchorPinned(config))
    }

    @Test
    fun should_not_pin_the_anchor_when_neither_steps_nor_rest_witnessed_it() {
        val state = stateWith(steps = 0, frozenByRest = false)
        assertFalse(state.isAnchorPinned(config))
    }

    // ── hasKinematicEgressSignal · the walk GPS saw when the counter was mute ─

    @Test
    fun should_signal_kinematic_egress_when_a_frozen_anchor_is_followed_by_enough_walk_fixes() {
        val state = stateWith(
            frozenByRest = true,
            kinematicEgressFixes = config.kinematicEgressMinWalkFixes,
        )
        assertTrue(state.hasKinematicEgressSignal(config))
    }

    /**
     * The freeze is not decoration here. Pedestrian-band fixes that pile up while the anchor is
     * still open describe an approach, not an egress — the same fixes mean opposite things on either
     * side of the freeze.
     */
    @Test
    fun should_not_signal_kinematic_egress_while_the_anchor_is_not_frozen() {
        val state = stateWith(
            frozenByRest = false,
            kinematicEgressFixes = config.kinematicEgressMinWalkFixes * 3,
        )
        assertFalse(state.hasKinematicEgressSignal(config))
    }

    @Test
    fun should_not_signal_kinematic_egress_below_the_walk_fix_threshold() {
        val state = stateWith(
            frozenByRest = true,
            kinematicEgressFixes = config.kinematicEgressMinWalkFixes - 1,
        )
        assertFalse(state.hasKinematicEgressSignal(config))
    }

    @Test
    fun should_not_signal_kinematic_egress_without_an_anchor() {
        val state = stateWith(
            anchor = null,
            frozenByRest = true,
            kinematicEgressFixes = config.kinematicEgressMinWalkFixes,
        )
        assertFalse(state.hasKinematicEgressSignal(config))
    }

    // ── movementOutrunsSteps · person or car ─────────────────────────────────

    @Test
    fun should_not_claim_movement_outran_the_steps_without_an_anchor() {
        val state = stateWith(anchor = null, steps = 2)
        assertFalse(state.movementOutrunsSteps(point(metersNorth = 500.0), config))
    }

    /**
     * The jam-creep case the predicate exists for: two jiggle steps cannot explain 50 m, so physics
     * says a vehicle moved whatever the Doppler band claims.
     * Reach = 2 steps × 1.0 m + 8 m + 8 m + 18 m floor = 36 m.
     */
    @Test
    fun should_claim_movement_outran_the_steps_when_a_jiggle_count_cannot_explain_the_distance() {
        val state = stateWith(steps = 2)
        assertTrue(state.movementOutrunsSteps(point(metersNorth = 50.0), config))
    }

    /**
     * And the deliberate pro-person bias: a real walk-away keeps pace with its own count, so the
     * same 50 m backed by 60 steps (reach 94 m) is not a vehicle.
     */
    @Test
    fun should_not_claim_movement_outran_the_steps_when_the_walk_keeps_pace_with_its_own_count() {
        val state = stateWith(steps = 60)
        assertFalse(state.movementOutrunsSteps(point(metersNorth = 50.0), config))
    }

    // ── escapesAnchorEnvelope · the same physics with the step credit removed ─

    @Test
    fun should_not_escape_the_anchor_envelope_without_an_anchor() {
        val state = stateWith(anchor = null)
        assertFalse(state.escapesAnchorEnvelope(point(metersNorth = 500.0), config))
    }

    /**
     * A Doppler blip while standing AT the anchor can never qualify, whatever speed it declares:
     * 20 m does not escape 8 + 8 + 18.
     */
    @Test
    fun should_not_escape_the_anchor_envelope_from_inside_it_however_fast_the_fix_claims_to_be() {
        val state = stateWith()
        val blip = point(metersNorth = 20.0, speed = 14f)
        assertFalse(state.escapesAnchorEnvelope(blip, config))
    }

    @Test
    fun should_escape_the_anchor_envelope_once_the_position_has_measurably_left_it() {
        assertTrue(stateWith().escapesAnchorEnvelope(point(metersNorth = 50.0), config))
    }

    /**
     * **The discrimination, in one state.** Both predicates run the same reach arithmetic; the only
     * difference is that this one passes `steps = 0`. With 60 steps counted, one fix at 50 m is
     * simultaneously "not proof a car moved" (the walk covers it) and "provably outside the anchor"
     * (the position left, whoever moved it). Give [escapesAnchorEnvelope] the step credit and this
     * assert flips — which is the only way to notice that the two have quietly become one.
     */
    @Test
    fun should_escape_the_envelope_even_where_the_steps_still_explain_the_movement() {
        val state = stateWith(steps = 60)
        val fix = point(metersNorth = 50.0)
        assertFalse(state.movementOutrunsSteps(fix, config), "the walk explains the distance")
        assertTrue(state.escapesAnchorEnvelope(fix, config), "but the position has still left the anchor")
    }

    // ── The floor and the ceiling of the egress ──────────────────────────────

    @Test
    fun should_not_credit_egress_displacement_without_an_anchor() {
        val state = stateWith(anchor = null)
        assertFalse(state.hasEgressDisplacement(point(metersNorth = 500.0), config))
    }

    @Test
    fun should_not_exceed_the_walk_reach_without_an_anchor() {
        val state = stateWith(anchor = null, steps = 0)
        assertTrue(state.movementOutrunsSteps(point(metersNorth = 900.0), config).not())
        assertFalse(state.egressExceedsWalkReach(point(metersNorth = 900.0), config))
    }

    /**
     * **One fix, opposite answers, and both are right.** At 60 m the egress floor is cleared (the
     * user has left the car) and the pedestrian ceiling is nowhere near (this is not a car driving
     * off) — the tight per-fix reach says "outran the steps", the generous one says "still a walk".
     *
     * The generosity is field-calibrated, not timid: the Calle Gavia trace logged 68 m of real
     * walking on 8 steps, so a ceiling as tight as the floor would strand honest parks. Collapsing
     * the two floors into one constant is the tempting simplification this test refuses.
     */
    @Test
    fun should_read_one_walk_as_past_the_egress_floor_and_far_under_the_pedestrian_ceiling() {
        val state = stateWith(steps = 10)
        val fix = point(metersNorth = 60.0)

        assertTrue(state.hasEgressDisplacement(fix, config), "past the 18 m egress floor")
        assertTrue(state.movementOutrunsSteps(fix, config), "past the tight per-fix reach of 44 m")
        assertFalse(state.egressExceedsWalkReach(fix, config), "but nowhere near the 176 m ceiling")
    }

    /**
     * The ceiling's actual job: vehicle-scale displacement, which is the drop-off/pick-up shape that
     * planted a phantom park while a frozen anchor's steps tried to confirm one.
     */
    @Test
    fun should_exceed_the_walk_reach_at_vehicle_scale() {
        val state = stateWith(steps = 10)
        assertTrue(state.egressExceedsWalkReach(point(metersNorth = 500.0), config))
    }

    // ── sustainedDepartureFrom · the track, not the fix ──────────────────────

    @Test
    fun should_measure_no_sustained_departure_without_an_anchor() {
        val state = stateWith(anchor = null)
        assertNull(state.sustainedDepartureFrom(point(metersNorth = 600.0, speed = 12f), 60_000L, config))
    }

    /** No recorded stop means no clock to measure a rate against — the rate IS the corroboration. */
    @Test
    fun should_measure_no_sustained_departure_when_the_anchor_never_recorded_its_stop() {
        val state = stateWith(capturedAtStop = null)
        assertNull(state.sustainedDepartureFrom(point(metersNorth = 600.0, speed = 12f), 60_000L, config))
    }

    @Test
    fun should_measure_the_departure_when_the_track_ran_at_vehicle_pace_since_the_stop() {
        val state = stateWith(capturedAtStop = 0L)

        val departure = state.sustainedDepartureFrom(point(metersNorth = 600.0, speed = 12f), 60_000L, config)

        assertNotNull(departure)
        assertTrue(departure.distanceMeters > 500.0, "≈600 m from the anchor, got ${departure.distanceMeters}")
        assertTrue(departure.rateMps in 9.0..11.0, "≈10 m/s over 60 s, got ${departure.rateMps}")
    }

    /**
     * The walk home is the case this rate window exists to exclude: 200 m is well past the floor,
     * but spread over 200 s it averages 1 m/s, and no amount of distance turns that into a drive.
     */
    @Test
    fun should_measure_no_sustained_departure_when_the_walk_home_covers_the_distance_slowly() {
        val state = stateWith(capturedAtStop = 0L)
        assertNull(state.sustainedDepartureFrom(point(metersNorth = 200.0, speed = 3f), 200_000L, config))
    }

    /** A pedestrian-band fix never carries this verdict, however far the anchor sits. */
    @Test
    fun should_measure_no_sustained_departure_from_a_fix_that_is_not_itself_moving() {
        val state = stateWith(capturedAtStop = 0L)
        assertNull(state.sustainedDepartureFrom(point(metersNorth = 600.0, speed = 1f), 60_000L, config))
    }

    // ── judgeEgressBirth · the ceiling the displacement gate never had ────

    @Test
    fun should_reportNothingToJudge_when_there_is_no_anchor_to_judge_the_birth_against() {
        val state = stateWith(anchor = null, egressBirth = EgressBirth(point(metersNorth = 5_000.0), 3))
        assertEquals(EgressBirthJudgement.NOT_RECORDED, state.judgeEgressBirth(config))
    }

    @Test
    fun should_reportNothingToJudge_when_no_egress_birth_was_ever_recorded() {
        assertEquals(
            EgressBirthJudgement.NOT_RECORDED,
            stateWith(egressBirth = null).judgeEgressBirth(config),
        )
    }

    @Test
    fun should_accept_an_egress_born_within_walking_consistency_of_the_anchor() {
        val state = stateWith(egressBirth = EgressBirth(point(metersNorth = 20.0), stepCountAtBirth = 5))
        assertEquals(EgressBirthJudgement.BORN_AT_ANCHOR, state.judgeEgressBirth(config))
    }

    /**
     * The hard floor is what keeps a sparse stream honest: 100 m out with 3 steps blows past the
     * computed allowance (8 + 8 + 3 + 30 = 49 m) and is still accepted, because on a thin stream the
     * first stepped fix can legitimately land that far from the door.
     */
    @Test
    fun should_accept_a_sparse_but_honest_birth_on_the_hard_floor_rather_than_the_computed_allowance() {
        val state = stateWith(egressBirth = EgressBirth(point(metersNorth = 100.0), stepCountAtBirth = 3))
        assertEquals(EgressBirthJudgement.BORN_AT_ANCHOR, state.judgeEgressBirth(config))
    }

    /**
     * And what it rejects: a wrong-stop anchor sits hundreds of metres to kilometres from the birth,
     * which no floor and no step count explains.
     */
    @Test
    fun should_reject_an_egress_born_a_kilometre_from_the_anchor() {
        val state = stateWith(egressBirth = EgressBirth(point(metersNorth = 1_200.0), stepCountAtBirth = 3))
        assertEquals(EgressBirthJudgement.BORN_AWAY, state.judgeEgressBirth(config))
    }

    // ── refinedParkLocation · where the pin lands ────────────────────────────

    @Test
    fun should_pin_at_the_best_witnessed_fix_when_there_is_no_anchor() {
        val sharpest = point(metersNorth = 3.0, accuracy = 4f)
        val state = stateWith(anchor = null, stopWindowFixes = listOf(point(accuracy = 30f), sharpest))

        assertEquals(sharpest, state.refinedParkLocation(point(metersNorth = 99.0), config).location)
    }

    @Test
    fun should_pin_at_the_anchor_when_no_egress_birth_was_recorded() {
        val anchor = point()
        val state = stateWith(anchor = anchor, egressBirth = null)

        val refined = state.refinedParkLocation(point(metersNorth = 99.0), config)

        assertEquals(anchor, refined.location)
        assertNull(refined.note, "nothing moved, so there is nothing to explain")
    }

    /**
     * A kinematic birth is recorded off a fix that is already moving, so it is somewhere along the
     * walk rather than at the door. Letting it move the pin would break exactly the promise the
     * freeze makes to mute hardware: *pin at the frozen anchor, not along the walk*.
     */
    @Test
    fun should_pin_at_the_anchor_when_the_birth_counted_no_steps() {
        val anchor = point()
        val state = stateWith(
            anchor = anchor,
            egressBirth = EgressBirth(point(metersNorth = 10.0, accuracy = 4f), stepCountAtBirth = 0),
        )

        assertEquals(anchor, state.refinedParkLocation(point(), config).location)
    }

    /** A blurry birth is not a better witness than the anchor, whatever its step count. */
    @Test
    fun should_pin_at_the_anchor_when_the_birth_fix_is_not_pin_grade() {
        val anchor = point()
        val blurry = point(metersNorth = 10.0, accuracy = config.egressBirthRefineMaxAccuracyMeters + 1f)
        val state = stateWith(anchor = anchor, egressBirth = EgressBirth(blurry, stepCountAtBirth = 10))

        assertEquals(anchor, state.refinedParkLocation(point(), config).location)
    }

    /**
     * The bound that stops the pin from following the pedestrian: 40 m cannot be explained by
     * 10 steps plus both accuracy envelopes (10 + 8 + 5 = 23 m), so one of the two fixes is off in a
     * way walking does not account for and the conservative answer is to keep the anchor.
     */
    @Test
    fun should_pin_at_the_anchor_when_the_gap_to_the_birth_is_more_than_the_steps_explain() {
        val anchor = point()
        val farBirth = point(metersNorth = 40.0, accuracy = 5f)
        val state = stateWith(anchor = anchor, egressBirth = EgressBirth(farBirth, stepCountAtBirth = 10))

        assertEquals(anchor, state.refinedParkLocation(point(), config).location)
    }

    /**
     * And the case the rule was written for: the anchor was measured sitting IN the car (roof
     * multipath, optimistically sharp), the birth seconds later in open air at the car door. Bounded
     * by the steps, it is the better witness of where the car is — and it says so, because a pin
     * that moves and does not explain itself is the diagnostic hole this note fills.
     */
    @Test
    fun should_refine_the_pin_to_a_pin_grade_egress_birth_that_the_steps_explain() {
        val anchor = point()
        val birthFix = point(metersNorth = 15.0, accuracy = 5f)
        val state = stateWith(anchor = anchor, egressBirth = EgressBirth(birthFix, stepCountAtBirth = 10))

        val refined = state.refinedParkLocation(point(), config)

        assertEquals(birthFix, refined.location)
        assertNotNull(refined.note)
        assertTrue(
            refined.note.text.contains("pin refined to egress birth"),
            "the moved pin must explain itself: ${refined.note.text}",
        )
    }

    /** Refining onto the anchor itself moved nothing, so it says nothing. */
    @Test
    fun should_stay_silent_when_the_refined_pin_is_the_anchor_itself() {
        val anchor = point(accuracy = 5f)
        val state = stateWith(anchor = anchor, egressBirth = EgressBirth(anchor, stepCountAtBirth = 10))

        val refined = state.refinedParkLocation(point(), config)

        assertEquals(anchor, refined.location)
        assertNull(refined.note)
    }

    // ── anchorRestMs · how long the CAR has rested ───────────────────────────

    /**
     * Zero while the anchor is unpinned, and the distinction is the whole point: an open anchor has
     * a `capturedAtStop` like any other, so reading the timestamp directly would report a rest that
     * nothing witnessed.
     */
    @Test
    fun should_report_no_car_rest_while_the_anchor_is_not_pinned() {
        val state = stateWith(steps = 0, frozenByRest = false, capturedAtStop = 10_000L)
        assertEquals(0L, state.anchorRestMs(now = 70_000L, config = config))
    }

    @Test
    fun should_report_the_rest_of_a_pinned_anchor_since_its_own_stop() {
        val state = stateWith(frozenByRest = true, capturedAtStop = 10_000L)
        assertEquals(60_000L, state.anchorRestMs(now = 70_000L, config = config))
    }

    private companion object {
        const val BASE_LAT = 36.6119
        const val BASE_LON = -6.2805

        /** Metres per degree of latitude — the tests are written in metres and read in metres. */
        const val METERS_PER_DEGREE_LAT = 111_320.0
    }
}
