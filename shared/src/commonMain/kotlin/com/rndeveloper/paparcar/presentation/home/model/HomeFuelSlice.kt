package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation

/**
 * The fuel stations around where the camera looks — the ONE store the lane, the map, the peek and
 * the selection all read. [FUEL-STATIONS-LIST-001] [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
 *
 * **Nothing is fetched until the lane is entered.** Most sessions never ask this question, and a
 * province of stations downloaded for a list nobody opened is the user's data spent on our
 * convenience. The trigger is therefore entering the lane — `HomeNearbyLens.FUEL` — and this value
 * only ever holds the ANSWER. ⛔ It used to hold an `isExpanded` of its own; two places saying
 * whether fuel is on show is how they come to disagree.
 * [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
 *
 * ⛔ One store, like the free spots. A second, camera-anchored "layer" store used to sit beside this
 * one and the surfaces painted their union: two anchors, so the union mixed areas and the list went
 * on naming stations the map had panned away from. It existed to keep a fuel-less answer from posing
 * as a priced one — which [fetchedFor] already guarantees inside a single store.
 */
data class HomeFuelSlice(
    val isLoading: Boolean = false,
    /** As the source answered them. What the surfaces show is `HomeState.fuelStationsShown`. */
    val stations: List<NearbyFuelStation> = emptyList(),
    /** The feed's own hour, verbatim. Null where the source publishes no prices at all. */
    val reportedAt: String? = null,
    /**
     * True when a national feed exists for this country and failed, so what is on show came from the
     * fallback. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
     *
     * ⛔ It is what stops the lane saying «this country publishes no prices» — a verdict about a
     * whole country — when what happened is that we could not fetch them.
     */
    val didFallBack: Boolean = false,
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
     * Set when the active car has no fuel type, so the LIST has no priced question to ask. Its own
     * state on purpose: rendering it as an empty list fabricated a "no stations within 5 km" that no
     * source ever said — measured on device with a pre-fuelType car.
     * [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
     *
     * The stations are still asked for, fuel-less, because the MAP has a question: capsules without a
     * figure are honest, an empty map is not. [FUEL-STATIONS-MAP-AND-PEEK-001]
     */
    val missingFuelType: Boolean = false,
    /**
     * Which fuel [stations] were asked FOR (null: asked with no fuel). A lane whose car now burns
     * something else asks again instead of showing a list filtered for a different question.
     * [FUEL-STATIONS-MAP-AND-PEEK-001]
     */
    val fetchedFor: FuelType? = null,
    /**
     * Centre this answer is ABOUT — the settled camera when it has spoken, the fix otherwise. Set on
     * the attempt and cleared on failure, so any move retries.
     * [A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001]
     */
    val anchorLat: Double? = null,
    val anchorLon: Double? = null,
    /**
     * The camera is looking further from the user than the lane follows: nothing asked, nothing
     * shown, and the lane says so. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
     */
    val tooFar: Boolean = false,
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
