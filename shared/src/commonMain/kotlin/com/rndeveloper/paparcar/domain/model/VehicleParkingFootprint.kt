package com.rndeveloper.paparcar.domain.model

/**
 * What a vehicle's parkings amount to when the user is about to delete it.
 *
 * Deleting a car takes its whole parking history with it, so the screen has to be able to say how
 * much that is BEFORE the user confirms — and to refuse outright while the car is still parked.
 * Both answers come from the same read because they are the same question asked twice.
 * [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001]
 */
data class VehicleParkingFootprint(
    /** Ended, non-retracted parkings — exactly what the history lists, and what the warning
     *  quotes. Zero is a real answer: the car simply has no history to lose. */
    val endedParkings: Int,
    /** True while the car holds a parking in progress. Deleting is blocked, not silently
     *  destructive: closing the parking is the user's call, and it may publish a spot. */
    val hasActiveParking: Boolean,
)
