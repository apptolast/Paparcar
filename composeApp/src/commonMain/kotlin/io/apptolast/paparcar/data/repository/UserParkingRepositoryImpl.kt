package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.data.datasource.local.room.UserParkingDao
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.AddressInfo
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
     */
    override suspend fun saveSession(session: UserParking): Result<String?> =
        runCatching {
            val previousActive = dao.getActive()
            dao.clearActive()
            dao.insert(session.toEntity())
            previousActive?.id
        }

    override suspend fun getActiveSession(): UserParking? =
        dao.getActive()?.toDomain()

    override fun observeActiveSession(): Flow<UserParking?> =
        dao.observeActive().map { it?.toDomain() }

    override fun observeAllSessions(): Flow<List<UserParking>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>> =
        dao.observeByVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    override suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking> =
        dao.getSessionsPaged(limit, offset).map { it.toDomain() }

    /**
     * Room-only clear. Firestore reconciliation is scheduled via [ParkingSyncScheduler]
     * so this never suspends on network I/O. [PIPE-002]
     */
    override suspend fun clearActive(): Result<Unit> = runCatching {
        val active = dao.getActive()
        dao.clearActive()
        active?.let { entity ->
            parkingSyncScheduler.scheduleClearActive(entity.id)
        }
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
}
