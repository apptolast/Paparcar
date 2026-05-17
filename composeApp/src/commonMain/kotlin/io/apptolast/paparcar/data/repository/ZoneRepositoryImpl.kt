package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.local.room.ZoneDao
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.data.mapper.toFirestoreMap
import io.apptolast.paparcar.data.mapper.toZoneEntity
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.repository.ZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class ZoneRepositoryImpl(
    private val dao: ZoneDao,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) : ZoneRepository {

    private suspend fun currentUserId(): String? =
        authRepository.getCurrentSession()?.userId

    /**
     * Auth-reactive zone stream. Resolves the userId once via
     * [AuthRepository.getCurrentSession] (cache-backed since AUTH-002) —
     * sign-out tears down the screen using this Flow so we don't need
     * to re-subscribe on auth state changes.
     */
    override fun observeZones(): Flow<List<Zone>> = flow {
        val uid = currentUserId()
        if (uid == null) {
            emit(emptyList())
        } else {
            emitAll(dao.observeByUser(uid).map { list -> list.map { it.toDomain() } })
        }
    }

    override suspend fun syncFromRemote(userId: String): Result<Unit> = runCatching {
        val snapshot = firestoreZonesCol(userId).get()
        val remoteEntities = snapshot.documents
            .mapNotNull { it.data<Map<String, Any?>>().toZoneEntity() }
        if (remoteEntities.isEmpty()) return@runCatching
        dao.upsertAll(remoteEntities)
    }

    override suspend fun saveZone(zone: Zone) {
        dao.insert(zone.toEntity())
        currentUserId()?.let { uid ->
            firestoreZonesCol(uid).document(zone.id).set(zone.toFirestoreMap())
        }
    }

    override suspend fun deleteZone(id: String) {
        val uid = currentUserId() ?: return
        dao.deleteById(id, uid)
        firestoreZonesCol(uid).document(id).delete()
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> = runCatching {
        firestoreZonesCol(userId).get().documents.forEach { it.reference.delete() }
        dao.deleteByUser(userId)
    }

    private fun firestoreZonesCol(userId: String) =
        firestore.collection("users").document(userId).collection("zones")
}
