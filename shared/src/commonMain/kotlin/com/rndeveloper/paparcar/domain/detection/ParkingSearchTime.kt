package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * Measures the time a trip spent LOOKING for a spot — the pivot's flagship figure ("tiempo perdido
 * buscando plaza"). [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001]
 *
 * **The clock**: the search starts the LAST time the trip re-entered [SEARCH_RADIUS_METERS] of
 * where it finally parked, and ends at the MEASURED STOP — the last recorded fix that is
 * physically at the pin — never at the anchor's own clock. Three confirm paths (manual pin,
 * "Sí, he aparcado", unattended timeout) carry a wall-clock anchor timestamp, which is
 * confirmation LATENCY: a pin the user marks six minutes after arriving must not book those six
 * minutes as searching. The stop is found by geometry, so the same rule holds for every lane.
 * [STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001]
 *
 * **The verdict is three-valued, and zero is a measurement**:
 *  - `null` — unmeasurable: no witnessed arrival at the pin, no fix ever outside the radius (a
 *    short hop cannot distinguish searching from arriving), missing clocks, or a result outside
 *    [0, [MAX_PLAUSIBLE_SEARCH_MS]]. Absent, never invented.
 *  - `0` — measured DIRECT arrival: the driven line inside the radius is essentially the straight
 *    way in ([DIRECT_EXCESS_FACTOR]/[MIN_EXCESS_METERS]); red lights on the way to your door are
 *    not a search. The old "~30–60 s floor a direct arrival pays" is retired: it booked the final
 *    approach as searching on every single trip.
 *  - `> 0` — measured search: the trip demonstrably drove laps inside the radius (path length far
 *    beyond the straight line in) — the figure is the driving time from the last re-entry to the
 *    measured stop.
 *
 * **Why it runs at confirm and nowhere else**: the stored polyline carries no timestamps
 * ([com.rndeveloper.paparcar.domain.util.PolylineCodec] encodes lat/lon), so this figure is
 * measurable exactly once — while the recorded route's raw points still carry their clocks. Like
 * `routeDistanceMeters`, it is measured at the moment the data exists and stamped on the session.
 */
object ParkingSearchTime {

    /** Inside this distance of the final spot the trip is "arriving or searching"; beyond it, it
     *  is still travelling. A named, calibratable constant — not revealed truth. */
    const val SEARCH_RADIUS_METERS = 300.0

    /** Beyond this the figure describes something else (a stop the segmentation missed, a clock
     *  jump) — an anomaly, not a search. */
    const val MAX_PLAUSIBLE_SEARCH_MS = 45 * 60_000L

    /** A fix within this of the pin is "at the car": GPS noise + the store's 12 m decimation +
     *  pin-drag slack. The LAST such fix is the measured stop — later fixes are the walk away
     *  (or the wait for a nudge answer), not the drive. */
    const val STOP_NEAR_METERS = 35.0

    /** When even the closest recorded fix is farther than this from the pin, the route never
     *  witnessed the arrival (GPS died early, pin dragged away) — the stop instant is unknown and
     *  the search is unmeasurable, not zero. */
    const val ARRIVAL_WITNESS_METERS = 75.0

    /** Direct-vs-searching verdict: it is a SEARCH only when the driven line inside the radius is
     *  longer than the straight line in by BOTH bounds — a ratio (grids and one-way detours reach
     *  ~1.4–1.7× honestly) and an absolute excess (a lap around a block adds ~300–500 m). Below
     *  either bound the arrival is DIRECT and the measured figure is 0, not a small invented one.
     *  Asymmetric on purpose: a missed short search (false negative) over booked laps that never
     *  happened (false positive). */
    const val DIRECT_EXCESS_FACTOR = 1.8
    const val MIN_EXCESS_METERS = 250.0

    /** Below this the "search" wouldn't outlast a red light — measured, but not evidence of
     *  hunting. Rounds down to a measured 0. */
    const val MIN_SEARCH_MS = 90_000L

    /**
     * @param drivenPoints the recorded route's raw fixes, in driving order, clocks intact.
     * @param anchor the pin being confirmed; only its POSITION is trusted. Its timestamp is used
     *   solely as an upper cut (fixes after it belong to no drive) — on the manual/user/timeout
     *   lanes it is a wall clock, so it must never END the search clock.
     */
    fun measure(drivenPoints: List<GpsPoint>, anchor: GpsPoint): Long? {
        if (anchor.timestamp <= 0L) return null
        val driven = drivenPoints.filter { it.timestamp in 1..anchor.timestamp }
        if (driven.isEmpty()) return null

        val distances = driven.map {
            haversineMeters(it.latitude, it.longitude, anchor.latitude, anchor.longitude)
        }

        // 1 · The measured STOP: the last fix that is at the pin. Walk-away fixes drift beyond
        // STOP_NEAR_METERS, so "last near" lands on the parked car even when the buffer kept
        // sampling for minutes afterwards. When GPS was sparse at the end, accept the closest
        // approach as the stop as long as the arrival itself was witnessed.
        val minDistance = distances.min()
        if (minDistance > ARRIVAL_WITNESS_METERS) return null // arrival never witnessed
        val nearBound = maxOf(STOP_NEAR_METERS, minDistance)
        val stopIndex = distances.indexOfLast { it <= nearBound }
        val stopAtMs = driven[stopIndex].timestamp

        // 2 · The ENTRY: the search clock starts when the trip RE-ENTERED the radius for the last
        // time BEFORE the stop. "Last time far" rather than "first time near" on purpose: a
        // through-street that passes the destination early in the trip must not start the clock —
        // only the final approach may. Fixes after the stop (the walk) must not reset it either.
        val lastFarIndex = (0..stopIndex).lastOrNull { distances[it] > SEARCH_RADIUS_METERS }
            ?: return null // never left the neighbourhood — unmeasurable, not zero
        val entryIndex = lastFarIndex + 1
        if (entryIndex > stopIndex) return 0L // the far fix was the stop's own predecessor: no time inside
        val searchMs = stopAtMs - driven[entryIndex].timestamp
        if (searchMs !in 0..MAX_PLAUSIBLE_SEARCH_MS) return null // an anomaly is not a figure

        // 3 · Direct or searching — decided by MEASURED geometry, not by time alone (time cannot
        // tell a red light from a lap; the driven shape can).
        val beelineMeters = haversineMeters(
            driven[entryIndex].latitude, driven[entryIndex].longitude,
            anchor.latitude, anchor.longitude,
        )
        val drivenInsideMeters = (entryIndex + 1..stopIndex).sumOf {
            haversineMeters(
                driven[it - 1].latitude, driven[it - 1].longitude,
                driven[it].latitude, driven[it].longitude,
            )
        }
        val demonstrablySearching = searchMs >= MIN_SEARCH_MS &&
            drivenInsideMeters > beelineMeters * DIRECT_EXCESS_FACTOR &&
            drivenInsideMeters - beelineMeters > MIN_EXCESS_METERS
        return if (demonstrablySearching) searchMs else 0L
    }
}
