package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.repository.SpotRepository
import kotlinx.coroutines.flow.Flow

class ObserveNearbySpotsUseCase(private val spotRepository: SpotRepository) {

    operator fun invoke(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>> {
        return spotRepository.observeNearbySpots(location, radiusMeters)
    }

    companion object {
        const val DEFAULT_SEARCH_RADIUS_METERS = 1000.0
    }
}
