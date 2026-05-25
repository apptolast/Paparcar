package io.apptolast.paparcar.domain.usecase.zone

import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.repository.ZoneRepository

/**
 * Updates an existing [Zone]'s mutable fields (name, iconKey, position)
 * while preserving its identity (id, userId, createdAt).
 * ZoneRepository.saveZone is already an upsert — this use case exists for
 * naming clarity at the call site.
 */
class UpdateZoneUseCase(private val repository: ZoneRepository) {
    suspend operator fun invoke(
        existing: Zone,
        name: String,
        lat: Double,
        lon: Double,
        iconKey: String,
    ) {
        repository.saveZone(
            existing.copy(name = name.trim(), lat = lat, lon = lon, iconKey = iconKey),
        )
    }
}
