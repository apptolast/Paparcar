package com.rndeveloper.paparcar.data.datasource.remote.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RouteGeometryPrivacyTest {

    @Test
    fun `should strip the trace and keep the figures`() {
        // [PRIV-A-TRACE-IS-NOT-A-STAT-001] A trace is not a stat: the numbers travel, the WHERE
        // does not.
        val dto = ParkingHistoryDto(
            id = "p1",
            latitude = 36.6,
            longitude = -6.2,
            accuracy = 8f,
            timestamp = 1L,
            isActive = false,
            routePolyline = "encoded",
            routeSnapped = true,
            routeInferredSpans = "spans",
            routeInferredResolution = "ACCEPTED",
            routeDistanceMeters = 3_400f,
            searchTimeMs = 180_000L,
        )

        val stripped = dto.withoutRouteGeometry()

        assertNull(stripped.routePolyline)
        assertFalse(stripped.routeSnapped, "null polyline + snapped=false is the established no-route shape")
        assertNull(stripped.routeInferredSpans)
        assertNull(stripped.routeInferredResolution)
        assertEquals(3_400f, stripped.routeDistanceMeters, "the distance is a figure - it travels")
        assertEquals(180_000L, stripped.searchTimeMs, "the search time is a figure - it travels")
        assertEquals(dto.id, stripped.id)
    }
}
