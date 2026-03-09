package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.places.PlacesPort
import io.apptolast.paparcar.domain.model.PlaceInfo

class StubPlacesPort : PlacesPort {
    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> =
        Result.success(null)
}
