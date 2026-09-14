package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * Measures the time a trip spent LOOKING for a spot — the pivot's flagship figure ("tiempo perdido
 * buscando plaza"). [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001]
 *
 * **The rule**: the search starts the LAST time the trip was farther than [SEARCH_RADIUS_METERS]
 * from where it finally parked, and ends at the anchor. A direct arrival pays a small floor
 * (~30–60 s of final approach — documented, consistent, and what moves period over period is the
 * delta); every lap around the block adds its whole lap. "Last time far" rather than "first time
 * near" on purpose: a through-street that passes the destination early in the trip must not start
 * the clock — only the final approach may.
 *
 * **Why it runs at confirm and nowhere else**: the stored polyline carries no timestamps
 * ([com.rndeveloper.paparcar.domain.util.PolylineCodec] encodes lat/lon), so this figure is
 * measurable exactly once — while the recorded route's raw points still carry their clocks. Like
 * `routeDistanceMeters`, it is measured at the moment the data exists and stamped on the session.
 *
 * A measured PROXY, honestly bounded: no fix ever left the radius → null (a short hop cannot
 * distinguish searching from arriving); missing clocks → null; a result outside
 * [0, [MAX_PLAUSIBLE_SEARCH_MS]] → null (an anomaly is not a figure). Absent, never invented.
 */
object ParkingSearchTime {

    /** Inside this distance of the final spot the trip is "arriving or searching"; beyond it, it
     *  is still travelling. A named, calibratable constant — not revealed truth. */
    const val SEARCH_RADIUS_METERS = 300.0

    /** Beyond this the figure describes something else (a stop the segmentation missed, a clock
     *  jump) — an anomaly, not a search. */
    const val MAX_PLAUSIBLE_SEARCH_MS = 45 * 60_000L

    /**
     * @param drivenPoints the recorded route's raw fixes, in driving order, clocks intact.
     * @param anchor the pin being confirmed; its [GpsPoint.timestamp] ends the clock.
     */
    fun measure(drivenPoints: List<GpsPoint>, anchor: GpsPoint): Long? {
        if (anchor.timestamp <= 0L) return null
        // Only the DRIVE measures the search: fixes recorded after the anchor are the walk away.
        val driven = drivenPoints.filter { it.timestamp in 1..anchor.timestamp }
        val lastFarIndex = driven.indexOfLast {
            haversineMeters(it.latitude, it.longitude, anchor.latitude, anchor.longitude) > SEARCH_RADIUS_METERS
        }
        if (lastFarIndex < 0) return null // never left the neighbourhood — unmeasurable, not zero
        // The search clock starts when the trip RE-ENTERED the radius for the last time: the first
        // fix after the last far one (or the anchor itself when the far fix was the final one).
        val enteredAtMs = driven.getOrNull(lastFarIndex + 1)?.timestamp ?: anchor.timestamp
        val searchMs = anchor.timestamp - enteredAtMs
        return searchMs.takeIf { it in 0..MAX_PLAUSIBLE_SEARCH_MS }
    }
}
