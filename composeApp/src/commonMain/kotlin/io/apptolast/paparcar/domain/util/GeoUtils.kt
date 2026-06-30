package io.apptolast.paparcar.domain.util

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
