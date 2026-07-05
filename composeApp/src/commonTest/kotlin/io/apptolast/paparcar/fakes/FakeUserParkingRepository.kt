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

    var saveNewParkingSessionCallCount = 0
        private set
    /** Failure override for tests. On success, the fake returns the previously-active session id. */
    var saveNewParkingSessionResult: Result<String?>? = null

    override suspend fun saveNewParkingSession(session: UserParking): Result<String?> {
        saveNewParkingSessionCallCount++
        saveNewParkingSessionResult?.let { override ->
            if (override.isFailure) return override
        }
        val vehicleId = session.vehicleId
        val previousActiveId = vehicleId
            ?.let { sessions.firstOrNull { s -> s.isActive && s.vehicleId == it } }
            ?.id
        if (vehicleId != null) {
            sessions.replaceAll { s ->
                if (s.isActive && s.vehicleId == vehicleId) s.copy(isActive = false) else s
            }
        }
        sessions.add(session)
        _sessionsFlow.value = sessions.toList()
        return Result.success(previousActiveId)
    }

    override suspend fun getActiveSessionByGeofence(geofenceId: String): UserParking? =
        sessions.firstOrNull { it.isActive && it.geofenceId == geofenceId }

    override suspend fun getActiveSessionByVehicle(vehicleId: String): UserParking? =
        sessions.firstOrNull { it.isActive && it.vehicleId == vehicleId }

    /**
     * Test-only convenience — returns the first active session regardless of geofence.
     * Mirrors the legacy single-active assumption used by pre-multi-parking tests.
     */
    fun getActiveSession(): UserParking? =
        sessions.firstOrNull { it.isActive }

    override fun observeActiveSessions(): Flow<List<UserParking>> =
        _sessionsFlow.map { list -> list.filter { it.isActive } }

    override fun observeAllSessions(): Flow<List<UserParking>> = _sessionsFlow

    override fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>> =
        _sessionsFlow.map { list -> list.filter { it.vehicleId == vehicleId } }

    override suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking> =
        sessions.drop(offset).take(limit)

    override suspend fun getSessionsByVehiclePaged(vehicleId: String, limit: Int, offset: Int): List<UserParking> =
        sessions.filter { it.vehicleId == vehicleId }.drop(offset).take(limit)

    /** Failure override for tests — when set to a failure, [clearActiveParkingSession] returns it
     *  without mutating state. */
    var clearActiveParkingSessionResult: Result<Unit>? = null

    override suspend fun clearActiveParkingSession(sessionId: String): Result<Unit> {
        clearActiveParkingSessionResult?.let { override ->
            if (override.isFailure) return override
        }
        sessions.replaceAll { if (it.id == sessionId) it.copy(isActive = false) else it }
        _sessionsFlow.value = sessions.toList()
        return Result.success(Unit)
    }

    override suspend fun syncFromRemote(userId: String): Result<Unit> {
        syncCallCount++
        return syncResult
    }

    var pushPendingCallCount = 0
        private set

    override suspend fun pushPendingParkingSessions(): Result<Unit> {
        pushPendingCallCount++
        return Result.success(Unit)
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

    override suspend fun updateParkingSessionAddressAndPlace(
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

    var updateParkingSessionPositionCallCount = 0
        private set
    var updateParkingSessionPositionResult: Result<UserParking>? = null

    override suspend fun updateParkingSessionPosition(id: String, location: GpsPoint): Result<UserParking> {
        updateParkingSessionPositionCallCount++
        updateParkingSessionPositionResult?.let { return it }
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
