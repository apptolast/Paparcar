package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.data.datasource.local.room.UserParkingDao
import io.apptolast.paparcar.data.datasource.remote.UserProfileDataSource
import io.apptolast.paparcar.data.mapper.toAddressDto
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.data.mapper.toParkingHistoryDto
import io.apptolast.paparcar.data.mapper.toPlaceInfoDto
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserParkingRepositoryImpl(
    private val dao: UserParkingDao,
    private val userProfileDataSource: UserProfileDataSource,
    private val authRepository: AuthRepository,
) : UserParkingRepository {

    private suspend fun currentUserId(): String? =
        authRepository.getCurrentSession()?.userId

    override suspend fun saveSession(session: UserParking): Result<Unit> =
        runCatching {
            val previousActive = dao.getActive()

            dao.clearActive()
            dao.insert(session.toEntity())

            currentUserId()?.let { userId ->
                // Persist the now-deactivated previous session to Firestore
                previousActive?.let { prev ->
                    userProfileDataSource.saveParkingSession(
                        userId,
                        prev.toDomain().copy(isActive = false).toParkingHistoryDto(),
                    )
                }
                // Persist the new active session
                userProfileDataSource.saveParkingSession(userId, session.toParkingHistoryDto())
            }
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

    override suspend fun clearActive(): Result<Unit> = runCatching {
        val active = dao.getActive()
        dao.clearActive()
        // Keep Firestore in sync: mark the session as inactive so it is not
        // re-imported as an active session on the next login via syncParkingHistoryFromRemote().
        active?.let { entity ->
            currentUserId()?.let { userId ->
                userProfileDataSource.saveParkingSession(
                    userId,
                    entity.toDomain().copy(isActive = false).toParkingHistoryDto(),
                )
            }
        }
    }

    override suspend fun syncParkingHistoryFromRemote(userId: String): Result<Unit> =
        runCatching {
            // Always sync on login — inserts are idempotent (REPLACE strategy).
            // Covers new installs, device switches, and multi-device scenarios.
            userProfileDataSource.getParkingHistory(userId).forEach { dto ->
                dao.insert(dto.toEntity())
            }
        }

    override suspend fun deleteAllData(userId: String): Result<Unit> =
        runCatching { dao.deleteByUser(userId) }

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
        currentUserId()?.let { userId ->
            userProfileDataSource.updateParkingSessionLocation(
                userId = userId,
                sessionId = id,
                address = address?.toAddressDto(),
                placeInfo = placeInfo?.toPlaceInfoDto(),
            )
        }
    }
}
