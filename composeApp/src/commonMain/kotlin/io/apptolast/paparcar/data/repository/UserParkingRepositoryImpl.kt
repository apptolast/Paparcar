package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.UserParkingSessionDao
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.UserParkingSession
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserParkingRepositoryImpl(
    private val dao: UserParkingSessionDao,
) : UserParkingRepository {

    override suspend fun saveSession(session: UserParkingSession): Result<Unit> =
        runCatching {
            // Ensure only one active session exists at a time: clear the previous
            // one before inserting so the reactive Flow always emits the latest session.
            dao.clearActive()
            dao.insert(session.toEntity())
        }

    override suspend fun getActiveSession(): UserParkingSession? =
        dao.getActive()?.toDomain()

    override fun observeActiveSession(): Flow<UserParkingSession?> =
        dao.observeActive().map { it?.toDomain() }

    override suspend fun getAllSessions(): List<UserParkingSession> =
        dao.getAll().map { it.toDomain() }

    override suspend fun clearActive(): Result<Unit> =
        runCatching { dao.clearActive() }
}
