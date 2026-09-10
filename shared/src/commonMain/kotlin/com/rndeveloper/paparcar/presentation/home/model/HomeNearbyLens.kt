package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement

/**
 * WHICH nearby question the sheet's lane is answering right now.
 * [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
 *
 * Home used to concatenate the three subjects — free spots, car parks, fuel — one under the next,
 * each foldable. That made the `y` of a section a function of how many rows the section above it
 * held, and the free-spots feed has no cap: measured off the code's own paddings, "where to refuel"
 * sat ~1.100 dp down with 10 spots nearby and ~2.100 dp with 25. The subject a user asks for cannot
 * live behind another subject's list.
 *
 * So the sheet renders ONE lane at a time and this is the choice. The lane's content always starts
 * at the same place, whatever the others hold.
 *
 * Three things follow from one value, which is the point of having only one:
 *  - the lane the sheet renders,
 *  - the ask that goes out (entering a lane is what unfolding it used to be — same lazy rules,
 *    nothing is fetched until the user asks the question),
 *  - the map layer, which a lane turns ON. ⛔ Never off: a layer is a persisted preference and
 *    visiting a lane must not rewrite one.
 *
 * ⛔ [SPOTS] is not a peer of the other two, and the order here is that decision: free spots are the
 * product, car parks and fuel are complements to it ([PARKINGS-ON-THE-MAP-001]). The hierarchy is
 * carried by the DEFAULT and by emphasis in the chip row — never by vertical distance again.
 *
 * ⛔ Not persisted. Home is the AHORA and every cold start opens on the spots the community is
 * offering, not on wherever the last session wandered.
 */
enum class HomeNearbyLens {
    /** Free spots the community is offering — the default, and the product. */
    SPOTS,

    /** Where to refuel, with the price the source published. [FUEL-STATIONS-LIST-001] */
    FUEL,

    /** Public car parks, with occupancy where a municipality publishes it. [PARKINGS-ON-THE-MAP-001] */
    PARKINGS;

    /**
     * Which third-party subject this lane answers for, or null when the answer is the community's
     * own. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
     *
     * The ONE translation between the lane the user is in and the thing whose provenance has to be
     * said once. Two consumers read it — the lane's own first-visit line and the checklist's
     * `EXPLORE_NEARBY` step — and a `when` written at either call site is how the two would end up
     * disagreeing about whether the user has been told.
     *
     * ⛔ [SPOTS] is null and that is not an omission: a free spot comes from the user standing next
     * to it, so there is no source to name and nothing to qualify.
     */
    val complement: NearbyComplement?
        get() = when (this) {
            SPOTS -> null
            FUEL -> NearbyComplement.FUEL
            PARKINGS -> NearbyComplement.CAR_PARKS
        }
}
