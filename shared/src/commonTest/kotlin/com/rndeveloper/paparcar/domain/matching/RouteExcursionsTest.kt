package com.rndeveloper.paparcar.domain.matching

import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [ROUTE-A-SNAPPED-LINE-MUST-NOT-INVENT-GROUND-001] */
class RouteExcursionsTest {

    private fun gp(lat: Double, lon: Double) = GpsPoint(lat, lon, 0f, 0L, 0f)

    @Test
    fun `should call it a return excursion when the step comes back to its own coordinate`() {
        // The exact field shape: 73 of these across 47 trips, every one with its two neighbours
        // on the IDENTICAL coordinate and a single node emitted in between.
        val step = listOf(
            gp(36.592870, -6.243210),
            gp(36.592972, -6.243210),   // out to a graph node, ~11 m
            gp(36.592870, -6.243210),   // and straight back to where it started
        )
        assertTrue(RouteExcursions.isReturnExcursion(step))
        assertEquals(listOf(step.first()), RouteExcursions.collapseReturnExcursion(step))
    }

    @Test
    fun `should leave a step that actually went somewhere`() {
        val step = listOf(
            gp(36.592870, -6.243210),
            gp(36.592972, -6.243210),
            gp(36.593100, -6.243000),
        )
        assertFalse(RouteExcursions.isReturnExcursion(step))
        assertEquals(step, RouteExcursions.collapseReturnExcursion(step))
    }

    @Test
    fun `should leave a step with no interior to invent anything with`() {
        // Two points cannot be an excursion even when they coincide — there is nothing between
        // them to have been drawn, and addPoint already drops the duplicate.
        val same = gp(36.592870, -6.243210)
        assertFalse(RouteExcursions.isReturnExcursion(listOf(same, same)))
        assertFalse(RouteExcursions.isReturnExcursion(listOf(same)))
        assertFalse(RouteExcursions.isReturnExcursion(emptyList()))
    }

    @Test
    fun `should not collapse a step that returns NEAR but not to its coordinate`() {
        // Half a metre of difference is still displacement somebody measured. The collapse is
        // only safe on the provable case — exact return — and the field corpus has no other.
        val step = listOf(
            gp(36.592870, -6.243210),
            gp(36.592972, -6.243210),
            gp(36.592875, -6.243210),   // ~0,6 m short of the start
        )
        assertFalse(RouteExcursions.isReturnExcursion(step))
        assertEquals(step, RouteExcursions.collapseReturnExcursion(step))
    }
}
