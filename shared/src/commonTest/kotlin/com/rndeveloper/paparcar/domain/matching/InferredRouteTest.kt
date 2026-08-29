package com.rndeveloper.paparcar.domain.matching

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.RouteInferenceResolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InferredRouteTest {

    private fun pts(n: Int) = (0 until n).map { GpsPoint(36.6 + it * 0.001, -6.24, 0f, 0L, 0f) }

    @Test
    fun `should round-trip spans and cuts through the encoded string`() {
        val encoded = InferredRoute.encode(listOf(2..5, 8..10), listOf(12))
        assertEquals(listOf(2..5, 8..10), InferredRoute.decodeSpans(encoded))
        assertEquals(listOf(12), InferredRoute.decodeCuts(encoded))
    }

    @Test
    fun `should encode nothing when there is nothing to record`() {
        assertNull(InferredRoute.encode(emptyList(), emptyList()))
    }

    @Test
    fun `should split a route into measured and inferred segments sharing their anchors`() {
        val points = pts(10)
        val segments = InferredRoute.split(points, InferredRoute.encode(listOf(3..6), emptyList()), resolution = null)

        assertEquals(3, segments.size)
        assertEquals(points.subList(0, 4), segments[0].points); assertTrue(!segments[0].inferred)
        assertEquals(points.subList(3, 7), segments[1].points); assertTrue(segments[1].inferred)
        assertEquals(points.subList(6, 10), segments[2].points); assertTrue(!segments[2].inferred)
    }

    @Test
    fun `should drop the inferred segments once the user rejects them`() {
        val points = pts(10)
        val segments = InferredRoute.split(
            points,
            InferredRoute.encode(listOf(3..6), emptyList()),
            resolution = RouteInferenceResolution.REJECTED,
        )

        assertEquals(2, segments.size)
        assertTrue(segments.none { it.inferred }, "expected the rejected reconstruction never drawn")
    }

    @Test
    fun `should break the measured line at a cut without any connector`() {
        val points = pts(8)
        val segments = InferredRoute.split(points, InferredRoute.encode(emptyList(), listOf(3)), resolution = null)

        assertEquals(2, segments.size)
        assertEquals(points.subList(0, 4), segments[0].points)
        assertEquals(points.subList(4, 8), segments[1].points)
    }

    @Test
    fun `should return one measured segment when there is no provenance`() {
        val points = pts(5)
        val segments = InferredRoute.split(points, encoded = null, resolution = null)
        assertEquals(listOf(RouteSegment(points, inferred = false)), segments)
    }

    @Test
    fun `should ignore malformed or out-of-range tokens defensively`() {
        val points = pts(5)
        val segments = InferredRoute.split(points, "banana,9:99,3:1,40!,2:4", resolution = null)
        // Only the valid 2:4 span survives; nothing crashes.
        assertEquals(2, segments.size)
        assertTrue(segments[1].inferred)
        assertEquals(points.subList(2, 5), segments[1].points)
    }
}
