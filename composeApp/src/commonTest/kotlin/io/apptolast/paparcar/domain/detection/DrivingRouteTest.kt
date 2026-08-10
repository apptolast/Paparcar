package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.GpsPoint
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
