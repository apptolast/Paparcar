package io.apptolast.paparcar.domain.detection.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DET-FROZEN-COUNTER-001][DET-GAP-ANCHOR-ZONE-001] The radius of the circle the app is honest
 * enough to draw when it knows the car is parked but not exactly where.
 */
class HonestZoneRadiusTest {

    private val floor = 60f
    private val ceiling = 250f

    @Test
    fun should_take_the_measured_doubt_when_it_exceeds_the_fix_accuracy() {
        // 2026-08-21: 206 steps x 0.75 m = 154 m of walking since the seal. The zone must contain
        // the car, where the old answer was a 3.6 m lie.
        assertEquals(154f, honestZoneRadius(centerAccuracyMeters = 3.6f, doubtMeters = 154.0, floor, ceiling))
    }

    @Test
    fun should_take_the_fix_accuracy_when_it_exceeds_the_measured_doubt() {
        assertEquals(120f, honestZoneRadius(centerAccuracyMeters = 120f, doubtMeters = 12.0, floor, ceiling))
    }

    /**
     * Below the floor an "area" is a dot with extra steps — it claims a precision the evidence does
     * not support while looking like a circle.
     */
    @Test
    fun should_never_draw_a_circle_smaller_than_the_floor() {
        assertEquals(floor, honestZoneRadius(centerAccuracyMeters = 4f, doubtMeters = 8.0, floor, ceiling))
        assertEquals(floor, honestZoneRadius(centerAccuracyMeters = 0f, doubtMeters = 0.0, floor, ceiling))
    }

    /**
     * Above the ceiling it paints half a neighbourhood and stops meaning anything. The artifact is
     * still SAVED at the cap — the nudge becomes the ask-to-refine, because losing the park
     * entirely is the worse answer.
     */
    @Test
    fun should_cap_the_circle_at_the_ceiling_rather_than_lose_the_park() {
        assertEquals(ceiling, honestZoneRadius(centerAccuracyMeters = 30f, doubtMeters = 900.0, floor, ceiling))
    }

    /**
     * **The equivalence this step rests on.** The honest close spelled the clamp `coerceIn(floor,
     * ceiling)` over a doubt that had already absorbed the accuracy; the coordinator spelled it
     * `minOf(ceiling, maxOf(floor, accuracy, doubt))`. Same function. Swept here so the claim is
     * measured rather than asserted.
     */
    @Test
    fun should_agree_with_the_coerceIn_spelling_at_every_combination() {
        var checked = 0
        for (accTenths in 0..4_000 step 7) {
            val acc = accTenths / 10f
            for (doubtTenths in 0..4_000 step 11) {
                val doubt = doubtTenths / 10f
                val viaShared = honestZoneRadius(acc, doubt.toDouble(), floor, ceiling)
                val viaCoerceIn = maxOf(acc, doubt).coerceIn(floor, ceiling)
                assertEquals(viaCoerceIn, viaShared, "acc=$acc doubt=$doubt")
                checked++
            }
        }
        assertTrue(checked > 100_000, "the sweep must actually cover the space, checked=$checked")
    }

    /**
     * The two witnesses are symmetric: whichever is larger wins, and neither can shrink the circle
     * the other earned. A degraded fix cannot hide a long walk, and a long walk cannot hide a
     * degraded fix.
     */
    @Test
    fun should_let_neither_witness_shrink_what_the_other_earned() {
        val bigWalk = honestZoneRadius(centerAccuracyMeters = 5f, doubtMeters = 200.0, floor, ceiling)
        val bigAccuracy = honestZoneRadius(centerAccuracyMeters = 200f, doubtMeters = 5.0, floor, ceiling)
        assertEquals(200f, bigWalk)
        assertEquals(200f, bigAccuracy)
    }
}
