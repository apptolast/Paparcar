package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking

/**
 * The car parks around where the camera looks — the ONE store the lane, the map, the peek and the
 * selection all read. [PARKINGS-ON-THE-MAP-001] [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
 *
 * **Nothing is fetched until the lane is entered** (`HomeNearbyLens.PARKINGS`), because most sessions
 * never ask this question.
 *
 * ⛔ One store, like the free spots. There used to be a second, camera-anchored "layer" store and the
 * surfaces painted the union of both: each kept its own anchor, so the union mixed AREAS — pins that
 * had panned off-screen went on being listed, measured from a centre nobody was looking at.
 */
data class HomeParkingsSlice(
    val isLoading: Boolean = false,
    /** As the source answered them. What the surfaces show is `HomeState.publicParkingsShown`. */
    val parkings: List<NearbyPublicParking> = emptyList(),

    /** El filtro activo, o null. [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001] */
    val filter: PublicParkingFilter? = null,
    /** Había parkings antes de filtrar: separa «no hay» de «ninguno cumple lo que pediste». */
    val hasAnyBeforeFilter: Boolean = false,
    /**
     * Set when the lookup failed. Distinct from an empty list on purpose: "none within 1,5 km" and
     * "we could not ask" are different sentences, and the lane must never say the first when it
     * means the second. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
     */
    val failed: Boolean = false,
    /**
     * Centre this answer is ABOUT — the settled camera when it has spoken, the fix otherwise. Set on
     * the attempt (so the same ground is not asked twice while it flies) and cleared on failure (so
     * any move retries). [A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001]
     */
    val anchorLat: Double? = null,
    val anchorLon: Double? = null,
    /**
     * Set when this list came off the DISK because the live answer failed — the moment the app last
     * actually managed to ask. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     *
     * ⛔ A third sentence, not a shade of the other two. `failed` means "we could not ask and have
     * nothing"; this means "we could not ask, and here is what was there when we last could".
     */
    val rememberedAtMs: Long? = null,
    /**
     * The camera is looking further from the user than the lane follows
     * ([ViewportRefetch.MAX_FROM_USER_METERS]): nothing was asked and nothing is shown, and the lane
     * says so — never "none within 1,5 km", which would be a verdict about a place nobody checked.
     * [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
     */
    val tooFar: Boolean = false,
)
