package com.rndeveloper.paparcar.domain.detection.physics

import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * *Could the body plausibly be AT this car?* — the padded containment test the sentry damper and the
 * AR-boarding evaluator share.
 */
class FenceContainmentTest {

    private fun at(meters: Double, accuracy: Float = 0f) =
        GpsPoint(
            latitude = meters / 111_320.0, longitude = 0.0,
            accuracy = accuracy, timestamp = 0L, speed = 0f,
        )

    private val car = at(0.0, accuracy = 5f)

    @Test
    fun should_say_inside_when_the_fix_sits_well_within_the_ring() {
        assertTrue(isWithinFence(at(30.0, accuracy = 4f), car, fenceRadiusMeters = 80f))
    }

    @Test
    fun should_say_outside_when_a_good_fix_sits_well_beyond_the_ring() {
        assertFalse(isWithinFence(at(400.0, accuracy = 4f), car, fenceRadiusMeters = 80f))
    }

    /**
     * **The padding, and the whole reason it exists.** A fix that could be off by 40 m and lands
     * 30 m outside the ring has not shown the body is away from the car — it has shown the receiver
     * is unsure. Reading that as "definitely outside" would act on GPS noise: it would stand the
     * sentry damper down, or label a boarding at the car as mid-trip.
     */
    @Test
    fun should_not_call_it_outside_when_the_fix_is_too_vague_to_say() {
        val vague = at(110.0, accuracy = 40f)
        assertTrue(
            isWithinFence(vague, car, fenceRadiusMeters = 80f),
            "30 m past the ring with 40 m of uncertainty is 'unsure', not 'away'",
        )
    }

    /**
     * The generosity is bounded, not unlimited: a vague fix that is far beyond even its own
     * uncertainty is still outside. Otherwise the padding would swallow real departures.
     */
    @Test
    fun should_still_say_outside_when_the_distance_beats_the_uncertainty() {
        assertFalse(isWithinFence(at(400.0, accuracy = 40f), car, fenceRadiusMeters = 80f))
    }

    /** The boundary is inclusive, on both the radius and the padded edge. */
    @Test
    fun should_include_the_boundary_when_the_fix_lands_exactly_on_it() {
        assertTrue(isWithinFence(at(80.0, accuracy = 0f), car, fenceRadiusMeters = 80f))
        assertTrue(isWithinFence(at(120.0, accuracy = 40f), car, fenceRadiusMeters = 80f))
    }

    /**
     * A larger vehicle registers a larger fence, and containment must follow the radius it is given
     * rather than any flat default — the same resolver the fence was registered with
     * [SESSION-RESTORE-001].
     */
    @Test
    fun should_follow_the_radius_it_is_given_when_the_vehicle_is_larger() {
        val fix = at(110.0, accuracy = 0f)
        assertFalse(isWithinFence(fix, car, fenceRadiusMeters = 80f))
        assertTrue(isWithinFence(fix, car, fenceRadiusMeters = 130f))
    }
}
