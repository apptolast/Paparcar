package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.places.PlacesDataSource

class FakePlacesDataSource : PlacesDataSource {

    var placeResult: Result<PlaceInfo?> = Result.success(null)

    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> = placeResult
}
