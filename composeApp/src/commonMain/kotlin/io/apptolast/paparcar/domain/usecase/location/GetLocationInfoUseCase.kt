package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.repository.LocationInfoRepository
import kotlinx.coroutines.flow.Flow

class GetLocationInfoUseCase(private val repository: LocationInfoRepository) {
    operator fun invoke(lat: Double, lon: Double): Flow<LocationInfo> =
        repository.getLocationInfo(lat, lon)
}
