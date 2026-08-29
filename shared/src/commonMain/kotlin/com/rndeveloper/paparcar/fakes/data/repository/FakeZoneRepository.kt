package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeZoneRepository : ZoneRepository {
    override fun observeZones(): Flow<List<Zone>> =
        MutableStateFlow(emptyList<Zone>()).asStateFlow()

    override suspend fun syncFromRemote(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun saveZone(zone: Zone): Result<Unit> = Result.success(Unit)

    override suspend fun deleteZone(id: String): Result<Unit> = Result.success(Unit)

    override suspend fun getPrivateZonesSnapshot(): List<Zone> = emptyList()

    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun pushPendingZones(): Result<Unit> = Result.success(Unit)
}
