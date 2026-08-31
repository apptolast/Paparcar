package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [09 §5] The transitions of the fifth sub-state.
 *
 * What is pinned here is the thing the whole step exists for: the capture **seals as a whole**.
 * Before P2.5 its five witnesses each carried their own copy of the rebind condition, so the
 * property "these five describe the SAME instant" was true by five separate coincidences.
 */
class AnchorTrustTest {

    private fun point(north: Double = 0.0, accuracy: Float = 8f, at: Long = 1_000L) =
        GpsPoint(36.6119 + north, -6.2805, accuracy, at, 0f)

    private val firstStop = 10_000L
    private val laterStop = 90_000L

    private fun AnchorTrust.stoppedAt(
        stop: Long,
        anchor: GpsPoint?,
        gapMs: Long = 0L,
        stepEvents: Int = 0,
        sensorAlive: Boolean = false,
        frozen: Boolean = false,
    ) = onStoppedFix(
        stopStartedAt = stop,
        stopWindowFixes = listOfNotNull(anchor),
        newAnchor = anchor,
        stopGapMs = gapMs,
        frozen = frozen,
        stepEventsSinceDriving = stepEvents,
        sensorAlive = sensorAlive,
    )

    // ── The seal is atomic ────────────────────────────────────────────────────

    /** Binding to a stop seals all five witnesses of the walk-in that led into it, at once. */
    @Test
    fun should_seal_every_witness_of_the_walk_in_when_the_anchor_binds_to_a_stop() {
        val origin = point(north = -0.0005)
        val anchor = point()
        val before = AnchorTrust(walkIn = WalkIn(fixesSinceDriving = 7, runOriginFix = origin))

        val after = before.stoppedAt(firstStop, anchor, gapMs = 42_000L, stepEvents = 9, sensorAlive = true)

        assertEquals(firstStop, after.capturedAtStop)
        assertEquals(7, after.capture.walkFixes)
        assertEquals(9, after.capture.stepEvents)
        assertTrue(after.capture.sawSteps)
        assertEquals(42_000L, after.capture.gapMs)
        assertTrue(after.capture.gapEntered)
        assertTrue(after.capture.walkInSpanMeters > 0.0, "the measured walk-in span is sealed too")
    }

    /**
     * **A same-stop refinement must NOT re-seal.** A sharper fix arriving right after the door slam
     * binds a new anchor instance to the SAME stop, and the taints belong to the stop rather than to
     * the sharpness of the fix. Re-sealing here would let a walk-entered taint evaporate the moment
     * GPS improved. [ANCHOR-LOCK-001]
     */
    @Test
    fun should_keep_the_original_capture_when_a_sharper_fix_refines_the_same_stop() {
        val sealed = AnchorTrust(walkIn = WalkIn(fixesSinceDriving = 7))
            .stoppedAt(firstStop, point(accuracy = 20f), stepEvents = 9, sensorAlive = true)

        val refined = sealed.copy(walkIn = WalkIn(fixesSinceDriving = 0))
            .stoppedAt(firstStop, point(accuracy = 4f), stepEvents = 0, sensorAlive = false)

        assertEquals(4f, refined.anchor?.accuracy, "the sharper fix is the anchor now")
        assertEquals(7, refined.capture.walkFixes, "…but the capture is still the stop's")
        assertEquals(9, refined.capture.stepEvents)
        assertTrue(refined.capture.sawSteps)
    }

    /** …and binding to a LATER stop re-seals every witness against the new one. */
    @Test
    fun should_reseal_every_witness_when_the_anchor_binds_to_a_later_stop() {
        val sealed = AnchorTrust(walkIn = WalkIn(fixesSinceDriving = 7))
            .stoppedAt(firstStop, point(), stepEvents = 9, sensorAlive = true)

        val rebound = sealed.copy(walkIn = WalkIn(fixesSinceDriving = 1))
            .stoppedAt(laterStop, point(north = 0.002), stepEvents = 0, sensorAlive = false)

        assertEquals(laterStop, rebound.capturedAtStop)
        assertEquals(1, rebound.capture.walkFixes)
        assertEquals(0, rebound.capture.stepEvents)
        assertFalse(rebound.capture.sawSteps)
    }

    /** The freeze latches: a stop that matured stays frozen through later stopped fixes. */
    @Test
    fun should_latch_the_freeze_once_a_stop_has_matured() {
        val frozen = AnchorTrust().stoppedAt(firstStop, point(), frozen = true)
        assertTrue(frozen.stoppedAt(firstStop, point(), frozen = false).frozenByRest)
    }

    // ── The stop ends ─────────────────────────────────────────────────────────

    /**
     * ⚠️ **The clearing asymmetry, preserved on purpose.** The two MEASUREMENTS die with the anchor;
     * the three witnesses of the walk-in survive it, and `isAnchorWalkEntered` can still read them
     * with no anchor in sight. Zeroing them would flip a walk-entered verdict to "clean" exactly
     * where the anchor is missing — the case the asymmetric-failure doctrine treats with most
     * suspicion — so a move does not get to make that call.
     */
    @Test
    fun should_keep_the_walk_in_witnesses_when_the_anchor_is_cleared() {
        val sealed = AnchorTrust(walkIn = WalkIn(fixesSinceDriving = 7))
            .stoppedAt(firstStop, point(), gapMs = 42_000L, stepEvents = 9, sensorAlive = true)

        val cleared = sealed.onMovingFix(
            anchorCleared = true, carMovement = true, fix = point(north = 0.01),
            repositionStreak = 0, realDriveStreak = 0, kinematicEgressFixes = 0,
        )

        assertNull(cleared.anchor)
        assertNull(cleared.capturedAtStop)
        assertFalse(cleared.frozenByRest)
        // Died with the anchor they measured…
        assertEquals(0.0, cleared.capture.walkInSpanMeters)
        assertEquals(0L, cleared.capture.gapMs)
        // …and the three that survive it today still do.
        assertEquals(7, cleared.capture.walkFixes)
        assertEquals(9, cleared.capture.stepEvents)
        assertTrue(cleared.capture.sawSteps)
    }

    /** A walking fix leaves the anchor alone and advances the "entered on foot" odometer. */
    @Test
    fun should_advance_the_walk_in_odometer_without_touching_the_anchor() {
        val anchored = AnchorTrust().stoppedAt(firstStop, point())
        val walked = anchored.onMovingFix(
            anchorCleared = false, carMovement = false, fix = point(north = 0.0002),
            repositionStreak = 0, realDriveStreak = 0, kinematicEgressFixes = 1,
        )
        assertEquals(anchored.anchor, walked.anchor)
        assertEquals(1, walked.walkIn.fixesSinceDriving)
        assertEquals(point(north = 0.0002), walked.walkIn.runOriginFix, "the run's first fix marks its origin")
        assertNull(walked.stopStartedAt, "the stop is over either way")
    }

    /**
     * [DET-ANCHOR-FREEZE-001] A resolved CAR movement zeroes the odometer AND drops the run origin —
     * a reposition maneuver counts as one, because the odometer measures "since the last car
     * movement", even though the user did not drive away.
     */
    @Test
    fun should_zero_the_walk_in_odometer_on_a_reposition_as_well_as_a_drive() {
        val walking = AnchorTrust(walkIn = WalkIn(fixesSinceDriving = 5, runOriginFix = point()))
        val after = walking.onMovingFix(
            anchorCleared = false, carMovement = true, fix = point(north = 0.001),
            repositionStreak = 2, realDriveStreak = 0, kinematicEgressFixes = 0,
        )
        assertEquals(0, after.walkIn.fixesSinceDriving)
        assertNull(after.walkIn.runOriginFix)
    }

    // ── The egress birth ──────────────────────────────────────────────────────

    private fun AnchorTrust.birth(
        fix: GpsPoint,
        stepCount: Int,
        kinematicEgressFixes: Int = 0,
        acceptsKinematicWitness: Boolean = false,
        anchorCleared: Boolean = false,
    ) = withEgressBirth(
        fix = fix,
        anchorCleared = anchorCleared,
        stepCount = stepCount,
        kinematicEgressFixes = kinematicEgressFixes,
        acceptsKinematicWitness = acceptsKinematicWitness,
        birthWindowMs = 30_000L,
        refineMaxExtraSteps = 2,
    )

    private val anchored = AnchorTrust().stoppedAt(firstStop, point())

    /** The birth is recorded once, with the steps already counted at that instant. */
    @Test
    fun should_record_the_egress_birth_with_the_steps_counted_at_that_instant() {
        val born = anchored.birth(point(at = 5_000L), stepCount = 3)
        assertEquals(3, born.egressBirth?.stepCountAtBirth)
        assertEquals(5_000L, born.egressBirth?.originFix?.timestamp)
    }

    /** No anchor, no birth: there is nothing for the walk to have started FROM. */
    @Test
    fun should_refuse_a_birth_with_no_anchor_to_be_born_at() {
        assertNull(AnchorTrust().birth(point(at = 5_000L), stepCount = 3).egressBirth)
    }

    /**
     * [DET-NOTHING-TO-JUDGE-IS-NOT-NO-DOUBT-001] **The claim the new prompt rests on**, pinned here
     * where the mechanism lives rather than asserted in prose.
     *
     * `EgressBirthJudgement.NOT_RECORDED` degrades a silent confirm to a question, and that is only
     * proportionate because the two confirm paths carrying their own physical proof of an egress
     * cannot reach it: a counted step opens a birth, and so does an accepted kinematic witness. What
     * remains is the path with no walk evidence at all (AR vehicle-exit + clock + displacement),
     * which is exactly the one that used to get the free "no doubt".
     *
     * ⚠️ And the asymmetry between the two flavours is real and NOT fixed here: the STOPPED flavour
     * passes `acceptsKinematicWitness = false`, so a mute step counter opens no birth there. The
     * production comment calls it *"bug #6 — preserved and named, never fixed inside a move"*; this
     * ticket makes its consequence a question instead of a silent pin, it does not close it.
     */
    @Test
    fun should_open_a_birth_for_either_witness_a_confirm_path_could_have() {
        assertNotNull(
            anchored.birth(point(at = 5_000L), stepCount = 1).egressBirth,
            "a counted step opens a birth — so the steps path can never be NOT_RECORDED",
        )
        assertNotNull(
            anchored.birth(
                point(at = 5_000L),
                stepCount = 0,
                kinematicEgressFixes = 1,
                acceptsKinematicWitness = true,
            ).egressBirth,
            "an accepted kinematic witness opens one too — so the kinematic path cannot either",
        )
        assertNull(
            anchored.birth(point(at = 5_000L), stepCount = 0, kinematicEgressFixes = 1).egressBirth,
            "and the STOPPED flavour still opens none on a kinematic witness alone — bug #6, named",
        )
    }

    /** A refinement sharpens the position and **keeps the step count**: the birth did not move, the
     *  witness of it did. */
    @Test
    fun should_sharpen_the_birth_position_without_moving_its_step_count() {
        val born = anchored.birth(point(at = 5_000L, accuracy = 30f), stepCount = 3)
        val sharper = born.birth(point(at = 6_000L, accuracy = 6f), stepCount = 5)
        assertEquals(6f, sharper.egressBirth?.originFix?.accuracy)
        assertEquals(3, sharper.egressBirth?.stepCountAtBirth, "the birth's step count is the birth's")
    }

    /** …and a walk that has moved on may not drag the birth with it. [BUG-REPARK-WALK] */
    @Test
    fun should_refuse_to_sharpen_a_birth_the_user_has_already_walked_away_from() {
        val born = anchored.birth(point(at = 5_000L, accuracy = 30f), stepCount = 3)
        val later = born.birth(point(at = 6_000L, accuracy = 6f), stepCount = 30)
        assertEquals(30f, later.egressBirth?.originFix?.accuracy, "the birth stays where it was born")
    }

    /** The birth dies with the anchor it was measured against. */
    @Test
    fun should_forget_the_birth_when_the_anchor_is_cleared() {
        val born = anchored.birth(point(), stepCount = 3)
        assertNull(born.birth(point(), stepCount = 9, anchorCleared = true).egressBirth)
    }

    // ── Bug #6: the asymmetry between the two flavours ───────────────────────

    /**
     * **The MOVING flavour accepts a kinematic witness**: a GPS-measured walk fix opens a birth even
     * with a mute step counter, which is the only way that hardware ever gets one.
     */
    @Test
    fun should_open_a_birth_from_a_kinematic_witness_on_a_moving_fix() {
        val born = anchored.birth(
            point(at = 5_000L), stepCount = 0, kinematicEgressFixes = 1, acceptsKinematicWitness = true,
        )
        assertEquals(0, born.egressBirth?.stepCountAtBirth)
        assertEquals(5_000L, born.egressBirth?.originFix?.timestamp)
    }

    /**
     * **…and the STOPPED flavour does not.** Same inputs, same anchor, opposite answer — because
     * `acceptsKinematicWitness` is false there.
     *
     * ⚠️ This is **bug #6, preserved and named, not fixed**. Both readings are defensible: on a
     * stopped fix a kinematic witness is either the mute-counter user finally getting a birth, or
     * GPS noise inventing one at a red light — and the difference decides where a pin lands.
     * Settling it needs a directed replay or field data. What this test does is make the asymmetry
     * impossible to read as an accident, and impossible to erase without a failing assert.
     */
    @Test
    fun should_refuse_a_kinematic_witness_on_a_stopped_fix() {
        val notBorn = anchored.birth(
            point(at = 5_000L), stepCount = 0, kinematicEgressFixes = 1, acceptsKinematicWitness = false,
        )
        assertNull(notBorn.egressBirth)
    }

    // ── The car's rest clock ──────────────────────────────────────────────────

    /**
     * [DET-CAR-REST-CLOCK-001] Rest is measured from the stop the ANCHOR belongs to, not from the
     * phone's current stop clock — a pedestrian who keeps restarting their own cannot restart the
     * car's.
     */
    @Test
    fun should_measure_the_cars_rest_from_the_stop_the_anchor_belongs_to() {
        val anchored = AnchorTrust().stoppedAt(firstStop, point())
        assertEquals(50_000L, anchored.restMsAt(60_000L))
        assertEquals(0L, AnchorTrust().restMsAt(60_000L), "no anchor, no rest to speak of")
    }
}
