package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrivingRouteTest {

    private fun p(lat: Double, lon: Double, ts: Long) = GpsPoint(lat, lon, 5f, ts, 0f)

    @Test
    fun `should start the route with the first fix`() {
        val out = DrivingRoute.append(emptyList(), p(40.0, -3.0, 1_000L))
        assertEquals(1, out.size)
        assertEquals(40.0, out.first().latitude)
    }

    @Test
    fun `should append a fix that moved beyond the decimation distance`() {
        val start = listOf(p(40.0, -3.0, 1_000L))
        // ~110 m north — well beyond MIN_POINT_DISTANCE_M.
        val out = DrivingRoute.append(start, p(40.001, -3.0, 6_000L))
        assertEquals(2, out.size)
    }

    @Test
    fun `should decimate a fix that barely moved, returning the same list reference`() {
        val start = listOf(p(40.0, -3.0, 1_000L))
        // ~1 m away — below MIN_POINT_DISTANCE_M.
        val out = DrivingRoute.append(start, p(40.00001, -3.0, 6_000L))
        assertTrue(out === start, "expected the same reference so the caller can skip persisting")
    }

    @Test
    fun `should start a fresh route after a long silence (new trip)`() {
        val start = listOf(p(40.0, -3.0, 1_000L), p(40.001, -3.0, 6_000L))
        val afterGap = DrivingRoute.append(start, p(41.0, -4.0, 6_000L + DrivingRoute.NEW_TRIP_GAP_MS + 1))
        assertEquals(1, afterGap.size)
        assertEquals(41.0, afterGap.first().latitude)
    }

    // ── Hopeless-accuracy ingest gate [ROUTE-FIX-ACCURACY-001] ───────────────

    @Test
    fun `should reject a hopeless-accuracy fix, returning the same list reference`() {
        val start = listOf(p(40.0, -3.0, 1_000L))
        val junk = GpsPoint(40.001, -3.0, DrivingRoute.HOPELESS_ACCURACY_METERS + 1f, 6_000L, 0f)
        val out = DrivingRoute.append(start, junk)
        assertTrue(out === start, "expected the junk fix rejected without a disk write")
    }

    @Test
    fun `should not seed a trip with a hopeless-accuracy fix`() {
        val junk = GpsPoint(40.0, -3.0, 150f, 1_000L, 0f)
        assertTrue(DrivingRoute.append(emptyList(), junk).isEmpty(), "expected no garbage origin seeded")
    }

    @Test
    fun `should keep accepting fixes at the hopeless bound`() {
        val start = listOf(p(40.0, -3.0, 1_000L))
        val mediocre = GpsPoint(40.001, -3.0, DrivingRoute.HOPELESS_ACCURACY_METERS, 6_000L, 0f)
        assertEquals(2, DrivingRoute.append(start, mediocre).size)
    }

    // ── endAtAnchor [ROUTE-END-AT-CAR-001] ───────────────────────────────────

    @Test
    fun `should drop fixes recorded after the anchor and end at the anchor when a walk tail follows the stop`() {
        val drive = listOf(p(40.0, -3.0, 1_000L), p(40.001, -3.0, 5_000L), p(40.002, -3.0, 9_000L))
        val walk = listOf(p(40.0025, -3.0002, 15_000L), p(40.003, -3.0004, 20_000L))
        val anchor = p(40.0023, -3.0, 10_000L) // ~33 m past the last driven fix

        val out = DrivingRoute.endAtAnchor(drive + walk, anchor)

        assertEquals(drive + anchor, out)
    }

    @Test
    fun `should keep the route intact when no fixes follow the anchor and the line already ends at it`() {
        val drive = listOf(p(40.0, -3.0, 1_000L), p(40.001, -3.0, 5_000L), p(40.002, -3.0, 9_000L))
        val anchor = p(40.00205, -3.0, 10_000L) // ~6 m from the last fix — below the append floor

        assertEquals(drive, DrivingRoute.endAtAnchor(drive, anchor))
    }

    @Test
    fun `should not stretch the line to an anchor beyond the plausibility ceiling`() {
        val drive = listOf(p(40.0, -3.0, 1_000L), p(40.001, -3.0, 5_000L))
        val anchor = p(40.1, -3.0, 6_000L) // ~11 km away — another story, never stretch

        assertEquals(drive, DrivingRoute.endAtAnchor(drive, anchor))
    }

    @Test
    fun `should trim nothing but still cap the line when the anchor carries no timestamp`() {
        val drive = listOf(p(40.0, -3.0, 1_000L), p(40.001, -3.0, 5_000L))
        val anchor = p(40.0013, -3.0, 0L) // synthetic caller, ~33 m past the end

        assertEquals(drive + anchor, DrivingRoute.endAtAnchor(drive, anchor))
    }

    @Test
    fun `should cap the route to MAX_POINTS, dropping the oldest`() {
        var route = emptyList<GpsPoint>()
        // Each fix ~110 m apart and 5 s apart so none is decimated or gap-reset.
        for (i in 0..DrivingRoute.MAX_POINTS + 50) {
            route = DrivingRoute.append(route, p(40.0 + i * 0.001, -3.0, 1_000L + i * 5_000L))
        }
        assertEquals(DrivingRoute.MAX_POINTS, route.size)
        // The oldest points fell off the tail — the last is the most recent.
        assertEquals(40.0 + (DrivingRoute.MAX_POINTS + 50) * 0.001, route.last().latitude, 1e-9)
    }
}
