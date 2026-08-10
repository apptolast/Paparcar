package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * Pure accumulation rule for the persisted driving route [DrivingRouteStore]. Kept platform-free so
 * the segmentation/decimation/cap logic is unit-tested without a device store. [DET-ROUTE-TRACK-001]
 */
object DrivingRoute {

    /** Sliding cap: a very long drive drops its oldest points. A few hundred points draw a smooth
     *  line and keep the persisted blob a few KB. */
    const val MAX_POINTS = 500

    /** A fix nearer than this to the last kept one carries no new shape — decimated away. Bounds the
     *  point count when the car crawls or idles, and smooths GPS jitter. */
    const val MIN_POINT_DISTANCE_M = 12.0

    /** Silence longer than this since the last fix means the previous trip ended (the service stops
     *  sampling when detection terminates); the next fix starts a fresh route so a new trip never
     *  inherits the previous one's tail. A safety net beneath the explicit [DrivingRouteStore.clear]
     *  on trip end, covering the rare case where a process death skipped that clear. */
    const val NEW_TRIP_GAP_MS = 20 * 60_000L

    /**
     * Returns the route after adding [point]. Returns [current] unchanged (same reference) when the
     * fix is decimated away, so a caller can skip persisting on no-op ticks.
     */
    fun append(current: List<GpsPoint>, point: GpsPoint): List<GpsPoint> {
        val last = current.lastOrNull() ?: return listOf(point)
        if (point.timestamp > 0L && last.timestamp > 0L &&
            point.timestamp - last.timestamp > NEW_TRIP_GAP_MS
        ) {
            return listOf(point)
        }
        val moved = haversineMeters(last.latitude, last.longitude, point.latitude, point.longitude)
        if (moved < MIN_POINT_DISTANCE_M) return current
        return (current + point).takeLast(MAX_POINTS)
    }
}
