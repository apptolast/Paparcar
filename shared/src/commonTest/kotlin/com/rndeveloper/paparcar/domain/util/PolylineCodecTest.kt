package com.rndeveloper.paparcar.domain.util

import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolylineCodecTest {

    private fun p(lat: Double, lon: Double) = GpsPoint(lat, lon, 0f, 0L, 0f)

    @Test
    fun `should encode the canonical Google example`() {
        // The reference example from Google's Encoded Polyline Algorithm docs.
        val points = listOf(p(38.5, -120.2), p(40.7, -120.95), p(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", PolylineCodec.encode(points))
    }

    @Test
    fun `should round-trip a route within 1e-5 degrees`() {
        val route = listOf(
            p(36.60012, -6.23001),
            p(36.60120, -6.23050),
            p(36.60234, -6.22990),
            p(36.60310, -6.22870),
        )
        val decoded = PolylineCodec.decode(PolylineCodec.encode(route))
        assertEquals(route.size, decoded.size)
        route.forEachIndexed { i, original ->
            assertTrue(abs(original.latitude - decoded[i].latitude) < 1e-5, "lat[$i] drifted")
            assertTrue(abs(original.longitude - decoded[i].longitude) < 1e-5, "lon[$i] drifted")
        }
    }

    @Test
    fun `should treat empty and null as no route`() {
        assertEquals("", PolylineCodec.encode(emptyList()))
        assertEquals(emptyList(), PolylineCodec.decode(""))
        assertEquals(emptyList(), PolylineCodec.decode(null))
    }

    // ── lengthMeters [VEH-STATS-SAY-SOMETHING-USEFUL-001] ────────────────────

    @Test
    fun `should measure a two-point route with haversine accuracy`() {
        // 0.01° of latitude ≈ 1111.9 m at any longitude.
        val encoded = PolylineCodec.encode(listOf(p(36.60, -6.23), p(36.61, -6.23)))
        val length = PolylineCodec.lengthMeters(encoded)!!
        assertTrue(abs(length - 1112f) < 5f, "expected ~1112 m, was $length")
    }

    @Test
    fun `should return null length when there is nothing to measure`() {
        // No route / single fix = "unknown distance", never 0 — a stat must not fake a number.
        assertEquals(null, PolylineCodec.lengthMeters(null))
        assertEquals(null, PolylineCodec.lengthMeters(""))
        assertEquals(null, PolylineCodec.lengthMeters(PolylineCodec.encode(listOf(p(36.6, -6.23)))))
    }

    @Test
    fun `should stay compact for a long route`() {
        // ~400 points ~11 m apart → a real urban trip. Must encode to well under 4 KB.
        val route = (0 until 400).map { p(36.6 + it * 0.0001, -6.23 + it * 0.00005) }
        val encoded = PolylineCodec.encode(route)
        assertTrue(encoded.length < 4000, "expected compact encoding, was ${encoded.length} chars")
    }
}
