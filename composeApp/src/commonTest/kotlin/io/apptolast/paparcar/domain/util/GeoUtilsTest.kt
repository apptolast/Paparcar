package io.apptolast.paparcar.domain.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertTrue

class GeoUtilsTest {

    @Test
    fun `haversine is zero for identical points`() {
        assertTrue(haversineMeters(40.4168, -3.7038, 40.4168, -3.7038) < 0.001)
    }

    @Test
    fun `haversine matches known short distance within tolerance`() {
        // ~111.2 m per 0.001 deg of latitude near the equator-ish; Madrid latitude.
        val d = haversineMeters(40.4168, -3.7038, 40.4258, -3.7038) // +0.009 deg lat
        // 0.009 deg * ~111_320 m/deg ≈ 1002 m
        assertTrue(abs(d - 1001.0) < 25.0, "expected ~1001 m, got $d")
    }

    @Test
    fun `boundingBox latitude half-extent equals radius over metres-per-degree`() {
        val radius = 500.0
        val bbox = boundingBox(40.4168, -3.7038, radius)
        val expectedDeltaLat = radius / METERS_PER_DEGREE_LAT
        assertTrue(abs((bbox.maxLat - 40.4168) - expectedDeltaLat) < 1e-9)
        assertTrue(abs((40.4168 - bbox.minLat) - expectedDeltaLat) < 1e-9)
    }

    @Test
    fun `boundingBox longitude half-extent widens with latitude (cos scaling)`() {
        val radius = 500.0
        val lat = 60.0
        val bbox = boundingBox(lat, 10.0, radius)
        val expectedDeltaLon = radius / (METERS_PER_DEGREE_LAT * cos(lat * PI / 180.0))
        assertTrue(abs((bbox.maxLon - 10.0) - expectedDeltaLon) < 1e-9)
        // At 60°N, cos(60°)=0.5 → lon delta is ~2x the lat delta.
        val deltaLat = radius / METERS_PER_DEGREE_LAT
        assertTrue((bbox.maxLon - 10.0) > deltaLat * 1.9)
    }
}
