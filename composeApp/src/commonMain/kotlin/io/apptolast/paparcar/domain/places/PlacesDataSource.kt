package io.apptolast.paparcar.domain.places

import io.apptolast.paparcar.domain.model.PlaceInfo

interface PlacesDataSource {
    /** Returns the most relevant POI within ~50 m of [lat]/[lon], or null if none found. */
    suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?>
}
