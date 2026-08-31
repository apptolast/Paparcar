package com.rndeveloper.paparcar.presentation.vehicles

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
