package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.data.datasource.local.room.UserParkingDao
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserParkingRepositoryImpl(
    private val dao: UserParkingDao,
    private val userProfileDataSource: RemoteUserProfileDataSource,
    private val authRepository: AuthRepository,
    private val parkingSyncScheduler: ParkingSyncScheduler,
) : UserParkingRepository {

    /**
     * Room-only. Firestore propagation lives in [ParkingSyncWorker], scheduled by
     * [ConfirmParkingUseCase] using the [previousActive] id returned here.
     * Keeps the confirm-parking critical path bounded by local I/O only. [PIPE-001]
     *
     * Multi-parking semantics: clears the previously-active session **only for the
     * same vehicleId** so each vehicle keeps its own independent active session.
     * Sessions saved without a vehicleId (legacy / unidentified) clear no rows. [MULTI-PARKING-001]
     */
    override suspend fun saveSession(session: UserParking): Result<String?> =
        runCatching {
            val previousActive = session.vehicleId?.let { dao.getActiveByVehicle(it) }
            session.vehicleId?.let { dao.clearActiveByVehicle(it) }
            dao.insert(session.toEntity())
            previousActive?.id
        }

    override suspend fun getActiveSessionByGeofence(geofenceId: String): UserParking? =
        dao.getActiveByGeofence(geofenceId)?.toDomain()

    override fun observeActiveSessions(): Flow<List<UserParking>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAllSessions(): Flow<List<UserParking>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>> =
        dao.observeByVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    override suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking> =
        dao.getSessionsPaged(limit, offset).map { it.toDomain() }

    /**
     * Room-only clear of a specific session. Firestore reconciliation is scheduled via
     * [ParkingSyncScheduler] so this never suspends on network I/O. [PIPE-002]
     */
    override suspend fun clearActiveById(sessionId: String): Result<Unit> = runCatching {
        dao.clearActiveById(sessionId)
        parkingSyncScheduler.scheduleClearActive(sessionId)
    }

    override suspend fun syncParkingHistoryFromRemote(userId: String): Result<Unit> =
        runCatching {
            val remoteEntities = userProfileDataSource.getParkingHistory(userId)
                .map { it.toEntity() }
            if (remoteEntities.isEmpty()) return@runCatching
            dao.upsertAll(remoteEntities)
        }

    override suspend fun deleteAllData(userId: String): Result<Unit> =
        runCatching { dao.deleteByUser(userId) }

    /**
     * Room-only update. Firestore reconciliation is scheduled via [ParkingSyncScheduler]. [PIPE-002]
     */
    override suspend fun updateLocationInfo(
        id: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ): Result<Unit> = runCatching {
        dao.updateLocationInfo(
            id = id,
            street = address?.street,
            city = address?.city,
            region = address?.region,
            country = address?.country,
            placeInfoName = placeInfo?.name,
            placeInfoCategory = placeInfo?.category?.name,
        )
        parkingSyncScheduler.scheduleLocationUpdate(id, address, placeInfo)
    }

    /**
     * Manual-edit path for the parked-car pin. Overwrites lat/lon in Room +
     * clears the cached address/POI so the re-scheduled enrichment fills them
     * with the new spot's geocode. Firestore reconciliation rides on top via
     * [ParkingSyncScheduler.schedule] with `previousSessionId = null` (we're
     * not transitioning between sessions, just mutating the active one).
     */
    override suspend fun updateLocation(
        id: String,
        location: GpsPoint,
    ): Result<UserParking> = runCatching {
        dao.updateLocation(
            id = id,
            lat = location.latitude,
            lon = location.longitude,
            accuracy = location.accuracy,
            timestamp = location.timestamp,
        )
        val updated = dao.getById(id)?.toDomain()
            ?: error("No parking session with id=$id")
        parkingSyncScheduler.schedule(updated, previousSessionId = null)
        updated
    }
}
