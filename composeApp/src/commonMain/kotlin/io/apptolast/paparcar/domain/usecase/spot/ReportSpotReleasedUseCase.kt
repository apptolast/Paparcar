package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.repository.SpotRepository

class ReportSpotReleasedUseCase(private val spotRepository: SpotRepository) {

    suspend operator fun invoke(spot: Spot): Result<Unit> {
        return spotRepository.reportSpotReleased(spot)
    }
}
