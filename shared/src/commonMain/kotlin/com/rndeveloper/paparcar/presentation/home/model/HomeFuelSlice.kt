package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.model.FuelType
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
    /**
     * Which fuel [stations] were priced FOR (null: fetched with no fuel — the map layer's fuel-less
     * ask). The section refetches when the car's fuel no longer matches, instead of showing a list
     * priced for a different question. [FUEL-STATIONS-MAP-AND-PEEK-001]
     */
    val fetchedFor: FuelType? = null,
) {
    /** True when at least one station could be priced — decides which sentence the section says. */
    val hasPrices: Boolean get() = stations.any { it.price != null }
}

/**
 * The map layer's OWN store: what the camera is looking at, not where the user stands.
 * [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
 *
 * Separate from [HomeFuelSlice] on purpose — they answer different questions ("what is HERE where
 * I look" vs "where do I refuel") and folding them into one store is exactly what made the layer
 * paint Madrid's capsules over Paris. No `failed` here: the layer has no sentence to say — a
 * failed viewport keeps the previous stations, logs, and the anchor still advances so a broken
 * area is not hammered on every settle.
 */
data class HomeFuelLayerSlice(
    /** Centre of the last ask (attempted, not just succeeded), or null before the first one. */
    val anchorLat: Double? = null,
    val anchorLon: Double? = null,
    val stations: List<NearbyFuelStation> = emptyList(),
    val isLoading: Boolean = false,
)
