package com.rndeveloper.paparcar.domain.fuel

import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation

/**
 * Which forecourt in a list deserves to be pointed at: the CHEAPEST and the NEAREST.
 * [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001]
 *
 * A pure function and not a use case on purpose — its result never appears in a diagnostic and
 * never changes a pin: it only decides what a row says about itself. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ## Why "nearest" is not redundant with the order
 *
 * The list is sorted with `compareBy { price == null }.thenBy { distance }`: priced stations first,
 * each block by distance. So the first row is the nearest **that has a price**, and in a country
 * whose feed carries none — which is most of the world, and the reason the owner asked for this —
 * the genuinely nearest forecourt can sit far down the list with no price at all. The badge names
 * what the ordering cannot.
 *
 * ## What it refuses to claim
 *
 * - **No superlative over one candidate.** With a single station there is no "cheapest": there is
 *   just a station. Same for "nearest".
 * - **No cheapest on a tie.** If two forecourts charge the same, neither is *the* cheapest, and a
 *   badge saying otherwise is a lie the user can check at the pump. Ties drop the badge entirely.
 * - **Distance ties do not happen in practice** (metres from a GPS fix), but the same rule applies
 *   for the same reason.
 * - **No cheapest across currencies.** [Money] carries one, and near a border the same list can hold
 *   euros and pounds. 1.42 £/l is not cheaper than 1.65 €/l because the number is smaller, so a
 *   mixed list gets no cheapest badge at all. The nearest one still stands: metres are metres.
 */
data class FuelStandouts(
    /** `stationKey` of the one that charges least — null when nobody can claim it. */
    val cheapestKey: String? = null,
    /** `stationKey` of the closest one — null when there is nothing to be closer than. */
    val nearestKey: String? = null,
) {
    /** True when one forecourt is both, which the UI shows as ONE badge rather than two. */
    val bothAreTheSame: Boolean =
        cheapestKey != null && cheapestKey == nearestKey

    fun badgeFor(stationKey: String): FuelStandoutBadge? = when {
        bothAreTheSame && stationKey == cheapestKey -> FuelStandoutBadge.CHEAPEST_AND_NEAREST
        stationKey == cheapestKey -> FuelStandoutBadge.CHEAPEST
        stationKey == nearestKey -> FuelStandoutBadge.NEAREST
        else -> null
    }

    companion object {
        val NONE = FuelStandouts()
    }
}

/** What a row can wear. One badge per row, never two — a row is a name, not a scoreboard. */
enum class FuelStandoutBadge {
    CHEAPEST,
    NEAREST,
    CHEAPEST_AND_NEAREST,
}

/**
 * Reads [FuelStandouts] off a list. Order-independent: it never assumes the caller sorted anything,
 * because the sort is a presentation choice and this is not.
 */
fun List<NearbyFuelStation>.fuelStandouts(): FuelStandouts {
    if (size < 2) return FuelStandouts.NONE

    val priced = filter { it.price != null }
    val oneCurrency = priced.map { it.price!!.perUnit.currency }.distinct().size == 1
    val cheapest = priced
        .takeIf { oneCurrency }
        ?.minByOrNull { it.price!!.perUnit.amount }
        ?.takeIf { winner ->
            // Strictly cheaper than every OTHER priced station, or nobody wears it.
            priced.none {
                it.stationKey != winner.stationKey &&
                    it.price!!.perUnit.amount <= winner.price!!.perUnit.amount
            }
        }
    val nearest = minByOrNull { it.distanceMeters }
        ?.takeIf { winner ->
            none { it.stationKey != winner.stationKey && it.distanceMeters <= winner.distanceMeters }
        }

    return FuelStandouts(cheapestKey = cheapest?.stationKey, nearestKey = nearest?.stationKey)
}
