package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class TripTrailTest {

    private fun pt(lat: Double, lon: Double) = GpsPoint(lat, lon, 5f, 0L, 0f)

    @Test
    fun should_append_point_when_far_enough() {
        val trail = listOf(pt(36.0, -6.0))
        val result = TripTrail.append(trail, pt(36.001, -6.0)) // ~111 m apart
        assertEquals(2, result.size)
    }

    @Test
    fun should_skip_point_when_within_min_distance() {
        val trail = listOf(pt(36.0, -6.0))
        val result = TripTrail.append(trail, pt(36.00001, -6.0)) // ~1 m < MIN_POINT_DISTANCE_M
        assertEquals(trail, result)
    }

    @Test
    fun should_append_to_empty_trail() {
        val result = TripTrail.append(emptyList(), pt(36.0, -6.0))
        assertEquals(1, result.size)
    }

    @Test
    fun should_cap_at_max_points_keeping_most_recent() {
        var trail = emptyList<GpsPoint>()
        repeat(TripTrail.MAX_POINTS + 5) { i ->
            trail = TripTrail.append(trail, pt(36.0 + i * 0.001, -6.0)) // each ~111 m apart → all kept
        }
        assertEquals(TripTrail.MAX_POINTS, trail.size)
        // The tail (most recent) is retained, the oldest points dropped.
        assertEquals(pt(36.0 + (TripTrail.MAX_POINTS + 4) * 0.001, -6.0), trail.last())
    }
}
