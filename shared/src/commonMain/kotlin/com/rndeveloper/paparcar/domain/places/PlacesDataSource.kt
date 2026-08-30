package com.rndeveloper.paparcar.domain.places

import com.rndeveloper.paparcar.domain.model.PlaceInfo

interface PlacesDataSource {
    /**
     * The place at [lat]/[lon] worth naming, or null when none is close enough to claim.
     *
     * "Close enough" is [NearbyPlacePolicy]'s to define, not each implementation's — the three
     * numbers that used to describe this radius (this KDoc said 50 m, the Android impl said 150 m,
     * the constant was 80) are now one. [POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001]
     */
    suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?>
}
