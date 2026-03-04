package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.repository.LocationRepository

class GetStoredLocationsUseCase(private val locationRepository: LocationRepository) {

    suspend operator fun invoke(): Result<List<GpsPoint>> {
        return locationRepository.getStoredLocations()
    }
}
