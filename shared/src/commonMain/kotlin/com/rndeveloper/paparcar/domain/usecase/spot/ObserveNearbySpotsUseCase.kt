package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import kotlinx.coroutines.flow.Flow

class ObserveNearbySpotsUseCase(private val spotRepository: SpotRepository) {

    operator fun invoke(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>> {
        return spotRepository.observeNearbySpots(location, radiusMeters)
    }

    companion object {
        const val DEFAULT_SEARCH_RADIUS_METERS = 2000.0
    }
}
