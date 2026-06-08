package io.apptolast.paparcar.domain.model

/**
 * Tri-state assessment of how well a parking [Spot] suits a candidate vehicle.
 *
 * Drives the colored badge on the spot peek sheet and the marker tint in the
 * map list. Computed by [computeSpotFit] from the bidimensional (size + body)
 * categorisation persisted on both sides.
 */
enum class SpotFit {
    /** Same body shape as the spot — strongest match. */
    OPTIMAL,
    /** Different body but fits the spot's length envelope. */
    FITS,
    /** Vehicle is too large (longer or wider) for what the spot accommodates. */
    DOES_NOT_FIT,
    /** Either the spot or the vehicle lacks size data — show neutral copy. */
    UNKNOWN,
}

/**
 * Computes the [SpotFit] for [spot] given the user's currently active [vehicle].
 *
 * Algorithm in priority order:
 *  1. Missing data → UNKNOWN.
 *  2. Both [Spot] and [Vehicle] expose the same [CarbodyType] → OPTIMAL.
 *  3. The spot's length category equals the vehicle's AND the vehicle's
 *     minimum required width fits within the spot's body's minimum width
 *     → FITS (same length envelope).
 *  4. The vehicle's length category is shorter than the spot's → FITS
 *     (smaller car always fits a bigger free space).
 *  5. Otherwise → DOES_NOT_FIT.
 */
fun computeSpotFit(spot: Spot, vehicle: Vehicle?): SpotFit {
    if (vehicle == null || spot.sizeCategory == null) return SpotFit.UNKNOWN
    val userSize = vehicle.sizeCategory
    val spotSize = spot.sizeCategory

    // Identical carbody is the optimal match.
    if (spot.carbodyType != null && spot.carbodyType == vehicle.carbodyType) {
        return SpotFit.OPTIMAL
    }
    // Same length category — verify width compatibility when both bodies known.
    if (spotSize == userSize) {
        val userRules = vehicle.carbodyType?.getParkingRules()
        val spotRules = spot.carbodyType?.getParkingRules()
        if (userRules != null && spotRules != null &&
            userRules.minPlazaWidthMeters > spotRules.minPlazaWidthMeters) {
            return SpotFit.DOES_NOT_FIT
        }
        return SpotFit.FITS
    }
    // Smaller car always fits a bigger spot (ordinal ordering: smaller ordinal = smaller car).
    return if (userSize.ordinal <= spotSize.ordinal) SpotFit.FITS else SpotFit.DOES_NOT_FIT
}
