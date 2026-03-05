package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.places.PlacesPort

class GetNearbyPlaceUseCase(private val placesPort: PlacesPort) {
    suspend operator fun invoke(lat: Double, lon: Double): Result<PlaceInfo?> =
        placesPort.getNearbyPlace(lat, lon)
}
