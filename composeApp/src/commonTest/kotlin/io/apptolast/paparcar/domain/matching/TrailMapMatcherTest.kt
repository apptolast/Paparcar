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

    // ── Routed transitions — the drawn line is road geometry, not chords ────────────
    // Born as v2 gap-fill [DET-ROUTE-ORIGIN-001]; in v4 every transition is routed.

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
    fun `should not invent vertices between points on the same straight street`() {
        val road = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        // Two points ~27 m apart on one straight edge — the route between them IS the edge.
        val trail = listOf(gp(36.60005, -6.2350), gp(36.60005, -6.2347))

        val matched = TrailMapMatcher.snap(trail, listOf(road))

        assertEquals(2, matched.size)
    }

    @Test
    fun `should draw the street corner between two short-distance fixes on a bend`() {
        // L-shaped streets sharing the corner node (36.6000, -6.2300). One fix ~40 m before the
        // corner, one ~40 m after it — a step the v2 gap-fill (60 m threshold) never routed. v4
        // routes every transition, so the drawn line turns AT the corner instead of cutting it.
        val eastWest = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        val northSouth = RoadWay(listOf(gp(36.6000, -6.2300), gp(36.6100, -6.2300)))
        val trail = listOf(gp(36.60002, -6.23045), gp(36.60036, -6.23002))

        val matched = TrailMapMatcher.snap(trail, listOf(eastWest, northSouth))

        assertTrue(
            matched.any { it.latitude == 36.6000 && it.longitude == -6.2300 },
            "expected the corner vertex on the drawn line, got ${matched.size} points",
        )
    }

    @Test
    fun `should decimate dense fixes and still draw the whole street stretch`() {
        val road = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        // A fix every ~9 m with alternating ~9 m jitter across the street — far denser than
        // MATCH_SPACING_METERS. Matching runs on the decimated skeleton; the drawn line still
        // covers the stretch and sits on the street.
        val trail = (0..10).map { i ->
            gp(if (i % 2 == 0) 36.60008 else 36.59992, -6.2360 + i * 0.0001)
        }

        val matched = TrailMapMatcher.snap(trail, listOf(road))

        assertTrue(matched.size < trail.size, "expected decimation, got ${matched.size} points")
        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected the drawn line on the street, a point is ${offset}m off")
        }
        // The stretch endpoints survive decimation.
        assertTrue(haversineMeters(matched.first().latitude, matched.first().longitude, 36.6000, -6.2360) < 2.0)
        assertTrue(haversineMeters(matched.last().latitude, matched.last().longitude, 36.6000, -6.2350) < 2.0)
    }

    // ── Always on the road — off-road fixes are dropped & bridged, never drawn [ROUTE-LINE-ONROAD-001] ──

    @Test
    fun `should drop an off-road spike and keep the drawn line on the road`() {
        val road = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        // Driving along the road with one fix ~220 m north (well beyond MAX_SNAP_METERS) mid-trail:
        // a GPS spike into open ground. It carries no street, so it is dropped and the on-road fixes
        // either side are bridged along the road — the line never darts north to the spike.
        val trail = listOf(
            gp(36.60003, -6.2360),
            gp(36.60004, -6.2355),
            gp(36.6020, -6.2352), // the spike — ~220 m off any street
            gp(36.60003, -6.2349),
            gp(36.60004, -6.2345),
        )

        val matched = TrailMapMatcher.snap(trail, listOf(road))

        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected every drawn point on the road, one is ${offset}m off")
        }
    }

    @Test
    fun `should drop a long off-road run and bridge it along the road, never drawing it raw`() {
        val road = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        // Three consecutive fixes far north of any street (open ground) — a real off-road stretch.
        // v5 never draws these raw: they are dropped and the gap is bridged along the road.
        val offRoad = listOf(gp(36.6020, -6.2356), gp(36.6021, -6.2352), gp(36.6020, -6.2348))
        val trail = listOf(gp(36.60003, -6.2360)) + offRoad + listOf(gp(36.60003, -6.2344))

        val matched = TrailMapMatcher.snap(trail, listOf(road))

        offRoad.forEach { p ->
            assertTrue(!matched.contains(p), "expected the off-road stretch dropped, never drawn raw")
        }
        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected every drawn point on the road, one is ${offset}m off")
        }
    }

    @Test
    fun `should snap a backdated origin set back from the road onto the street`() {
        val road = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        // The backdated origin sits ~170 m off the street (a car park) — beyond MAX_SNAP_METERS but
        // within ORIGIN_SNAP_METERS, so the origin snaps onto the nearest road and the line starts
        // ON the road instead of darting out to the car park.
        val origin = gp(36.60153, -6.2360)
        val trail = listOf(origin, gp(36.60003, -6.2355), gp(36.60004, -6.2350))

        val matched = TrailMapMatcher.snap(trail, listOf(road))

        val originOffset = haversineMeters(matched.first().latitude, matched.first().longitude, 36.6000, matched.first().longitude)
        assertTrue(originOffset < 1.0, "expected the origin snapped onto the road, it is ${originOffset}m off")
        assertTrue(matched.first() != origin, "expected the origin snapped, not kept raw")
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

    @Test
    fun `should keep a noisy fix on the followed street even when the parallel street is connected`() {
        // Same parallel-street trap, but now cross streets CONNECT the two at both ends — the
        // parallel candidate is reachable along the graph, just via an absurd ~900 m detour for a
        // ~48 m measured step. The HMM transition kills it where pure nearest-distance would not.
        // [ROUTE-LINE-PRO-001]
        val followed = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        val parallel = RoadWay(listOf(gp(36.60036, -6.2400), gp(36.60036, -6.2300)))
        val crossWest = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.60036, -6.2400)))
        val crossEast = RoadWay(listOf(gp(36.6000, -6.2300), gp(36.60036, -6.2300)))
        val trail = listOf(
            gp(36.60005, -6.2360),
            gp(36.60005, -6.2355),
            gp(36.60020, -6.2350), // noisy — nearest street is the parallel one
            gp(36.60005, -6.2345),
            gp(36.60005, -6.2340),
        )

        val matched = TrailMapMatcher.snap(trail, listOf(followed, parallel, crossWest, crossEast))

        assertEquals(5, matched.size)
        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected the drawn line on the followed street, a point is ${offset}m off it")
        }
    }
}
