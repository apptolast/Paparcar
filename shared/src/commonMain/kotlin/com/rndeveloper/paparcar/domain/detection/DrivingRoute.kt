package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.haversineMeters

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

    /** [ROUTE-FIX-ACCURACY-001] A fix this imprecise measures nothing drawable — rejected at ingest,
     *  including as a trip's first point (a cold start must not seed a garbage origin; the true
     *  origin is seeded from the previous parking at confirm). Field 2026-08-14: acc 98–157 m fixes
     *  interleaved mid-stream bent the urban line into off-street loops. Fixes at or below the bound
     *  all enter: the matcher weighs each by its own accuracy (per-point σ), so a mediocre fix still
     *  anchors a stretch where it is the only data. */
    const val HOPELESS_ACCURACY_METERS = 100f

    /** Silence longer than this since the last fix means the previous trip ended (the service stops
     *  sampling when detection terminates); the next fix starts a fresh route so a new trip never
     *  inherits the previous one's tail. A safety net beneath the explicit [DrivingRouteStore.clear]
     *  on trip end, covering the rare case where a process death skipped that clear. */
    const val NEW_TRIP_GAP_MS = 20 * 60_000L

    /** The parking anchor is appended as the route's final vertex only when the last driven fix
     *  stops short of it by at least the floor (below it the line already ends at the car within
     *  decimation noise) and by at most the ceiling (a farther anchor belongs to another story —
     *  never stretch the line to it). Mirrors the origin-prepend plausibility window
     *  (`MIN/MAX_ORIGIN_PREPEND_METERS` in ConfirmParkingUseCase). [ROUTE-END-AT-CAR-001] */
    const val MIN_ANCHOR_APPEND_METERS = 15.0
    const val MAX_ANCHOR_APPEND_METERS = 5_000.0

    /**
     * Returns the route after adding [point]. Returns [current] unchanged (same reference) when the
     * fix is decimated away, so a caller can skip persisting on no-op ticks.
     */
    fun append(current: List<GpsPoint>, point: GpsPoint): List<GpsPoint> {
        if (point.accuracy > HOPELESS_ACCURACY_METERS) return current // [ROUTE-FIX-ACCURACY-001]
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

    /**
     * The stored route is the DRIVING route — invariant: it ends at the parking anchor, never at
     * the pedestrian. The store keeps sampling while the user walks away from the parked car with
     * GPS still live (egress proof, hold window), so the raw buffer carries a pedestrian tail past
     * the spot. [anchor] is the pin being saved, whose fix timestamp is the measured end of
     * driving (the stop `bestStopLocation` was captured at): every fix recorded after it is the
     * walk, not the drive — dropped. The anchor itself then becomes the final vertex when the
     * remaining line stops short of it (plausibility-windowed), so the polyline terminates at the
     * car. An anchor without a real timestamp (synthetic callers) trims nothing — the append alone
     * still ends the line at the pin. [ROUTE-END-AT-CAR-001]
     */
    fun endAtAnchor(points: List<GpsPoint>, anchor: GpsPoint): List<GpsPoint> {
        val driven = if (anchor.timestamp > 0L) {
            points.takeWhile { it.timestamp <= anchor.timestamp }
        } else {
            points
        }
        val last = driven.lastOrNull() ?: return driven
        val gapM = haversineMeters(last.latitude, last.longitude, anchor.latitude, anchor.longitude)
        return if (gapM in MIN_ANCHOR_APPEND_METERS..MAX_ANCHOR_APPEND_METERS) driven + anchor else driven
    }
}
