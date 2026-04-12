package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.places.PlacesDataSource

class FakePlacesDataSource : PlacesDataSource {

    var placeResult: Result<PlaceInfo?> = Result.success(null)

    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> = placeResult
}
