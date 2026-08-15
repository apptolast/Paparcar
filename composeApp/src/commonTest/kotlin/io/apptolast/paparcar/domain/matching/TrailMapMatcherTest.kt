package io.apptolast.paparcar.domain.matching

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.places.RoadWay
import io.apptolast.paparcar.domain.util.haversineMeters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrailMapMatcherTest {

    private fun gp(lat: Double, lon: Double, accuracy: Float = 0f) = GpsPoint(lat, lon, accuracy, 0L, 0f)

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

    @Test
    fun `should keep a straight stretch on the main road instead of a parallel service way`() {
        // Field 2026-08-10 (CA-603): a school drop-off loop runs ~20 m parallel to the main road,
        // CONNECTED at both ends — routable, near-identical length, so the transition term can't
        // discriminate. Drifted fixes sit NEARER the service way; only the minor-way emission
        // handicap keeps the line on the real road. [ROUTE-QUALITY-001]
        val main = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        val serviceLoop = RoadWay(listOf(gp(36.60018, -6.2400), gp(36.60018, -6.2300)), isMinor = true)
        val joinWest = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.60018, -6.2400)), isMinor = true)
        val joinEast = RoadWay(listOf(gp(36.6000, -6.2300), gp(36.60018, -6.2300)), isMinor = true)
        // Every mid fix drifts ~13 m north of the main road → only ~7 m from the service loop.
        val trail = listOf(
            gp(36.60012, -6.2360),
            gp(36.60012, -6.2355),
            gp(36.60012, -6.2350),
            gp(36.60012, -6.2345),
            gp(36.60012, -6.2340),
        )

        val matched = TrailMapMatcher.snap(trail, listOf(main, serviceLoop, joinWest, joinEast))

        assertTrue(matched.isNotEmpty())
        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected the drawn line on the MAIN road, a point is ${offset}m off it")
        }
    }

    @Test
    fun `should still match a service way when the trail is genuinely on it`() {
        // A fuel-station forecourt / parking aisle ~67 m from the main road: the fixes are ON the
        // aisle, the main road's emission is hopeless — the handicap must not exile a real
        // service-way stretch. [ROUTE-QUALITY-001]
        val main = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        val aisle = RoadWay(listOf(gp(36.6006, -6.2400), gp(36.6006, -6.2300)), isMinor = true)
        val trail = listOf(
            gp(36.6006, -6.2360),
            gp(36.6006, -6.2355),
            gp(36.6006, -6.2350),
        )

        val matched = TrailMapMatcher.snap(trail, listOf(main, aisle))

        assertTrue(matched.isNotEmpty())
        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6006, p.longitude)
            assertTrue(offset < 1.0, "expected the drawn line on the AISLE, a point is ${offset}m off it")
        }
    }

    // ── Data holes are never silently routed through [ROUTE-GAP-HONEST-001] ────────

    private fun gpAt(lat: Double, lon: Double, ts: Long) = GpsPoint(lat, lon, 0f, ts, 0f)

    @Test
    fun `should mark the road bridge across a data hole as inferred instead of passing it off as measured`() {
        // The field bug (14-08, Redmi Litoral): a 7-min GPS nap spanning kilometres was bridged
        // with the shortest road corridor and drawn EXACTLY like the measured line. The bridge may
        // still be drawn — but its span must be reported so the UI can dim it and ask.
        val eastWest = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        val northSouth = RoadWay(listOf(gp(36.6000, -6.2300), gp(36.6100, -6.2300)))
        // Two fixes ~1.4 km apart with 7 minutes of silence between them.
        val trail = listOf(gpAt(36.60005, -6.2398, 0L), gpAt(36.6098, -6.23005, 7 * 60_000L))

        val matched = TrailMapMatcher.match(trail, listOf(eastWest, northSouth))

        assertTrue(matched.points.size > 2, "expected the hole bridged along the streets")
        assertTrue(
            matched.points.any { it.latitude == 36.6000 && it.longitude == -6.2300 },
            "expected the street corner on the bridged path",
        )
        assertEquals(1, matched.inferredSpans.size, "expected the bridge REPORTED as inferred")
        val span = matched.inferredSpans.single()
        assertEquals(0, span.first, "expected the span to open on the measured anchor")
        assertEquals(matched.points.lastIndex, span.last, "expected the span to close on the measured anchor")
        assertTrue(matched.cuts.isEmpty())
    }

    @Test
    fun `should keep a sparse highway step as an ordinary measured transition`() {
        // 400 m between fixes 15 s apart is live highway sampling, NOT a hole — it must stay a
        // plain routed transition with no inferred span and no cut.
        val road = RoadWay(listOf(gp(36.6000, -6.2500), gp(36.6000, -6.2200)))
        val trail = listOf(
            gpAt(36.60003, -6.2460, 0L),
            gpAt(36.60003, -6.2415, 15_000L), // ~400 m later
            gpAt(36.60003, -6.2370, 30_000L),
        )

        val matched = TrailMapMatcher.match(trail, listOf(road))

        assertTrue(matched.inferredSpans.isEmpty(), "expected no inferred span on live sparse sampling")
        assertTrue(matched.cuts.isEmpty())
        matched.points.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected the sparse line on the street, a point is ${offset}m off")
        }
    }

    @Test
    fun `should cut instead of bridging a hole with no plausible road path`() {
        // Two disconnected streets with a 7-min, ~1.4 km hole between their fixes: no road path
        // exists, so the honest output is a CUT — the two stretches must never be joined.
        val a = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2390)))
        val b = RoadWay(listOf(gp(36.6100, -6.2300), gp(36.6100, -6.2290)))
        val trail = listOf(gpAt(36.60005, -6.2398, 0L), gpAt(36.60995, -6.2295, 7 * 60_000L))

        val matched = TrailMapMatcher.match(trail, listOf(a, b))

        assertEquals(2, matched.points.size)
        assertTrue(matched.inferredSpans.isEmpty(), "no plausible bridge → nothing inferred")
        assertEquals(listOf(0), matched.cuts, "expected the line to CUT between the stretches")
    }

    @Test
    fun `should cut instead of bridging a hole beyond the ceiling`() {
        // A hole larger than GAP_BRIDGE_CEILING_METERS (~9 km along one straight road): the guess
        // space is too large to ask about — cut, never a kilometres-long invention.
        val road = RoadWay(listOf(gp(36.5500, -6.2400), gp(36.6400, -6.2400)))
        val trail = listOf(gpAt(36.5505, -6.23995, 0L), gpAt(36.6395, -6.23995, 20 * 60_000L))

        val matched = TrailMapMatcher.match(trail, listOf(road))

        assertTrue(matched.inferredSpans.isEmpty(), "expected no bridge beyond the ceiling")
        assertEquals(listOf(0), matched.cuts)
    }

    // ── Per-measurement σ — imprecise fixes stop choosing the street [ROUTE-FIX-ACCURACY-001] ──

    @Test
    fun `should keep an imprecise drifted run on the followed street instead of detouring to a parallel one`() {
        // Field 2026-08-14 (centro, Federico Rubio / Palacios): a run of drifted fixes with POOR
        // reported accuracy sat nearer a connected parallel street, and with a constant σ their
        // collective emission outvoted the detour cost — the line looped off the followed street.
        // With per-measurement σ the imprecise run carries a near-flat emission and the transition
        // term keeps the line where the sharp fixes put it.
        val followed = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        val parallel = RoadWay(listOf(gp(36.60045, -6.2400), gp(36.60045, -6.2300)))
        val crossWest = RoadWay(listOf(gp(36.6000, -6.2356), gp(36.60045, -6.2356)))
        val crossEast = RoadWay(listOf(gp(36.6000, -6.2344), gp(36.60045, -6.2344)))
        val trail = listOf(
            gp(36.60005, -6.2358, accuracy = 8f),
            gp(36.60040, -6.2354, accuracy = 60f), // drifted run — ~44 m off the followed street,
            gp(36.60040, -6.2350, accuracy = 60f), // ~6 m from the parallel one, but the fix
            gp(36.60040, -6.2346, accuracy = 60f), // barely measured anything (acc 60 m)
            gp(36.60005, -6.2342, accuracy = 8f),
        )

        val matched = TrailMapMatcher.snap(trail, listOf(followed, parallel, crossWest, crossEast))

        assertTrue(matched.isNotEmpty())
        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected the drawn line on the FOLLOWED street, a point is ${offset}m off it")
        }
    }

    @Test
    fun `should still draw the line when every fix is imprecise`() {
        // A stretch where mediocre fixes are the ONLY data must still anchor a line — flattening
        // their authority must never mean losing them (user rule, 2026-08-15: descartar vale con
        // redundancia; con pocos fixes hay que conservar algo).
        val road = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        val trail = listOf(
            gp(36.60009, -6.2360, accuracy = 90f),
            gp(36.60009, -6.2350, accuracy = 90f),
            gp(36.60009, -6.2340, accuracy = 90f),
        )

        val matched = TrailMapMatcher.snap(trail, listOf(road))

        assertTrue(matched.isNotEmpty(), "expected a line from imprecise-only fixes, got none")
        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected the imprecise-only line on the street, a point is ${offset}m off")
        }
    }

    @Test
    fun `should let the sharper fix of a spacing bucket represent it in decimation`() {
        // Two fixes land in the same ~25 m bucket: the first is mediocre (acc 40) and pulls toward
        // a parallel street; the second is sharp (acc 5) and sits on the followed one. The bucket's
        // representative must be the sharp measurement, not the first arrival.
        val followed = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        val parallel = RoadWay(listOf(gp(36.60036, -6.2400), gp(36.60036, -6.2300)))
        val trail = listOf(
            gp(36.60005, -6.2360, accuracy = 5f),
            gp(36.60028, -6.2352, accuracy = 40f), // mediocre — nearer the parallel street
            gp(36.60005, -6.2351, accuracy = 5f),  // sharp, same bucket (~9 m away)
            gp(36.60005, -6.2344, accuracy = 5f),
        )

        val matched = TrailMapMatcher.snap(trail, listOf(followed, parallel))

        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected the sharp fix to represent its bucket, a point is ${offset}m off")
        }
    }

    @Test
    fun `should not tax a shared segment when a major way overlaps a service way`() {
        // The same physical segment fetched twice (a service way overlapping the main road, e.g.
        // dual mapping): the major way must upgrade the shared edge so the handicap never taxes a
        // stretch that is also a real road — regardless of fetch order. [ROUTE-QUALITY-001]
        val asService = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)), isMinor = true)
        val asMain = RoadWay(listOf(gp(36.6000, -6.2400), gp(36.6000, -6.2300)))
        val parallel = RoadWay(listOf(gp(36.60018, -6.2400), gp(36.60018, -6.2300)))
        // Fixes drift toward the parallel plain road; if the shared edge kept its minor tax the
        // parallel one would win.
        val trail = listOf(
            gp(36.60006, -6.2360),
            gp(36.60006, -6.2350),
            gp(36.60006, -6.2340),
        )

        val matched = TrailMapMatcher.snap(trail, listOf(asService, asMain, parallel))

        assertTrue(matched.isNotEmpty())
        matched.forEach { p ->
            val offset = haversineMeters(p.latitude, p.longitude, 36.6000, p.longitude)
            assertTrue(offset < 1.0, "expected the upgraded shared edge to win, a point is ${offset}m off it")
        }
    }
}
