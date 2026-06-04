package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.data.datasource.local.room.ZoneDao
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toDto
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.repository.ZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Repository managing habitual user zones.
 * Refactored to use [FirebaseDataSource] for architectural consistency. [SYNC-001]
 */
class ZoneRepositoryImpl(
    private val dao: ZoneDao,
    private val firebaseDataSource: FirebaseDataSource,
    private val authRepository: AuthRepository,
) : ZoneRepository {

    private suspend fun currentUserId(): String? =
        authRepository.getCurrentSession()?.userId

    override fun observeZones(): Flow<List<Zone>> = flow {
        val uid = currentUserId()
        if (uid == null) {
            emit(emptyList())
        } else {
            emitAll(dao.observeByUser(uid).map { list -> list.map { it.toDomain() } })
        }
    }

    override suspend fun syncFromRemote(userId: String): Result<Unit> = runCatching {
        // [FIX] Using explicit DTO mapping via DataSource to avoid SerializationException with 'Any'
        val remoteEntities = firebaseDataSource.getZones(userId)
            .map { it.toEntity() }
        if (remoteEntities.isEmpty()) return@runCatching
        dao.deleteByUser(userId)
        dao.upsertAll(remoteEntities)
    }

    override suspend fun saveZone(zone: Zone): Result<Unit> = runCatching {
        dao.insert(zone.toEntity())
        currentUserId()?.let { uid ->
            firebaseDataSource.saveZone(uid, zone.toDto())
        }
    }

    override suspend fun getPrivateZonesSnapshot(): List<Zone> =
        observeZones().first().filter { it.isPrivate }

    override suspend fun deleteZone(id: String): Result<Unit> = runCatching {
        val uid = currentUserId() ?: return@runCatching
        dao.deleteById(id, uid)
        firebaseDataSource.deleteZone(uid, id)
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> = runCatching {
        firebaseDataSource.deleteAllZones(userId)
        dao.deleteByUser(userId)
    }
}
