package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import kotlinx.coroutines.flow.Flow

interface AddressAndPlaceRepository {

    /**
     * Address first, then the POI when it arrives.
     *
     * [persisting] declares that the caller is going to STORE this answer — a parking session's
     * name, not a title on a map that the next pan replaces. It buys one thing: a cached
     * *"there is no place here"* does not settle the question for it, and the POI is measured again
     * at these exact coordinates. [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
     *
     * ⛔ A cell is ~11 × 8 m and its answer was measured from ONE point inside it. For a place FOUND
     * that is harmless — a name within 40 m of that point is within ~54 m of any other, and the
     * error is a metre of copy. For a NEGATIVE it is the whole verdict: 14 m against a 40 m naming
     * radius is exactly what flips it. And the two consumers race — `HomeGeocodingController`
     * geocodes the user's own position, which while walking away from a just-parked car is metres
     * from the pin and seconds ahead of the enrichment worker. Whoever got there first used to
     * decide, including the one that was going to throw its answer away on the next pan.
     */
    fun getAddressAndPlace(
        lat: Double,
        lon: Double,
        persisting: Boolean = false,
    ): Flow<AddressAndPlace>
}
