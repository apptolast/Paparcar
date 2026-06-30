package io.apptolast.paparcar.domain.matching

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.places.RoadWay
import io.apptolast.paparcar.domain.util.haversineMeters
import kotlin.math.PI
import kotlin.math.cos

/**
 * Pure, free map-matching of the live trip trail onto OSM roads. v1: each fix is snapped to the
 * nearest point on the nearest road segment, so the polyline sits on the street instead of cutting
 * across blocks from raw GPS drift. Fixes farther than [MAX_SNAP_METERS] from any road (e.g. GPS in
 * a plaza / parking lot, or sparse rural roads) are kept as-is rather than yanked onto a wrong road.
 *
 * Known v1 limitation: snapping is per-point (no path consistency), so it can jump between parallel
 * roads or cut a corner between two snapped points that sit on the same curved road. A v2 would walk
 * the road geometry between consecutive snaps on the same way. [ROUTE-SNAP-001]
 */
object TrailMapMatcher {

    /** Beyond this distance to the nearest road, keep the raw fix (don't snap to a far/wrong road). */
    const val MAX_SNAP_METERS = 30.0

    fun snap(points: List<GpsPoint>, roads: List<RoadWay>): List<GpsPoint> {
        if (roads.isEmpty() || points.size < 2) return points
        return points.map { snapPoint(it, roads) }
    }

    private fun snapPoint(p: GpsPoint, roads: List<RoadWay>): GpsPoint {
        // Local equirectangular scale: longitude degrees shrink by cos(lat) so planar distances are
        // ~isotropic over the small bbox of one trip. Roads are nearby, so one cosLat is enough.
        val cosLat = cos(p.latitude * PI / 180.0)
        var bestLat = 0.0
        var bestLon = 0.0
        var bestDist = MAX_SNAP_METERS
        var found = false
        for (way in roads) {
            val v = way.points
            for (i in 0 until v.size - 1) {
                val (lat, lon) = projectOntoSegment(
                    p.latitude, p.longitude,
                    v[i].latitude, v[i].longitude,
                    v[i + 1].latitude, v[i + 1].longitude,
                    cosLat,
                )
                val d = haversineMeters(p.latitude, p.longitude, lat, lon)
                if (d < bestDist) {
                    bestDist = d
                    bestLat = lat
                    bestLon = lon
                    found = true
                }
            }
        }
        return if (found) p.copy(latitude = bestLat, longitude = bestLon) else p
    }

    /** Closest point (lat, lon) on segment a→b to point p, in the local planar frame. */
    private fun projectOntoSegment(
        plat: Double, plon: Double,
        alat: Double, alon: Double,
        blat: Double, blon: Double,
        cosLat: Double,
    ): Pair<Double, Double> {
        val ax = alon * cosLat; val ay = alat
        val bx = blon * cosLat; val by = blat
        val px = plon * cosLat; val py = plat
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val projX = ax + t * dx
        val projY = ay + t * dy
        return projY to (projX / cosLat) // back to (lat, lon)
    }
}
