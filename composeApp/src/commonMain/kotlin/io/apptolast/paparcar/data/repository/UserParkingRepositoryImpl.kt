package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.UserParkingDao
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

@OptIn(kotlin.time.ExperimentalTime::class)
private val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

class UserParkingRepositoryImpl(
    private val dao: UserParkingDao,
) : UserParkingRepository {

    @OptIn(kotlin.time.ExperimentalTime::class)
    override suspend fun saveSession(session: UserParking): Result<Unit> =
        runCatching {
            // Ensure only one active session exists at a time.
            dao.clearActive()
            // Prune ended sessions older than 30 days to keep the table small.
            dao.insert(session.toEntity())
            dao.deleteOldSessions(kotlin.time.Clock.System.now().toEpochMilliseconds() - THIRTY_DAYS_MS)
        }

    override suspend fun getActiveSession(): UserParking? =
        dao.getActive()?.toDomain()

    override fun observeActiveSession(): Flow<UserParking?> =
        dao.observeActive().map { it?.toDomain() }

    override suspend fun getAllSessions(): List<UserParking> =
        dao.getAll().map { it.toDomain() }

    override suspend fun clearActive(): Result<Unit> =
        runCatching { dao.clearActive() }
}
