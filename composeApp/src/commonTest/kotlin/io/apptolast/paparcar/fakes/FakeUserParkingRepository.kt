package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUserParkingRepository(
    initialSession: UserParking? = null,
    initialSessions: List<UserParking> = emptyList(),
) : UserParkingRepository {

    private val sessions = mutableListOf<UserParking>().also { list ->
        initialSession?.let { list.add(it) }
        list.addAll(initialSessions)
    }
    private val _sessionsFlow = MutableStateFlow<List<UserParking>>(sessions.toList())

    var syncCallCount = 0
        private set
    var syncResult: Result<Unit> = Result.success(Unit)

    var saveSessionCallCount = 0
        private set
    /** Failure override for tests. On success, the fake returns the previously-active session id. */
    var saveSessionResult: Result<String?>? = null

    override suspend fun saveSession(session: UserParking): Result<String?> {
        saveSessionCallCount++
        saveSessionResult?.let { override ->
            if (override.isFailure) return override
        }
        val previousActiveId = sessions.firstOrNull { it.isActive }?.id
        sessions.removeAll { it.isActive }
        sessions.add(session)
        _sessionsFlow.value = sessions.toList()
        return Result.success(previousActiveId)
    }

    override suspend fun getActiveSession(): UserParking? =
        sessions.firstOrNull { it.isActive }

    override fun observeActiveSession(): Flow<UserParking?> =
        _sessionsFlow.map { it.firstOrNull { s -> s.isActive } }

    override fun observeAllSessions(): Flow<List<UserParking>> = _sessionsFlow

    override fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>> =
        _sessionsFlow.map { list -> list.filter { it.vehicleId == vehicleId } }

    override suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking> =
        sessions.drop(offset).take(limit)

    override suspend fun clearActive(): Result<Unit> {
        sessions.replaceAll { it.copy(isActive = false) }
        _sessionsFlow.value = sessions.toList()
        return Result.success(Unit)
    }

    override suspend fun syncParkingHistoryFromRemote(userId: String): Result<Unit> {
        syncCallCount++
        return syncResult
    }

    var deleteAllDataCallCount = 0
        private set
    var deleteAllDataResult: Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(userId: String): Result<Unit> {
        deleteAllDataCallCount++
        if (deleteAllDataResult.isSuccess) {
            sessions.clear()
            _sessionsFlow.value = emptyList()
        }
        return deleteAllDataResult
    }

    override suspend fun updateLocationInfo(
        id: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ): Result<Unit> {
        val idx = sessions.indexOfFirst { it.id == id }
        if (idx >= 0) {
            sessions[idx] = sessions[idx].copy(address = address, placeInfo = placeInfo)
            _sessionsFlow.value = sessions.toList()
        }
        return Result.success(Unit)
    }

    var updateLocationCallCount = 0
        private set
    var updateLocationResult: Result<UserParking>? = null

    override suspend fun updateLocation(id: String, location: GpsPoint): Result<UserParking> {
        updateLocationCallCount++
        updateLocationResult?.let { return it }
        val idx = sessions.indexOfFirst { it.id == id }
        if (idx < 0) return Result.failure(IllegalStateException("No session $id"))
        val updated = sessions[idx].copy(
            location = location,
            address = null,
            placeInfo = null,
        )
        sessions[idx] = updated
        _sessionsFlow.value = sessions.toList()
        return Result.success(updated)
    }
}
