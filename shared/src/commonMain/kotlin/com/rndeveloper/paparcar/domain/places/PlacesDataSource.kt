package com.rndeveloper.paparcar.domain.places

import com.rndeveloper.paparcar.domain.model.PlaceInfo

interface PlacesDataSource {
    /** Returns the most relevant POI within ~50 m of [lat]/[lon], or null if none found. */
    suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?>
}
