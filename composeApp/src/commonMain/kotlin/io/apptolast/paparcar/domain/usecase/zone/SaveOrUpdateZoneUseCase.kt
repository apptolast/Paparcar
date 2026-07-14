package io.apptolast.paparcar.domain.usecase.zone

import io.apptolast.paparcar.domain.repository.ZoneRepository
import kotlinx.coroutines.flow.first

/**
 * Persists Home's zone form — create when [invoke]'s `editingZoneId` is null,
 * in-place update otherwise. Absorbs the edit-mode zone lookup that used to
 * live inline in HomeViewModel.confirmAddZone. [HOME-ATOMIZE-001 F4]
 *
 * Creation delegates to [SaveZoneUseCase] (id/createdAt/userId generation);
 * an edit copies the loaded zone so createdAt and ownership are preserved.
 */
class SaveOrUpdateZoneUseCase(
    private val repository: ZoneRepository,
    private val saveZone: SaveZoneUseCase,
) {
    suspend operator fun invoke(
        editingZoneId: String?,
        name: String,
        lat: Double,
        lon: Double,
        iconKey: String,
        radiusMeters: Float,
        isPrivate: Boolean,
    ): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("zone name is blank"))
        }
        return if (editingZoneId != null) {
            val existing = repository.observeZones().first().find { it.id == editingZoneId }
                ?: return Result.failure(IllegalStateException("zone $editingZoneId vanished while editing"))
            repository.saveZone(
                existing.copy(
                    name = trimmed,
                    lat = lat,
                    lon = lon,
                    iconKey = iconKey,
                    radiusMeters = radiusMeters,
                    isPrivate = isPrivate,
                ),
            )
        } else {
            saveZone(
                name = trimmed,
                lat = lat,
                lon = lon,
                iconKey = iconKey,
                radiusMeters = radiusMeters,
                isPrivate = isPrivate,
            ).map { }
        }
    }
}
