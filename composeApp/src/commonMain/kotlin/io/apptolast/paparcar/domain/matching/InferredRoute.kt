package io.apptolast.paparcar.domain.matching

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.RouteInferenceResolution

/**
 * One drawable piece of a stored route: consecutive polyline points plus whether they are measured
 * movement or a road-INFERRED reconstruction of a data hole. Separate segments are never joined
 * when drawn — a missing connector IS the honest cut. [ROUTE-GAP-HONEST-001]
 */
data class RouteSegment(
    val points: List<GpsPoint>,
    val inferred: Boolean,
)

/**
 * Compact persistence of a [TrailMapMatcher.MatchedRoute]'s provenance alongside its flat encoded
 * polyline: one TEXT column holding comma-separated tokens — `a:b` (points a..b are an inferred
 * bridge, anchors included) and `a!` (the line cuts after point a). Null/blank = fully measured
 * route (the overwhelmingly common case costs nothing). [ROUTE-GAP-HONEST-001]
 */
object InferredRoute {

    private const val SPAN_SEPARATOR = ","
    private const val RANGE_SEPARATOR = ":"
    private const val CUT_SUFFIX = "!"

    /** Null when there is nothing to record — a blank column keeps legacy readers untouched. */
    fun encode(inferredSpans: List<IntRange>, cuts: List<Int>): String? {
        if (inferredSpans.isEmpty() && cuts.isEmpty()) return null
        val tokens = inferredSpans.map { "${it.first}$RANGE_SEPARATOR${it.last}" } + cuts.map { "$it$CUT_SUFFIX" }
        return tokens.joinToString(SPAN_SEPARATOR)
    }

    fun decodeSpans(encoded: String?): List<IntRange> =
        tokens(encoded).filter { it.contains(RANGE_SEPARATOR) }.mapNotNull { token ->
            val (a, b) = token.split(RANGE_SEPARATOR).takeIf { it.size == 2 } ?: return@mapNotNull null
            val start = a.toIntOrNull() ?: return@mapNotNull null
            val end = b.toIntOrNull() ?: return@mapNotNull null
            if (start < end) start..end else null
        }

    fun decodeCuts(encoded: String?): List<Int> =
        tokens(encoded).filter { it.endsWith(CUT_SUFFIX) }
            .mapNotNull { it.removeSuffix(CUT_SUFFIX).toIntOrNull() }

    private fun tokens(encoded: String?): List<String> =
        encoded?.split(SPAN_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    /**
     * Splits a stored polyline into drawable [RouteSegment]s honouring the recorded provenance and
     * the user's verdict: measured stretches always draw; inferred bridges draw (dimmed while the
     * question is pending, normal once [RouteInferenceResolution.CONFIRMED]) unless
     * [RouteInferenceResolution.REJECTED] — then they are dropped and the line cuts at the holes.
     * Cuts split the measured line without any connector. Out-of-range or overlapping tokens are
     * ignored defensively (a foreign document must never crash rendering).
     */
    fun split(
        points: List<GpsPoint>,
        encoded: String?,
        resolution: RouteInferenceResolution?,
    ): List<RouteSegment> {
        if (points.size < 2) return emptyList()
        if (encoded.isNullOrBlank()) return listOf(RouteSegment(points, inferred = false))
        val spans = decodeSpans(encoded)
            .filter { it.first >= 0 && it.last <= points.lastIndex }
            .sortedBy { it.first }
        val cuts = decodeCuts(encoded).filter { it in 0 until points.lastIndex }.toSet()
        val drawInferred = resolution != RouteInferenceResolution.REJECTED
        val segments = ArrayList<RouteSegment>()
        var cursor = 0
        for (span in spans) {
            if (span.first < cursor) continue // overlapping garbage token
            if (span.first > cursor) addMeasured(segments, points, cursor, span.first, cuts)
            if (drawInferred) segments.add(RouteSegment(points.subList(span.first, span.last + 1), inferred = true))
            cursor = span.last
        }
        if (cursor < points.lastIndex) addMeasured(segments, points, cursor, points.lastIndex, cuts)
        return segments.filter { it.points.size >= 2 }
    }

    /** Measured stretch points[from..to], split (no connector) after every cut index inside it. */
    private fun addMeasured(
        segments: ArrayList<RouteSegment>,
        points: List<GpsPoint>,
        from: Int,
        to: Int,
        cuts: Set<Int>,
    ) {
        var start = from
        for (cut in cuts.filter { it in from until to }.sorted()) {
            segments.add(RouteSegment(points.subList(start, cut + 1), inferred = false))
            start = cut + 1
        }
        segments.add(RouteSegment(points.subList(start, to + 1), inferred = false))
    }
}
