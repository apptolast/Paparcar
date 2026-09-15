package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * A measured sum, and how many of the parkings in scope it was actually measured over.
 *
 * [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001] Not every parking carries a measurement:
 * `UserParking.routeDistanceMeters` is null for the Bluetooth lane that never traced its drive and
 * for legacy rows, and `UserParking.searchTimeMs` only exists when a fresh measured route was
 * attached at confirm time. A sum over those is a sum over a SUBSET, and printing it beside a count
 * of the whole scope describes something the user never asked about — "12 parkings · 40 km" when
 * three of the twelve produced those 40 km.
 *
 * So the sum never travels alone. [isComplete] is the only thing a call site needs to decide
 * whether the figure can be shown plainly or has to say what it covers.
 *
 * ⛔ **Generic over the value, and with no `ScopedDistance` alias left behind**
 * [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]. This type was born tied to ONE magnitude, and the
 * consequence arrived exactly as you would expect: the next measured magnitude — the time spent
 * hunting a spot — was added as a bare `Long` and reached the screen as a period total computed
 * over a subset. Coverage belongs to the IDEA of a measured sum, not to kilometres. A second name
 * for one shape is the door through which "this one is the distance thing" walks back in.
 */
data class ScopedMeasure<out T : Any>(
    val value: T,
    /** Parkings that carried the measurement — always ≥ 1, or there would be no figure at all. */
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
fun sumMeasuredDistance(sessions: List<UserParking>): ScopedMeasure<Float>? =
    sessions.scopedSum(UserParking::routeDistanceMeters) { it.sum() }

/**
 * Time [sessions] spent LOOKING for a spot, with its coverage — a SUM, not an average, because it
 * is the cost the period charged the user [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001].
 *
 * Coverage matters more here than anywhere else: the figure is stamped only when a fresh measured
 * route was attached at confirm, so a partial sum is the common case, not the edge one.
 */
fun sumMeasuredSearchTime(sessions: List<UserParking>): ScopedMeasure<Long>? =
    sessions.scopedSum(UserParking::searchTimeMs) { it.sum() }

/**
 * The one place a measured sum is built: take what exists, count it against the whole scope, and
 * return nothing at all when nothing was measured. A new measured magnitude is one line here and
 * cannot be born without its coverage — which is the whole point of the type.
 */
private inline fun <T : Any> List<UserParking>.scopedSum(
    measure: (UserParking) -> T?,
    sum: (List<T>) -> T,
): ScopedMeasure<T>? {
    val measured = mapNotNull(measure)
    if (measured.isEmpty()) return null
    return ScopedMeasure(value = sum(measured), fromParkings = measured.size, ofParkings = size)
}
