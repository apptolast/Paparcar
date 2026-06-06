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

/**
 * Fake repository for tests and debug DI.
 * Vehicles:
 *   mock_vehicle_001 — Seat León, no BT, isActive, most-used
 *   mock_vehicle_002 — Toyota Corolla, BT configured, active session
 *   mock_vehicle_003 — Honda CBR 600, motorcycle, rarely used
 *   mock_vehicle_004 — Ford Transit, van, BT configured, moderate use
 */
class FakeUserParkingRepository : UserParkingRepository {
    private val now = Clock.System.now().toEpochMilliseconds()

    private val mockSessions: List<UserParking> = buildList {
        // ── Active session ────────────────────────────────────────────────────
        add(UserParking(
            id = "parking_active_001",
            userId = "mock_user_001",
            vehicleId = "mock_vehicle_002",
            location = GpsPoint(36.5900, -6.2300, 5f, now - 3_600_000L, 0f),
            isActive = true,
            spotType = SpotType.AUTO_DETECTED,
            detectionReliability = 0.92f,
            address = AddressInfo("Calle Active", "Puerto de Santa María", "Cádiz", "España", "ES"),
        ))

        // ── Seat León (vehicle_001) — 65 sessions over 2 years ───────────────
        for (i in 1..65) {
            val daysAgo = i * 11L
            add(UserParking(
                id = "parking_leon_$i",
                userId = "mock_user_001",
                vehicleId = "mock_vehicle_001",
                location = GpsPoint(
                    36.5920 + (i % 7) * 0.0005,
                    -6.2290 - (i % 5) * 0.0008,
                    10f,
                    now - daysAgo * 86_400_000L,
                    0f,
                ),
                isActive = false,
                spotType = if (i % 6 == 0) SpotType.MANUAL_REPORT else SpotType.AUTO_DETECTED,
                detectionReliability = when {
                    i % 6 == 0 -> 1.0f
                    i % 3 == 0 -> 0.90f
                    else -> 0.77f
                },
                address = AddressInfo("Calle Histórica $i", "Puerto de Santa María", "Cádiz", "España", "ES"),
            ))
        }

        // ── Toyota Corolla BT (vehicle_002) — 45 sessions over 18 months ─────
        for (i in 1..45) {
            val daysAgo = i * 12L
            add(UserParking(
                id = "parking_corolla_$i",
                userId = "mock_user_001",
                vehicleId = "mock_vehicle_002",
                location = GpsPoint(
                    36.5880 + (i % 4) * 0.0006,
                    -6.2310 - (i % 6) * 0.0004,
                    8f,
                    now - daysAgo * 86_400_000L,
                    0f,
                ),
                isActive = false,
                spotType = if (i % 8 == 0) SpotType.MANUAL_REPORT else SpotType.AUTO_DETECTED,
                detectionReliability = if (i % 8 == 0) 1.0f else 0.95f,
                address = AddressInfo("Av. Corolla $i", "El Puerto", "Cádiz", "España", "ES"),
            ))
        }

        // ── Honda CBR 600 (vehicle_003) — 12 sessions over 1 year ────────────
        for (i in 1..12) {
            val daysAgo = i * 30L
            add(UserParking(
                id = "parking_moto_$i",
                userId = "mock_user_001",
                vehicleId = "mock_vehicle_003",
                location = GpsPoint(
                    36.5940 + i * 0.0003,
                    -6.2270 - i * 0.0005,
                    15f,
                    now - daysAgo * 86_400_000L,
                    0f,
                ),
                isActive = false,
                spotType = SpotType.MANUAL_REPORT,
                detectionReliability = 1.0f,
                address = AddressInfo("Paseo Moto $i", "El Puerto", "Cádiz", "España", "ES"),
            ))
        }

        // ── Ford Transit BT (vehicle_004) — 18 sessions over 6 months ────────
        for (i in 1..18) {
            val daysAgo = i * 10L
            add(UserParking(
                id = "parking_transit_$i",
                userId = "mock_user_001",
                vehicleId = "mock_vehicle_004",
                location = GpsPoint(
                    36.5860 + (i % 3) * 0.0007,
                    -6.2330 - (i % 4) * 0.0003,
                    12f,
                    now - daysAgo * 86_400_000L,
                    0f,
                ),
                isActive = false,
                spotType = if (i % 5 == 0) SpotType.MANUAL_REPORT else SpotType.AUTO_DETECTED,
                detectionReliability = if (i % 5 == 0) 1.0f else 0.93f,
                address = AddressInfo("Calle Furgoneta $i", "Puerto de Santa María", "Cádiz", "España", "ES"),
            ))
        }
    }

    private val _sessionsFlow = MutableStateFlow(mockSessions)

    override suspend fun saveNewParkingSession(session: UserParking): Result<String?> = Result.success(null)

    override suspend fun getActiveSessionByGeofence(geofenceId: String): UserParking? =
        mockSessions.find { it.isActive && it.geofenceId == geofenceId }

    override fun observeActiveSessions(): Flow<List<UserParking>> =
        _sessionsFlow.map { list -> list.filter { it.isActive } }

    override fun observeAllSessions(): Flow<List<UserParking>> = _sessionsFlow.asStateFlow()

    override fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>> =
        _sessionsFlow.map { list -> list.filter { it.vehicleId == vehicleId } }

    override suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking> =
        mockSessions.drop(offset).take(limit)

    override suspend fun getSessionsByVehiclePaged(vehicleId: String, limit: Int, offset: Int): List<UserParking> =
        mockSessions
            .filter { it.vehicleId == vehicleId && !it.isActive }
            .sortedByDescending { it.location.timestamp }
            .drop(offset)
            .take(limit)

    override suspend fun clearActiveParkingSession(sessionId: String): Result<Unit> = Result.success(Unit)

    override suspend fun syncFromRemote(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateParkingSessionAddressAndPlace(
        id: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun updateParkingSessionPosition(id: String, location: GpsPoint): Result<UserParking> {
        val session = mockSessions.find { it.id == id } ?: return Result.failure(Exception("Not found"))
        return Result.success(session.copy(location = location))
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)
}
