package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * A distance, and how many of the parkings in scope it was actually measured over.
 *
 * [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001] Not every parking carries a route:
 * `UserParking.routeDistanceMeters` is null for the Bluetooth lane (which wakes at the destination
 * and never traces the drive) and for legacy rows. A sum over those is a sum over a SUBSET, and
 * printing it beside a count of the whole scope describes something the user never asked about —
 * "12 parkings · 40 km" when three of the twelve produced those 40 km.
 *
 * So the sum never travels alone. [isComplete] is the only thing a call site needs to decide
 * whether the figure can be shown plainly or has to say what it covers.
 *
 * Moved from presentation in STATS-CORE-001: everything the stats derive from distance (litres,
 * kWh, cost, CO₂) inherits this coverage, so the type lives where those figures are computed.
 */
data class ScopedDistance(
    val meters: Float,
    /** Parkings that carried a measured route — always ≥ 1, or there would be no distance at all. */
    val fromParkings: Int,
    /** Parkings in the selected window, measured or not. */
    val ofParkings: Int,
) {
    /** True when every parking in scope contributed — the only case where the figure is a TOTAL. */
    val isComplete: Boolean get() = fromParkings >= ofParkings
}

/**
 * Distance covered by [sessions] with its coverage, or null when not one of them carries a route
 * ("no distance data" renders as nothing, never as 0 km). The ONE implementation —
 * `VehicleHistoryCalculator.sumDistanceMeters` delegates here. [STATS-CORE-001]
 */
fun sumMeasuredDistance(sessions: List<UserParking>): ScopedDistance? {
    val measured = sessions.mapNotNull { it.routeDistanceMeters }
    if (measured.isEmpty()) return null
    return ScopedDistance(
        meters = measured.sum(),
        fromParkings = measured.size,
        ofParkings = sessions.size,
    )
}
