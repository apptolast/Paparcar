package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.domain.repository.LocationRepository

class SaveLocationToLocalUseCase(private val locationRepository: LocationRepository) {

    suspend operator fun invoke(location: SpotLocation): Result<Unit> {
        return locationRepository.saveLocation(location)
    }
}
