package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow

class ObserveLocationUpdatesUseCase(private val locationRepository: LocationRepository) {

    operator fun invoke(): Flow<GpsPoint> {
        return locationRepository.observeBalancedLocationFlow()
    }
}
