package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUserParkingRepository(initialSession: UserParking? = null) : UserParkingRepository {

    private val sessions = mutableListOf<UserParking>().also { list ->
        initialSession?.let { list.add(it) }
    }
    private val _sessionsFlow = MutableStateFlow<List<UserParking>>(sessions.toList())

    var syncCallCount = 0
        private set
    var syncResult: Result<Unit> = Result.success(Unit)

    var saveSessionCallCount = 0
        private set
    var saveSessionResult: Result<Unit> = Result.success(Unit)

    override suspend fun saveSession(session: UserParking): Result<Unit> {
        saveSessionCallCount++
        if (saveSessionResult.isFailure) return saveSessionResult
        sessions.removeAll { it.isActive }
        sessions.add(session)
        _sessionsFlow.value = sessions.toList()
        return saveSessionResult
    }

    override suspend fun getActiveSession(): UserParking? =
        sessions.firstOrNull { it.isActive }

    override fun observeActiveSession(): Flow<UserParking?> =
        _sessionsFlow.map { it.firstOrNull { s -> s.isActive } }

    override fun observeAllSessions(): Flow<List<UserParking>> = _sessionsFlow

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
}
