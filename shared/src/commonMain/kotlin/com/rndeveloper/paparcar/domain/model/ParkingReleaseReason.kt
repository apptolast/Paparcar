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
 */
enum class ParkingReleaseReason(
    /** Report the freed plaza to the community. Only a real departure frees a plaza. */
    val publishesSpot: Boolean,
    /** The user left in that car (vs. deleted a record that should never have existed). */
    val isDeparture: Boolean,
) {
    /** "Release spot" in the departure dialog — I'm leaving and I share the plaza. */
    DEPARTURE_PUBLISHED(publishesSpot = true, isDeparture = true),

    /** "Just delete" in the departure dialog — I'm leaving, but I don't want to share the plaza. */
    DEPARTURE_UNPUBLISHED(publishesSpot = false, isDeparture = true),

    /** "Delete record" in the edit sheet — the parking was wrong or stale; it says nothing about
     *  which car I drive, so it must never touch the active vehicle. */
    RECORD_DELETED(publishesSpot = false, isDeparture = false),
}
