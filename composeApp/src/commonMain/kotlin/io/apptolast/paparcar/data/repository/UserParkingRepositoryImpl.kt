package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.ParkingSessionDao
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.ParkingSession
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserParkingRepositoryImpl(
    private val dao: ParkingSessionDao,
) : UserParkingRepository {

    override suspend fun saveSession(session: ParkingSession): Result<Unit> =
        runCatching { dao.insert(session.toEntity()) }

    override suspend fun getActiveSession(): ParkingSession? =
        dao.getActive()?.toDomain()

    override fun observeActiveSession(): Flow<ParkingSession?> =
        dao.observeActive().map { it?.toDomain() }

    override suspend fun getAllSessions(): List<ParkingSession> =
        dao.getAll().map { it.toDomain() }

    override suspend fun clearActive(): Result<Unit> =
        runCatching { dao.clearActive() }
}
