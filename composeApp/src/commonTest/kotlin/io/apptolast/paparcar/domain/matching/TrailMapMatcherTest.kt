package io.apptolast.paparcar.domain.matching

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.places.RoadWay
import io.apptolast.paparcar.domain.util.haversineMeters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrailMapMatcherTest {

    private fun gp(lat: Double, lon: Double) = GpsPoint(lat, lon, 0f, 0L, 0f)

    @Test
    fun `should snap a near point onto the road`() {
        // A road running east-west at lat 36.6000 between lon -6.2300 and -6.2280.
        val road = RoadWay(listOf(gp(36.6000, -6.2300), gp(36.6000, -6.2280)))
        // Trail points ~11 m north of the road (within MAX_SNAP_METERS).
        val trail = listOf(gp(36.60010, -6.2298), gp(36.60012, -6.2290))
        val snapped = TrailMapMatcher.snap(trail, listOf(road))
        snapped.forEach { p ->
            // Snapped onto the road's latitude → ~0 m north/south offset.
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected snapped onto road lat, off by ${offset}m")
        }
    }

    @Test
    fun `should keep a far point unchanged`() {
        val road = RoadWay(listOf(gp(36.6000, -6.2300), gp(36.6000, -6.2280)))
        val far = gp(36.6020, -6.2290)   // ~220 m north — beyond MAX_SNAP_METERS
        val far2 = gp(36.6021, -6.2289)
        val snapped = TrailMapMatcher.snap(listOf(far, far2), listOf(road))
        assertEquals(far, snapped[0])
        assertEquals(far2, snapped[1])
    }

    @Test
    fun `should return input unchanged when no roads`() {
        val trail = listOf(gp(36.6, -6.23), gp(36.601, -6.231))
        assertEquals(trail, TrailMapMatcher.snap(trail, emptyList()))
    }

    // ── v2 — long gaps routed along the road graph [DET-ROUTE-ORIGIN-001] ──────────

    @Test
    fun `should fill a long gap by routing through the street corner instead of a straight chord`() {
        // L-shaped streets sharing the corner node (36.6000, -6.2300): one east-west, one north-south.
        val eastWest = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        val northSouth = RoadWay(listOf(gp(36.6000, -6.2300), gp(36.6100, -6.2300)))
        // Trip woke ~1.4 km from the parked spot: one point near each far end of the L.
        val trail = listOf(gp(36.60005, -6.2398), gp(36.6098, -6.23005))

        val matched = TrailMapMatcher.snap(trail, listOf(eastWest, northSouth))

        // The gap is filled through the road graph — the corner vertex must be on the drawn path.
        assertTrue(matched.size > 2, "expected routed vertices inserted, got ${matched.size} points")
        assertTrue(
            matched.any { it.latitude == 36.6000 && it.longitude == -6.2300 },
            "expected the street corner on the filled path",
        )
        // Endpoints preserved (snapped onto their streets).
        assertTrue(haversineMeters(matched.first().latitude, matched.first().longitude, 36.6000, -6.2398) < 2.0)
        assertTrue(haversineMeters(matched.last().latitude, matched.last().longitude, 36.6098, -6.2300) < 2.0)
    }

    @Test
    fun `should keep the straight chord when the roads are disconnected`() {
        // Two short streets with no shared node — no road path exists between them.
        val a = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2390)))
        val b = RoadWay(listOf(gp(36.6100, -6.2300), gp(36.6100, -6.2290)))
        val trail = listOf(gp(36.60005, -6.2398), gp(36.60995, -6.2295))

        val matched = TrailMapMatcher.snap(trail, listOf(a, b))

        // Honest fallback: no invented route, just the two snapped points (a straight chord).
        assertEquals(2, matched.size)
    }

    @Test
    fun `should not route between consecutive points closer than the gap threshold`() {
        val road = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        // Two points ~27 m apart along the road — dense driving fixes, no routing needed.
        val trail = listOf(gp(36.60005, -6.2350), gp(36.60005, -6.2347))

        val matched = TrailMapMatcher.snap(trail, listOf(road))

        assertEquals(2, matched.size)
    }

    // ── v3 — continuity-aware snapping + spike dropping [ROUTE-LINE-CLEAN-001] ──────

    @Test
    fun `should drop a single off-road spike between on-road fixes`() {
        val road = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        // Driving along the road with one fix ~110 m north (beyond MAX_SNAP_METERS) mid-trail.
        val trail = listOf(
            gp(36.60003, -6.2360),
            gp(36.60004, -6.2355),
            gp(36.6010, -6.2352), // the spike
            gp(36.60003, -6.2349),
            gp(36.60004, -6.2345),
        )

        val matched = TrailMapMatcher.snap(trail, listOf(road))

        assertEquals(4, matched.size, "expected the spike dropped")
        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected every drawn point on the road, one is ${offset}m off")
        }
    }

    @Test
    fun `should keep a long off-road run raw`() {
        val road = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        // Three consecutive fixes far north of any street (a car park / open ground) — beyond
        // MAX_OUTLIER_RUN, so this is a real off-road stretch, not a spike.
        val offRoad = listOf(gp(36.6010, -6.2356), gp(36.6011, -6.2352), gp(36.6010, -6.2348))
        val trail = listOf(gp(36.60003, -6.2360)) + offRoad + listOf(gp(36.60003, -6.2344))

        val matched = TrailMapMatcher.snap(trail, listOf(road))

        offRoad.forEach { p ->
            assertTrue(matched.contains(p), "expected the off-road stretch kept raw")
        }
    }

    @Test
    fun `should keep an off-road trail origin raw`() {
        val road = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        // The backdated origin sits ~110 m off the street (car park); it must survive as-is.
        val origin = gp(36.6010, -6.2360)
        val trail = listOf(origin, gp(36.60003, -6.2355), gp(36.60004, -6.2350))

        val matched = TrailMapMatcher.snap(trail, listOf(road))

        assertEquals(origin, matched.first())
    }

    @Test
    fun `should keep a noisy fix on the followed street instead of jumping to a parallel one`() {
        // Two parallel east-west streets ~40 m apart.
        val followed = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        val parallel = RoadWay(listOf(gp(36.60036, -6.2400), gp(36.60036, -6.2300)))
        // Trail hugs the southern street except one noisy fix ~22 m north — NEARER the parallel
        // street (~14 m), which naive per-point snapping would jump to.
        val trail = listOf(
            gp(36.60005, -6.2360),
            gp(36.60005, -6.2355),
            gp(36.60020, -6.2350), // noisy — nearest street is the parallel one
            gp(36.60005, -6.2345),
            gp(36.60005, -6.2340),
        )

        val matched = TrailMapMatcher.snap(trail, listOf(followed, parallel))

        assertEquals(5, matched.size)
        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected every fix on the followed street, one is ${offset}m off it")
        }
    }
}
