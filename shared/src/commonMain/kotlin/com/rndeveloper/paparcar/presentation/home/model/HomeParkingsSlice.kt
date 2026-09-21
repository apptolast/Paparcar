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
     * Set when this list came off the DISK — the moment the app last actually managed to ask.
     * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     *
     * ⛔ A third sentence, not a shade of the other two. `failed` means "we could not ask and have
     * nothing"; this means "here is what was there when we last could".
     *
     * ⚠️ **Read together with [isLoading], because the pair says two different things.**
     * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
     * - set **while loading** → these are last visit's car parks, painted so the lane is not a
     *   skeleton while the real ask flies. Nothing is claimed about the network, and the lane says
     *   nothing: the "no connection" note is suppressed for exactly this case.
     * - set **and not loading** → the ask is over and it failed. Now the note is true, and it shows.
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
