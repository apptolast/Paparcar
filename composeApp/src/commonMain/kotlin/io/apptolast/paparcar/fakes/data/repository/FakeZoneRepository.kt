package io.apptolast.paparcar.fakes.data.repository

import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.repository.ZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeZoneRepository : ZoneRepository {
    override fun observeZones(): Flow<List<Zone>> =
        MutableStateFlow(emptyList<Zone>()).asStateFlow()

    override suspend fun syncFromRemote(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun saveZone(zone: Zone) {}

    override suspend fun deleteZone(id: String) {}

    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)
}
