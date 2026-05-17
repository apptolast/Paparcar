package io.apptolast.paparcar.domain.usecase.zone

import io.apptolast.paparcar.domain.repository.ZoneRepository

class DeleteZoneUseCase(private val repository: ZoneRepository) {
    suspend operator fun invoke(id: String) {
        repository.deleteZone(id)
    }
}
