package io.apptolast.paparcar.domain.detection.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DET-GAP-ANCHOR-ZONE-001] How far the phone could have walked while the GPS stream was silent —
 * the only bound a hole leaves on where the car actually is.
 *
 * This function had **no test** until P1.8 moved it: it was inlined in one evaluator, extracted when
 * a second caller needed the identical arithmetic, and both callers feed it straight into a saved
 * zone's radius. Two consumers and no test is precisely the shape a bound gets widened in one and
 * forgotten in the other.
 */
class GapDoubtTest {

    private val pedestrianMps = 2f

    @Test
    fun should_bound_the_doubt_by_what_a_walker_could_cover_when_the_stream_went_silent() {
        // The Góndola hole: 100.5 s of silence between the last driving fix and the first stopped
        // one. A walker could have carried the phone ~201 m in that time.
        assertEquals(201.0, walkableInsideGapMeters(100_500L, pedestrianMps), 0.001)
    }

    @Test
    fun should_bound_nothing_when_there_was_no_hole() {
        assertEquals(0.0, walkableInsideGapMeters(0L, pedestrianMps))
    }

    /**
     * A negative gap is clock skew, not a hole. It must yield no doubt rather than a negative
     * radius — a negative would sink under the zone floor and silently claim a precision the
     * evidence never supported.
     */
    @Test
    fun should_bound_nothing_when_the_clock_ran_backwards() {
        assertEquals(0.0, walkableInsideGapMeters(-5_000L, pedestrianMps))
    }

    /**
     * Pedestrian pace on purpose, not driving pace. The question is how far the BODY could have
     * carried the phone away from the car during the silence — that is what makes the doubt
     * boundable at all. At driving pace the "bound" would cover a whole town and stop bounding
     * anything.
     */
    @Test
    fun should_scale_with_the_walking_ceiling_it_is_given() {
        val hole = 60_000L
        assertEquals(120.0, walkableInsideGapMeters(hole, 2f), 0.001)
        assertEquals(60.0, walkableInsideGapMeters(hole, 1f), 0.001)
    }

    @Test
    fun should_grow_monotonically_with_the_silence() {
        var previous = -1.0
        for (seconds in 0..600 step 5) {
            val doubt = walkableInsideGapMeters(seconds * 1_000L, pedestrianMps)
            assertTrue(doubt >= previous, "the doubt shrank at ${seconds}s")
            previous = doubt
        }
    }
}
