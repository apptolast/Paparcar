package com.rndeveloper.paparcar.data.cache

import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.round

/**
 * The geometry of *having asked* — the pure half of the disk cache.
 * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * Kept out of the Room classes on purpose: whether a saved disc can answer a question, and what a
 * fresh answer means for what was saved before it, are decisions worth testing without a database.
 */

/** Layer names. Coverage is never shared between them: two questions, two sets of discs. */
object CoverageLayer {
    const val PUBLIC_PARKINGS = "parkings"
    const val FUEL_STATIONS = "fuel"
}

/**
 * The identity of a disc: same layer, same radius and same ~110 m cell means the same question, so
 * asking again REPLACES the stamp instead of adding a second row. That is what keeps the coverage
 * table bounded while the map is panned.
 *
 * The rounding is [locationCacheKey]'s, and for the same measured reason: standing still makes GPS
 * wander by tens of metres, and an exact-coordinate identity would file every reading as a brand new
 * question.
 */
fun coverageKey(layer: String, latitude: Double, longitude: Double, radiusMeters: Int): String {
    val lat = round(latitude * KEY_PRECISION) / KEY_PRECISION
    val lon = round(longitude * KEY_PRECISION) / KEY_PRECISION
    return "$layer|$radiusMeters|$lat,$lon"
}

/** Three decimal places of a degree ≈ 110 m — same cell size as [locationCacheKey]. */
private const val KEY_PRECISION = 1_000.0

/**
 * A recorded question: everything of one kind within [radiusMeters] of a point, as of [fetchedAt].
 */
data class CoverageDisc(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val fetchedAt: Long,
)

/**
 * Whether this disc can answer a query **on its own and in full** — containment, not overlap.
 *
 * ⛔ Overlap would be the wrong test and the bug would be invisible. Two 1.500 m discs 1.400 m apart
 * overlap, but most of the second one's area was never asked about; answering from the first would
 * serve a partial list as if it were complete, which is the same "hole that reads as *there is
 * nothing here*" the coverage table exists to prevent. It is also the discipline
 * [locationCacheKey] already applies to the radius: being served a smaller answer than you asked for
 * silently shrinks the list.
 *
 * The cost is that the disk answers only near where it was filled, which is exactly the case this
 * feature is for: coming back to where you were, with no radio. Joint coverage by several discs is
 * a real improvement and deliberately not attempted here — see the ticket.
 */
fun CoverageDisc.covers(
    queryLatitude: Double,
    queryLongitude: Double,
    queryRadiusMeters: Int,
): Boolean {
    val centreGap = haversineMeters(latitude, longitude, queryLatitude, queryLongitude)
    return centreGap + queryRadiusMeters <= radiusMeters
}

/** The rectangle SQL can filter on, as `minLat, maxLat, minLon, maxLon`. */
data class BoundingBox(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
)

/**
 * The bounding box that CONTAINS a circle — deliberately wider than the circle, never narrower.
 *
 * It is only the cheap first cut: SQL takes the rectangle, and the caller still filters by real
 * distance afterwards. Erring wide costs a few extra rows to test; erring narrow would silently drop
 * facilities that are genuinely in range, and nothing downstream could notice.
 */
fun boundingBox(latitude: Double, longitude: Double, radiusMeters: Int): BoundingBox {
    val latDelta = radiusMeters / METERS_PER_DEGREE_LATITUDE
    // cos() collapses towards the poles, so a fixed floor keeps the longitude span from exploding
    // (and from dividing by zero) where degrees of longitude stop meaning much anyway.
    val shrink = max(abs(cos(latitude * PI / 180.0)), MIN_LONGITUDE_SCALE)
    val lonDelta = radiusMeters / (METERS_PER_DEGREE_LATITUDE * shrink)
    return BoundingBox(
        minLatitude = latitude - latDelta,
        maxLatitude = latitude + latDelta,
        minLongitude = longitude - lonDelta,
        maxLongitude = longitude + lonDelta,
    )
}

private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
private const val MIN_LONGITUDE_SCALE = 0.01

/**
 * How far a response can honestly claim to have looked.
 *
 * An Overpass query caps its output (`out center 80` for car parks), so a response that came back
 * **at the cap** was truncated: beyond its farthest element it saw nothing and knows nothing. Storing
 * the requested radius in that case would let the disc claim an area it never covered, and the next
 * cold start would report an empty ring as a fact. A short response saw the whole disc, so it keeps
 * the radius it asked for.
 */
fun effectiveRadiusMeters(
    requestedRadiusMeters: Int,
    centreLatitude: Double,
    centreLongitude: Double,
    resultCoordinates: List<Pair<Double, Double>>,
    /**
     * Whether the feed stopped at its own cap. ⛔ Passed in rather than inferred from a count,
     * because the cap is not one number: Overpass parkings stop at 80, Overpass fuel at 60, France
     * at 100, and Spain never stops because it filters a whole province on the phone. Only the
     * source that answered knows which of those it was.
     */
    isTruncated: Boolean,
): Int {
    if (!isTruncated || resultCoordinates.isEmpty()) return requestedRadiusMeters
    val farthest = resultCoordinates.maxOf { (lat, lon) ->
        haversineMeters(centreLatitude, centreLongitude, lat, lon)
    }
    return minOf(requestedRadiusMeters, farthest.toInt())
}
