package com.rndeveloper.paparcar.domain.model

/**
 * WHY an active parking session is being closed by the user. [PARK-DELETE-NO-DECLARE-001]
 *
 * Replaces the old `publishSpot: Boolean`, which described the *consequence* and left the two very
 * different meanings of "close this session" indistinguishable: leaving in the car (a departure)
 * versus deleting a record that was wrong (a statement that it never happened). Inferring identity
 * from the latter stole the active flag — and with it the geofences — from the car the user
 * actually drives.
 *
 * [isDeparture] is a FACT about what happened, not a verdict: whether a departure also declares the
 * active vehicle depends on the car too, and that composition lives in
 * `VehicleFenceOwnershipPolicy.shouldDeclareActiveOnRelease` — the one home for "who owns identity".
 *
 * ## Why leaving is ONE answer now
 *
 * [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] There used to be two departures,
 * `DEPARTURE_PUBLISHED` and `DEPARTURE_UNPUBLISHED`, and the release dialog asked the user to pick.
 * That question was in the wrong place twice over:
 *
 * - It asked **per departure** — at the worst possible moment, with the car started — for something
 *   that is a property of the PLACE and therefore durable. "Don't share this one" said about a
 *   garage has to be re-said every single time you leave it.
 * - The app already has the durable answer, and it is the private zone. Which is exactly the
 *   invariant that was missing from the manual release: the button published the plaza of a private
 *   garage because the dialog, not the place, was deciding.
 *
 * So the place decides and leaving is one button. The opt-out did not disappear — it moved to where
 * it is answered once and remembered.
 */
enum class ParkingReleaseReason(
    /**
     * The user is handing the freed plaza to the community.
     *
     * ⚠️ An INTENTION, not an assertion — it was called `publishesSpot` and read as the outcome.
     * `SpotReleasePolicy` still gets to withhold the spot (private zone, doubt circle wider than
     * the publish ceiling), so the only honest account of whether a plaza went out is the boolean
     * `ReleaseActiveParkingSessionUseCase` returns. Same distinction the detection master switch
     * had to learn [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001].
     */
    val offersSpot: Boolean,
    /** The user left in that car (vs. deleted a record that should never have existed). */
    val isDeparture: Boolean,
) {
    /** "Me voy" in the release dialog — I'm leaving; the plaza goes out if this place allows it. */
    DEPARTURE(offersSpot = true, isDeparture = true),

    /** "Delete record" in the edit sheet — the parking was wrong or stale; it says nothing about
     *  which car I drive, so it must never touch the active vehicle. */
    RECORD_DELETED(offersSpot = false, isDeparture = false),
}
