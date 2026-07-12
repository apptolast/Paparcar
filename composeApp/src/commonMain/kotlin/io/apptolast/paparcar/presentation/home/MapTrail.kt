package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * Pure accumulator for the live map polyline drawn while a trip is detected (the navigation-app
 * style polyline behind the moving car). Kept OUT of the already-large [HomeViewModel] so the
 * decimation + cap policy is isolated and unit-testable. [TRIP-TRAIL-001]
 */
object MapTrail {
    /** Skip points closer than this to the previous one — keeps the polyline light without visible gaps.
     *  Lowered from 8 m so slow-speed turns keep more curvature detail for the spline to round.
     *  [ROUTE-SMOOTH-001] */
    const val MIN_POINT_DISTANCE_M = 4.0

    /** Bounded sliding window: on long trips the oldest points drop off the tail, the line never grows unbounded. */
    const val MAX_POINTS = 500

    /** Returns [trail] with [point] appended, unless it's within [MIN_POINT_DISTANCE_M] of the last point. */
    fun append(trail: List<GpsPoint>, point: GpsPoint): List<GpsPoint> {
        val last = trail.lastOrNull()
        if (last != null &&
            haversineMeters(last.latitude, last.longitude, point.latitude, point.longitude) < MIN_POINT_DISTANCE_M
        ) {
            return trail
        }
        val appended = trail + point
        return if (appended.size > MAX_POINTS) appended.takeLast(MAX_POINTS) else appended
    }
}
