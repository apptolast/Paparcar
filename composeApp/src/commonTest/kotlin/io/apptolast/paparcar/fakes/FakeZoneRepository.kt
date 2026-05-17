package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.repository.ZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeZoneRepository : ZoneRepository {

    private val _zones = MutableStateFlow<List<Zone>>(emptyList())

    var zones: List<Zone>
        get() = _zones.value
        set(value) { _zones.value = value }

    var savedZone: Zone? = null
        private set
    var deletedZoneId: String? = null
        private set
    var deleteAllUserId: String? = null
        private set

    var syncFromRemoteCallCount = 0
        private set
    var syncFromRemoteResult: Result<Unit> = Result.success(Unit)

    override fun observeZones(): Flow<List<Zone>> = _zones

    override suspend fun syncFromRemote(userId: String): Result<Unit> {
        syncFromRemoteCallCount++
        return syncFromRemoteResult
    }

    override suspend fun saveZone(zone: Zone) {
        savedZone = zone
        _zones.value = _zones.value + zone
    }

    override suspend fun deleteZone(id: String) {
        deletedZoneId = id
        _zones.value = _zones.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> {
        deleteAllUserId = userId
        _zones.value = emptyList()
        return Result.success(Unit)
    }
}
