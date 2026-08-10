package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.GpsPoint

/**
 * Durable record of the CURRENT trip's driven route — the dense GPS fixes the detection service
 * samples while tracking, persisted so the drawn route survives the app going to background or being
 * cold-started mid-trip. [DET-ROUTE-TRACK-001]
 *
 * Distinct from [TripTrail]: that is a sparse, forensic one-shot breadcrumb ring for the reconcile
 * safety net; this is the dense driving polyline, fed from the service's live tracking stream and
 * restored by the trip controller to draw the real route instead of reconstructing one from the
 * parked spot.
 *
 * Fed only by the service (single active tracked trip → a single buffer, no per-vehicle key). The
 * buffer self-segments by a long time-gap between fixes so a fresh trip never inherits the previous
 * one's tail, and is cleared explicitly when a trip terminates. Platform-backed (SharedPreferences on
 * Android); null where no platform store is wired (iOS today).
 */
interface DrivingRouteStore {
    /** Append a tracked driving fix. A long silence since the last point starts a new trip; a fix
     *  that barely moved is decimated away. */
    fun append(point: GpsPoint)

    /** The current trip's recorded route (empty when none). */
    fun points(): List<GpsPoint>

    /** Drop the recorded route — the trip terminated (park confirmed or aborted). */
    fun clear()
}
