package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation

/**
 * The "where to refuel" lane, as the sheet needs it. [FUEL-STATIONS-LIST-001]
 *
 * **Nothing is fetched until the lane is entered.** Most sessions never ask this question, and a
 * province of stations downloaded for a list nobody opened is the user's data spent on our
 * convenience. The trigger is therefore entering the lane — `HomeNearbyLens.FUEL` — and this value
 * only ever holds the ANSWER. ⛔ It used to hold an `isExpanded` of its own; two places saying
 * whether fuel is on show is how they come to disagree.
 * [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
 */
data class HomeFuelSlice(
    val isLoading: Boolean = false,
    val stations: List<NearbyFuelStation> = emptyList(),
    /** The feed's own hour, verbatim. Null where the source publishes no prices at all. */
    val reportedAt: String? = null,
    /**
     * Set when this list came off the disk because the live answer failed — the moment the app last
     * managed to ask. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     *
     * A third sentence, beside `failed` and an empty list: "we could not ask, and this is what was
     * here when we last could".
     */
    val rememberedAtMs: Long? = null,
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
    /**
     * Centre this answer is ABOUT — the camera when it has spoken, the fix otherwise. The section
     * follows the camera like the layer does, so it needs its own anchor to know whether a move
     * earned a fresh ask. [A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001]
     */
    val anchorLat: Double? = null,
    val anchorLon: Double? = null,
) {
    /** True when at least one station could be priced — decides which sentence the section says. */
    val hasPrices: Boolean get() = stations.any { it.price != null }

    /**
     * The hour of the OLDEST price on show, for sources that date each price instead of the
     * response. [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
     *
     * ⛔ Read as a BOUND, never as "the list is this old" — that is the difference between a true
     * sentence and the lie this ticket exists to avoid. Nothing here is older than this, which is
     * all a single line can honestly promise about a list whose prices were measured spanning
     * 0,7 h to thirteen months.
     */
    val oldestPriceAtMs: Long? get() = stations.mapNotNull { it.price?.reportedAtMs }.minOrNull()
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
    /**
     * When these markers came off the disk rather than the wire.
     * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     *
     * Carried for the same reason as its car-park twin, and kept symmetrical with it on purpose:
     * one lane knowing and the other not would make the open question — where the MAP says it —
     * answerable for half the map. ⏳ That placement is still undecided; it belongs in the FAB band
     * with the GPS capsule [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001], not in a new surface.
     */
    val rememberedAtMs: Long? = null,
)
