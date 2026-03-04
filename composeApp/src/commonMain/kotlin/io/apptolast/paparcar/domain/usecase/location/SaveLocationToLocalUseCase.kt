package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.repository.LocationRepository

class SaveLocationToLocalUseCase(private val locationRepository: LocationRepository) {

    suspend operator fun invoke(location: GpsPoint): Result<Unit> {
        return locationRepository.saveLocation(location)
    }
}
