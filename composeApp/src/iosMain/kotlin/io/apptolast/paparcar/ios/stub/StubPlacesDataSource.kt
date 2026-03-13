package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.places.PlacesDataSource
import io.apptolast.paparcar.domain.model.PlaceInfo

class StubPlacesDataSource : PlacesDataSource {
    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> =
        Result.success(null)
}
