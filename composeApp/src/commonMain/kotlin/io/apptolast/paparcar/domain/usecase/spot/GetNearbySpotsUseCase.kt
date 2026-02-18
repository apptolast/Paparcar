package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.domain.repository.SpotRepository

class GetNearbySpotsUseCase(private val spotRepository: SpotRepository) {

    suspend operator fun invoke(location: SpotLocation, radiusMeters: Double): Result<List<Spot>> {
        return spotRepository.getNearbySpots(location, radiusMeters)
    }
}
