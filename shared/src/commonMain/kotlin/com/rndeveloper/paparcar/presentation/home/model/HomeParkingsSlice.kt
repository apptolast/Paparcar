package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking

/**
 * The "car parks nearby" section, as the sheet needs it. [PARKINGS-ON-THE-MAP-001]
 *
 * Same contract as [HomeFuelSlice], and deliberately so: **nothing is fetched until the section is
 * opened**, because most sessions never ask this question.
 */
data class HomeParkingsSlice(
    val isExpanded: Boolean = false,
    val isLoading: Boolean = false,
    val parkings: List<NearbyPublicParking> = emptyList(),
    /**
     * Set when the lookup failed. Distinct from an empty list on purpose: "none within 1,5 km" and
     * "we could not ask" are different sentences, and the section must never say the first when it
     * means the second. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
     */
    val failed: Boolean = false,
    /**
     * Centre this answer is ABOUT — the camera when it has spoken, the fix otherwise. The section
     * follows the camera like the layer does, so it needs its own anchor to know whether a move
     * earned a fresh ask. [A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001]
     */
    val anchorLat: Double? = null,
    val anchorLon: Double? = null,
    /**
     * Set when this list came off the DISK because the live answer failed — the moment the app last
     * actually managed to ask. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     *
     * ⛔ A third sentence, not a shade of the other two. `failed` means "we could not ask and have
     * nothing"; this means "we could not ask, and here is what was there when we last could". The
     * section must be able to tell them apart, because a driver acts differently on each.
     */
    val rememberedAtMs: Long? = null,
)

/**
 * The map layer's OWN store: what the camera is looking at, not where the user stands.
 * [PARKINGS-ON-THE-MAP-001] [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
 *
 * Twin of [HomeFuelLayerSlice], and the separation is the same lesson: the section answers "where
 * do I leave the car near ME" and the layer answers "what is HERE, where I am looking". Folding
 * them into one store is what painted Madrid's capsules over Paris in the fuel lane, and there is
 * no reason to relearn it in this one.
 *
 * No `failed` here: the layer has no sentence of its own — a failed viewport keeps whatever was
 * showing and logs; the SECTION owns the error copy.
 */
data class HomeParkingsLayerSlice(
    /** Centre of the last ask (attempted, not just succeeded), or null before the first one. */
    val anchorLat: Double? = null,
    val anchorLon: Double? = null,
    val parkings: List<NearbyPublicParking> = emptyList(),
    val isLoading: Boolean = false,
    /**
     * When these pins came off the disk rather than the wire.
     * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     *
     * Carried here even though the layer has no sentence of its own, because the alternative is a
     * map drawing remembered pins with no way for anything to know. ⏳ WHERE the map says it is an
     * open design decision — it belongs in the FAB band with the GPS capsule
     * [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001], not in a new floating surface invented here.
     */
    val rememberedAtMs: Long? = null,
)
