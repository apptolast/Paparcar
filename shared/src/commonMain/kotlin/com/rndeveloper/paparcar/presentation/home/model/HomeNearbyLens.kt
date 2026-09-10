package com.rndeveloper.paparcar.presentation.home.model

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
    PARKINGS,
}
