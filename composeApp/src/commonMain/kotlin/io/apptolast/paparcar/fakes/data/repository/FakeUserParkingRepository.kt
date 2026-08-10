@file:OptIn(ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.fakes.data.repository

import io.apptolast.paparcar.domain.detection.DetectionRuntimeState
import io.apptolast.paparcar.domain.model.*
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
class FakeUserParkingRepository(
    private val runtime: DetectionRuntimeState? = null,
    private val scenario: io.apptolast.paparcar.fakes.MockScenario? = null,
) : UserParkingRepository {
    private val now = Clock.System.now().toEpochMilliseconds()

    /** Seeded by the Dev Catalog "own parked session" lever: an ACTIVE session for the ACTIVE
     *  vehicle, so the real Home reaches the watching/parked state. [UX-PARKED-STATE-001] */
    private val ownParkedSeed = UserParking(
        id = "parking_own_active",
        userId = "mock_user_001",
        vehicleId = "mock_vehicle_001",
        location = GpsPoint(36.5915, -6.2285, 6f, now - 40 * 60_000L, 0f),
        isActive = true,
        spotType = SpotType.AUTO_DETECTED,
        detectionReliability = 0.95f,
        address = AddressInfo("Calle Luna, 18", "Puerto de Santa María", "Cádiz", "España", "ES"),
    )

    /** Sessions saved at runtime (manual mark / re-park) — the fake behaves like a repo. */
    private val savedSessions = MutableStateFlow<List<UserParking>>(emptyList())

    /** Ids released at runtime — releasing in the mock really frees the session. */
    private val releasedIds = MutableStateFlow<Set<String>>(emptySet())

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

    /** Base list + Dev-Catalog seed + runtime saves, with runtime releases applied. */
    private fun allSessionsFlow(): Flow<List<UserParking>> {
        val baseWithSeed = if (scenario == null) {
            _sessionsFlow.map { it }
        } else {
            combine(_sessionsFlow, scenario.ownParkedSession) { list, own ->
                if (own) list + ownParkedSeed else list
            }
        }
        return combine(baseWithSeed, savedSessions, releasedIds) { base, saved, released ->
            (base + saved).map { s -> if (s.id in released) s.copy(isActive = false) else s }
        }
    }

    private fun currentSessions(): List<UserParking> {
        val seed = if (scenario?.ownParkedSession?.value == true) listOf(ownParkedSeed) else emptyList()
        return (mockSessions + seed + savedSessions.value)
            .map { s -> if (s.id in releasedIds.value) s.copy(isActive = false) else s }
    }

    override suspend fun saveNewParkingSession(session: UserParking): Result<String?> {
        savedSessions.value = savedSessions.value + session.copy(isActive = true)
        return Result.success(session.id)
    }

    override suspend fun getActiveSessionByGeofence(geofenceId: String): UserParking? =
        currentSessions().find { it.isActive && it.geofenceId == geofenceId }

    override suspend fun getActiveSessionByVehicle(vehicleId: String): UserParking? =
        currentSessions().find { it.isActive && it.vehicleId == vehicleId }

    override suspend fun getSessionById(id: String): UserParking? =
        currentSessions().find { it.id == id }

    override fun observeActiveSessions(): Flow<List<UserParking>> {
        val active = allSessionsFlow().map { list -> list.filter { it.isActive } }
        val rt = runtime ?: return active
        // Sim fidelity: ALWAYS surface the parked session first, so a starting trip captures it as the
        // faded "departure point", THEN clear it while running so readiness can reach Monitoring (Parked
        // otherwise wins). Mirrors a real park → depart transition even when the toggle is flipped before
        // entering the app. [DRIVE-SIM-001] [TRIP-TRAIL-001]
        return active.flatMapLatest { sessions ->
            flow {
                emit(sessions)
                emitAll(rt.isRunning.map { running -> if (running) emptyList() else sessions })
            }
        }
    }

    override fun observeAllSessions(): Flow<List<UserParking>> = allSessionsFlow()

    override fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>> =
        allSessionsFlow().map { list -> list.filter { it.vehicleId == vehicleId } }

    override suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking> =
        currentSessions().drop(offset).take(limit)

    override suspend fun getSessionsByVehiclePaged(vehicleId: String, limit: Int, offset: Int): List<UserParking> =
        currentSessions()
            .filter { it.vehicleId == vehicleId && !it.isActive }
            .sortedByDescending { it.location.timestamp }
            .drop(offset)
            .take(limit)

    override suspend fun clearActiveParkingSession(sessionId: String): Result<Unit> {
        // Releasing really frees the session so the mock plays the whole loop: the parked card
        // and the watching line drop, and the story returns to its cold-start row. [UX-PARKED-STATE-001]
        releasedIds.value = releasedIds.value + sessionId
        return Result.success(Unit)
    }

    override suspend fun syncFromRemote(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun pushPendingParkingSessions(): Result<Unit> = Result.success(Unit)

    override suspend fun updateParkingSessionAddressAndPlace(
        id: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun updateParkingSessionPosition(id: String, location: GpsPoint): Result<UserParking> {
        val session = currentSessions().find { it.id == id } ?: return Result.failure(Exception("Not found"))
        return Result.success(session.copy(location = location))
    }

    override suspend fun updateParkingSessionRoute(
        id: String,
        routePolyline: String?,
        snapped: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)
}
