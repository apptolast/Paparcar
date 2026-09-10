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
)
