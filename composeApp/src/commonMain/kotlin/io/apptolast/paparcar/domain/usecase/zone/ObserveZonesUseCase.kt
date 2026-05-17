package io.apptolast.paparcar.domain.usecase.zone

import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.repository.ZoneRepository
import kotlinx.coroutines.flow.Flow

class ObserveZonesUseCase(private val repository: ZoneRepository) {
    operator fun invoke(): Flow<List<Zone>> = repository.observeZones()
}
