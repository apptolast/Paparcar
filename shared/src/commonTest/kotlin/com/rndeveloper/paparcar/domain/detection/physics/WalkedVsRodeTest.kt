package com.rndeveloper.paparcar.domain.detection.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-RIDE-PROOF-001] The step budget the honest close and the safety net now share.
 *
 * The two use cases stay two verdicts; what they share is this arithmetic, and the tests that
 * matter most here are the ones that pin **why unifying it was safe** and **why the origin is a
 * parameter**.
 */
class WalkedVsRodeTest {

    private val stride = 0.75f
    private val fraction = 0.4f

    /**
     * 150 m at 0.75 m/step is 200 steps, and the bar sits at 40% of that.
     *
     * ⚠️ The bar is **81, not 80**, and the reason is worth knowing before anyone "corrects" it:
     * `0.4f` is not 0.4 — the nearest Float is 0.400000005960…, so the geometric bar lands at
     * 80.0000011920929 and rounds up. The effect is one step on a 150 m walk, which is far inside
     * the tolerance the fraction exists to provide, and it is **identical in both of the forms this
     * refactor unified** (see the boundary sweep below). Left as-is deliberately: changing the
     * arithmetic to land on 80 would be a behaviour change wearing a tidy-up's clothes.
     */
    @Test
    fun should_call_it_walked_when_the_steps_match_the_distance() {
        assertTrue(walkExplainsDisplacement(steps = 200, distanceMeters = 150.0, stride, fraction))
        assertEquals(81L, requiredStepsToWalk(150.0, stride, fraction))
        assertTrue(walkExplainsDisplacement(steps = 81, distanceMeters = 150.0, stride, fraction))
        assertFalse(walkExplainsDisplacement(steps = 80, distanceMeters = 150.0, stride, fraction))
    }

    @Test
    fun should_call_it_ridden_when_the_steps_cannot_account_for_the_distance() {
        // 1.5 km on 40 steps: the body did not walk here.
        assertFalse(walkExplainsDisplacement(steps = 40, distanceMeters = 1_500.0, stride, fraction))
    }

    /**
     * The tolerance is the point of [walkedStepFraction]: a real walk under-logs (pocket carries,
     * phone in hand, OEM batching), so the bar is a fraction of the geometric count, not the count.
     * Half the "expected" steps still reads as walked.
     */
    @Test
    fun should_tolerate_an_under_logging_counter_when_the_walk_was_real() {
        assertTrue(walkExplainsDisplacement(steps = 100, distanceMeters = 150.0, stride, fraction))
    }

    @Test
    fun should_round_the_budget_up_when_the_geometry_is_fractional() {
        // 10 m / 0.75 = 13.33 steps, × 0.4 = 5.33 → a walker owes 6, not 5.
        assertEquals(6L, requiredStepsToWalk(10.0, stride, fraction))
        assertFalse(walkExplainsDisplacement(steps = 5, distanceMeters = 10.0, stride, fraction))
        assertTrue(walkExplainsDisplacement(steps = 6, distanceMeters = 10.0, stride, fraction))
    }

    /**
     * **The equivalence this refactor rests on.** One caller compared `steps >= ceil(x)` and the
     * other `steps >= x` against the raw Double. For integer step counts those are the same test —
     * `n >= ceil(x)` ⟺ `n >= x` — and unifying on the rounded form is therefore exact rather than
     * approximate.
     *
     * Asserting it by inspection would have been a claim; this sweeps a range of distances, both
     * forms, every plausible step count, and fails the day someone changes the rounding.
     */
    @Test
    fun should_agree_with_the_raw_comparison_at_every_boundary() {
        var checked = 0
        for (dmm in 1..4_000) {
            val distance = dmm / 4.0 // 0.25 m granularity up to 1 km
            val rawBar = (distance / stride) * fraction
            val required = requiredStepsToWalk(distance, stride, fraction)
            // Only the counts around the bar can possibly disagree.
            for (steps in (required - 2).coerceAtLeast(0)..(required + 2)) {
                val viaRounded = walkExplainsDisplacement(steps, distance, stride, fraction)
                val viaRaw = steps >= rawBar
                assertEquals(
                    viaRaw, viaRounded,
                    "disagreement at distance=$distance steps=$steps (raw bar $rawBar, rounded $required)",
                )
                checked++
            }
        }
        assertTrue(checked > 10_000, "the sweep must actually cover the boundaries, checked=$checked")
    }

    /**
     * **Why the origin is a parameter.** Field 2026-07-22 01:47 (Redmi, Glorieta): the budget was
     * measured from the PIN while the counter had been zeroed mid-egress ~159 m away. The remaining
     * ~83 m walk cost ~110 steps against the 129 the pin's distance demanded — a walk home read as a
     * ride, and the pin landed inside the user's house. Same steps, two origins, opposite verdicts.
     */
    @Test
    fun should_flip_the_verdict_when_the_two_sides_disagree_on_the_origin() {
        val stepsWalked = 110L
        assertFalse(
            walkExplainsDisplacement(stepsWalked, distanceMeters = 242.0, stride, fraction),
            "measured from the PIN (159 m + 83 m) the walk looks like a ride — the Glorieta bug",
        )
        assertTrue(
            walkExplainsDisplacement(stepsWalked, distanceMeters = 83.0, stride, fraction),
            "measured from the SEAL, where the counter was actually zeroed, it reads as the walk it was",
        )
    }

    @Test
    fun should_demand_nothing_when_the_body_has_not_displaced() {
        assertEquals(0L, requiredStepsToWalk(0.0, stride, fraction))
        assertTrue(walkExplainsDisplacement(steps = 0, distanceMeters = 0.0, stride, fraction))
    }
}
