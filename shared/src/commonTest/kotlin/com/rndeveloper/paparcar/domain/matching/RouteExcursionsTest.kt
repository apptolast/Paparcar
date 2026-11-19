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
        assertEquals(listOf(step.first()), RouteExcursions.collapseExcursions(step))
    }

    @Test
    fun `should leave a step that actually went somewhere`() {
        val step = listOf(
            gp(36.592870, -6.243210),
            gp(36.592972, -6.243210),
            gp(36.593100, -6.243000),
        )
        assertFalse(RouteExcursions.isReturnExcursion(step))
        assertEquals(step, RouteExcursions.collapseExcursions(step))
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
    fun `should collapse an excursion that sits INSIDE the step, not only one that returns to its start`() {
        // Esta es la forma que de verdad emite el matcher y que la primera version del guard NO
        // cazaba: el paso SI va a algun sitio, pero por el camino sale a un nodo y vuelve.
        // Medido: 6 sesiones reales con 10 picos de esta forma y el guard disparando cero veces.
        val step = listOf(
            gp(36.6330, -6.2278),   // la medicion de la que sale el paso
            gp(36.6331, -6.2279),   // nodo
            gp(36.6332, -6.2280),   // ...sale
            gp(36.6331, -6.2279),   // ...y vuelve al mismo nodo
            gp(36.6334, -6.2283),   // sigue su camino
        )
        val collapsed = RouteExcursions.collapseExcursions(step)
        assertEquals(
            listOf(step[0], step[1], step[4]),
            collapsed,
            "la ida y vuelta interior tenia que desaparecer y el resto quedarse",
        )
    }

    @Test
    fun `should collapse nested excursions down to the ground actually covered`() {
        val a = gp(36.6330, -6.2278)
        val b = gp(36.6331, -6.2279)
        val c = gp(36.6332, -6.2280)
        // a -> b -> c -> b -> a : todo el tinglado vuelve a `a`
        assertEquals(listOf(a), RouteExcursions.collapseExcursions(listOf(a, b, c, b, a)))
    }

    @Test
    fun `should never move the measurement the step left from`() {
        // El primer punto es la medicion: el colapso puede vaciar el interior, jamas reemplazarla.
        val origin = gp(36.6330, -6.2278)
        val out = RouteExcursions.collapseExcursions(
            listOf(origin, gp(36.6331, -6.2279), origin)
        )
        assertEquals(origin, out.first())
        assertEquals(1, out.size)
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
        assertEquals(step, RouteExcursions.collapseExcursions(step))
    }
}
