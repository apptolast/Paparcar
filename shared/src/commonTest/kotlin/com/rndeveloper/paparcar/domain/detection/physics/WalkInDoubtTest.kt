package com.rndeveloper.paparcar.domain.detection.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001] How far the pedestrian had already
 * walked when a walk-entered anchor was captured.
 *
 * Same history as [GapDoubtTest]'s subject and the same reason for existing: the arithmetic was
 * inlined in one evaluator until a second verdict needed it, and while it stayed inlined the second
 * verdict never learned it existed. Two witnesses of one walk, and the rule is that the louder wins.
 */
class WalkInDoubtTest {

    private val stride = 0.75f

    @Test
    fun should_take_the_counted_steps_when_the_counter_spoke_louder_than_the_track() {
        // 40 steps at 0,75 m is 30 m of walking; the GPS only saw the last 12 m of it.
        assertEquals(30.0, walkedInToAnchorMeters(40, 12.0, stride), 0.001)
    }

    /**
     * The case the field trace is made of: the counter said nothing at all during the walk back, so
     * the only witness left is the span of the walk-band run. A rule that demanded counted steps
     * would bound this at zero and license an exact pin on a pedestrian's standing spot — which is
     * the failure `DET-WALK-ENTERED-ANCHOR-ZONE-001` was written for, on the mute-counter device.
     */
    @Test
    fun should_fall_back_to_the_measured_track_when_the_counter_was_mute() {
        assertEquals(29.5, walkedInToAnchorMeters(0, 29.5, stride), 0.001)
    }

    @Test
    fun should_bound_nothing_when_neither_witness_saw_a_walk() {
        assertEquals(0.0, walkedInToAnchorMeters(0, 0.0, stride))
    }

    /**
     * ⚠️ The property the callers depend on and the reason this is not a measurement: it is a LOWER
     * bound. Both witnesses can only report the part of the walk they observed, so the number is
     * free to be smaller than the real offset — field 2026-07-15 returned 29,5 m against 37 m of
     * true error. A caller may size an area with it; a caller may NOT read a small value as
     * "the anchor is good enough", which is exactly the inference the exact pin was resting on.
     */
    @Test
    fun should_never_exceed_the_louder_of_its_two_witnesses() {
        val stepped = 17 * stride.toDouble()
        val tracked = 41.0
        val bound = walkedInToAnchorMeters(17, tracked, stride)
        assertEquals(maxOf(stepped, tracked), bound, 0.001)
        assertTrue(bound <= maxOf(stepped, tracked), "the bound may not invent metres neither saw")
    }

    @Test
    fun should_scale_with_the_stride_it_is_given() {
        assertEquals(20.0, walkedInToAnchorMeters(20, 0.0, 1.0f), 0.001)
        assertEquals(15.0, walkedInToAnchorMeters(20, 0.0, 0.75f), 0.001)
    }

    @Test
    fun should_grow_monotonically_with_the_steps_counted() {
        var previous = -1.0
        for (steps in 0..400 step 10) {
            val doubt = walkedInToAnchorMeters(steps, 5.0, stride)
            assertTrue(doubt >= previous, "the bound shrank at $steps steps")
            previous = doubt
        }
    }
}
