@file:OptIn(ExperimentalTime::class)

package io.apptolast.paparcar.fakes.data.repository

import io.apptolast.paparcar.domain.model.*
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FakeUserParkingRepository : UserParkingRepository {
    private val now = Clock.System.now().toEpochMilliseconds()
    
    private val mockSessions: List<UserParking> = buildList {
        // 1. Sesión activa actual (Toyota Corolla)
        add(UserParking(
            id = "parking_active_001",
            userId = "mock_user_001",
            vehicleId = "mock_vehicle_002",
            location = GpsPoint(36.5900, -6.2300, 5f, now - 3600_000L, 0f), // Hace 1h
            isActive = true,
            spotType = SpotType.AUTO_DETECTED,
            address = AddressInfo("Calle Active", "Puerto de Santa María", "Cádiz", "España", "ES")
        ))

        // 2. Generar ~52 sesiones históricas (1 por semana durante el último año)
        for (i in 1..52) {
            val weekMs = i * 7 * 24 * 60 * 60 * 1000L
            val vehicleId = if (i % 3 == 0) "mock_vehicle_002" else "mock_vehicle_001"
            val spotType = if (i % 5 == 0) SpotType.MANUAL_REPORT else SpotType.AUTO_DETECTED
            
            add(UserParking(
                id = "parking_history_$i",
                userId = "mock_user_001",
                vehicleId = vehicleId,
                location = GpsPoint(36.5920 + (i * 0.0001), -6.2290 - (i * 0.0001), 10f, now - weekMs, 0f),
                isActive = false,
                spotType = spotType,
                address = AddressInfo("Calle Histórica $i", "Puerto de Santa María", "Cádiz", "España", "ES")
            ))
        }
    }

    private val _sessionsFlow = MutableStateFlow(mockSessions)

    override suspend fun saveSession(session: UserParking): Result<String?> = Result.success(null)

    override suspend fun getActiveSessionByGeofence(geofenceId: String): UserParking? =
        mockSessions.find { it.isActive && it.geofenceId == geofenceId }

    override fun observeActiveSessions(): Flow<List<UserParking>> =
        _sessionsFlow.map { list -> list.filter { it.isActive } }

    override fun observeAllSessions(): Flow<List<UserParking>> = _sessionsFlow.asStateFlow()

    override fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>> =
        _sessionsFlow.map { list -> list.filter { it.vehicleId == vehicleId } }

    override suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking> =
        mockSessions.drop(offset).take(limit)

    override suspend fun clearActiveById(sessionId: String): Result<Unit> = Result.success(Unit)

    override suspend fun syncParkingHistoryFromRemote(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateLocationInfo(
        id: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?
    ): Result<Unit> = Result.success(Unit)

    override suspend fun updateLocation(id: String, location: GpsPoint): Result<UserParking> {
        val session = mockSessions.find { it.id == id } ?: return Result.failure(Exception("Not found"))
        return Result.success(session.copy(location = location))
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)
}
