package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation

/**
 * The "where to refuel" section, as the sheet needs it. [FUEL-STATIONS-LIST-001]
 *
 * **Nothing is fetched until the section is opened.** Most sessions never ask this question, and a
 * province of stations downloaded for a list nobody unfolded is the user's data spent on our
 * convenience. [isExpanded] is therefore the trigger, not just a visual state.
 */
data class HomeFuelSlice(
    val isExpanded: Boolean = false,
    val isLoading: Boolean = false,
    val stations: List<NearbyFuelStation> = emptyList(),
    /** The feed's own hour, verbatim. Null where the source publishes no prices at all. */
    val reportedAt: String? = null,
    /**
     * Set when the lookup failed. Distinct from an empty list on purpose: "nothing within 5 km" and
     * "we could not ask" are different sentences, and the section must never say the first when it
     * means the second.
     */
    val failed: Boolean = false,
    /**
     * Set when the active car has no fuel type, so there was no question to ask. Its own state on
     * purpose: rendering it as an empty list fabricated a "no stations within 5 km" that no source
     * ever said — measured on device with a pre-fuelType car.
     * [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
     */
    val missingFuelType: Boolean = false,
) {
    /** True when at least one station could be priced — decides which sentence the section says. */
    val hasPrices: Boolean get() = stations.any { it.price != null }
}
