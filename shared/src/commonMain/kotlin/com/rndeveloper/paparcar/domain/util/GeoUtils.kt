package com.rndeveloper.paparcar.domain.util

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** Approximate metres per degree of latitude (roughly constant across the globe). */
const val METERS_PER_DEGREE_LAT = 111_111.0

private fun toRadians(deg: Double): Double = deg * PI / 180.0

/**
 * Returns the great-circle distance in metres between two WGS-84 coordinates
 * using the Haversine formula.
 */
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = toRadians(lat2 - lat1)
    val dLon = toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(toRadians(lat1)) * cos(toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** Axis-aligned lat/lon bounds. */
data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
)

/**
 * Returns an axis-aligned [BoundingBox] of half-extent [radiusMeters] around the given
 * coordinate. Longitude delta is scaled by cos(lat) so the box stays roughly square in
 * metres at any latitude. Used to pre-filter spots before the exact radius check.
 */
fun boundingBox(lat: Double, lon: Double, radiusMeters: Double): BoundingBox {
    val deltaLat = radiusMeters / METERS_PER_DEGREE_LAT
    val deltaLon = radiusMeters / (METERS_PER_DEGREE_LAT * cos(lat * PI / 180.0))
    return BoundingBox(
        minLat = lat - deltaLat,
        maxLat = lat + deltaLat,
        minLon = lon - deltaLon,
        maxLon = lon + deltaLon,
    )
}

/**
 * Distance in metres from a point to the nearest edge of [box], and **0 when the point is inside**.
 *
 * Standard axis-aligned box distance: the per-axis overshoot is zero while the coordinate is within
 * the box's span, so the result degrades to a plain point distance for a degenerate box (a box whose
 * corners coincide *is* a point). That property is what lets one threshold judge both an OSM node
 * and a whole supermarket polygon. [POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001]
 */
fun distanceToBoundingBoxMeters(lat: Double, lon: Double, box: BoundingBox): Double {
    val overshootLat = maxOf(box.minLat - lat, lat - box.maxLat, 0.0)
    val overshootLon = maxOf(box.minLon - lon, lon - box.maxLon, 0.0)
    val metersLat = overshootLat * METERS_PER_DEGREE_LAT
    val metersLon = overshootLon * METERS_PER_DEGREE_LAT * cos(toRadians(lat))
    return sqrt(metersLat * metersLat + metersLon * metersLon)
}
