package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.repository.SpotRepository

class GetNearbySpotsUseCase(private val spotRepository: SpotRepository) {

    suspend operator fun invoke(location: GpsPoint, radiusMeters: Double,): Result<List<Spot>> =
        spotRepository.getNearbySpots(location, radiusMeters)

}
