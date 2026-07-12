package io.apptolast.paparcar.data.geohash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [AUDIT-DATA-002 A11] The geohash neighbourhood query — the fix for the border-miss +
 * over-download of the old single-cell query.
 */
class GeohashTest {

    // ── Canonical neighbour algorithm (movable-type reference vectors) ────────

    @Test
    fun should_matchHandVerifiedAdjacents_forReferenceCell() {
        // Hand-traced through the canonical neighbour/border tables for cell 'gbsuv' (odd length),
        // north crosses the border and recurses into the parent.
        assertEquals("gbsvj", geohashAdjacent("gbsuv", 'n'))
        assertEquals("gbsut", geohashAdjacent("gbsuv", 's'))
    }

    @Test
    fun should_beInverse_forOppositeDirections() {
        // The strong correctness invariant: opposite directions cancel for a mid-grid cell.
        for (origin in listOf("ezs42", "gbsuv", "u4pru", "9q8yy")) {
            assertEquals(origin, geohashAdjacent(geohashAdjacent(origin, 'e'), 'w'), "e∘w on $origin")
            assertEquals(origin, geohashAdjacent(geohashAdjacent(origin, 'n'), 's'), "n∘s on $origin")
            assertTrue(geohashAdjacent(origin, 'n') != origin, "n must move $origin")
            assertTrue(geohashAdjacent(origin, 'e') != origin, "e must move $origin")
        }
    }

    // ── Precision picked from radius ─────────────────────────────────────────

    @Test
    fun should_pickPrecision_soCellCoversRadius() {
        assertEquals(6, precisionForRadius(500.0))   // ~0.6 km cell
        assertEquals(5, precisionForRadius(2_000.0)) // ~4.9 km cell (the app's ~2 km radius)
        assertEquals(4, precisionForRadius(10_000.0))
        assertEquals(3, precisionForRadius(50_000.0))
    }

    // ── Query bounds: the 3×3 neighbourhood ──────────────────────────────────

    @Test
    fun should_returnNineDistinctRanges_forAMidGridPoint() {
        val bounds = geohashQueryBounds(36.6024, -6.2766, radiusMeters = 2_000.0)
        assertEquals(9, bounds.size, "center + 8 neighbours")
        assertEquals(bounds.size, bounds.map { it.first }.toSet().size, "ranges must be distinct")
        // Every range is a valid [prefix, successor) with the successor sorting strictly after.
        assertTrue(bounds.all { it.first < it.second })
    }

    @Test
    fun should_includeTheCenterCell_inTheBounds() {
        val lat = 36.6024; val lon = -6.2766
        val center = encodeGeohash(lat, lon, precisionForRadius(2_000.0))
        val bounds = geohashQueryBounds(lat, lon, 2_000.0)
        assertTrue(bounds.any { it.first == center }, "the center cell must be one of the queried ranges")
    }

    @Test
    fun should_coverASpotJustAcrossACellBoundary() {
        // The regression the fix targets: a query point right at a cell edge and a spot a short
        // hop across it. The spot's geohash must fall inside one of the 9 ranges.
        val qLat = 36.60000; val qLon = -6.27000
        val bounds = geohashQueryBounds(qLat, qLon, 2_000.0)
        // A point ~300 m east — encode at full precision, then check its prefix lands in a range.
        val spotHash = encodeGeohash(qLat, qLon + 0.0035, precision = 9)
        val covered = bounds.any { (start, end) -> spotHash >= start && spotHash < end }
        assertTrue(covered, "a spot just across the boundary must be inside the neighbourhood query")
    }
}
