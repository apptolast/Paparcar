package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.UserParkingDao
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@OptIn(kotlin.time.ExperimentalTime::class)
class UserParkingRepositoryImpl(
    private val dao: UserParkingDao,
) : UserParkingRepository {

    private val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

    override suspend fun saveSession(session: UserParking): Result<Unit> =
        runCatching {
            dao.clearActive()
            dao.insert(session.toEntity())
            dao.deleteOldSessions(
                kotlin.time.Clock.System.now().toEpochMilliseconds() - THIRTY_DAYS_MS
            )
        }

    override suspend fun getActiveSession(): UserParking? =
        dao.getActive()?.toDomain()

    override fun observeActiveSession(): Flow<UserParking?> =
        dao.observeActive().map { it?.toDomain() }

    override suspend fun getAllSessions(): List<UserParking> =
        dao.getAll().map { it.toDomain() }

    override suspend fun clearActive(): Result<Unit> =
        runCatching { dao.clearActive() }

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
    }
}
