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
}
