@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.data.datasource.local.room.ZoneDao
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toDto
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Repository managing habitual user zones.
 * Refactored to use [FirebaseDataSource] for architectural consistency. [SYNC-001]
 */
class ZoneRepositoryImpl(
    private val dao: ZoneDao,
    private val firebaseDataSource: FirebaseDataSource,
    private val authRepository: AuthRepository,
    /**
     * Background scope for the non-blocking Firestore mirror — a set().await() suspends until server
     * ack (i.e. forever while offline), so it must not gate the caller. Same rationale as
     * VehicleRepositoryImpl; the pendingSync flag + reconcile keep the local truth safe. [SYNC-RECONCILE-001]
     */
    private val syncScope: CoroutineScope,
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
        val remoteEntities = firebaseDataSource.getZones(userId).map { it.toEntity() }
        if (remoteEntities.isEmpty()) return@runCatching
        // Reconcile instead of blind remote-wins so an offline zone edit isn't clobbered by a stale
        // server snapshot. [SYNC-RECONCILE-001]
        val local = dao.getByUser(userId)
        val merged = reconcileZones(local = local, remote = remoteEntities)
        // [AUDIT-DATA-001 M5] Atomic swap (see VehicleDao.replaceAllForUser).
        dao.replaceAllForUser(userId, merged)
    }

    override suspend fun saveZone(zone: Zone): Result<Unit> = runCatching {
        val now = Clock.System.now().toEpochMilliseconds()
        // Local write (synchronous) — stamped pending so a stale sync can't revert it before confirmed.
        dao.insert(zone.toEntity(updatedAt = now, pendingSync = true))
        currentUserId()?.let { uid ->
            // Remote mirror in the background so the save doesn't hang on the ack. [SYNC-RECONCILE-001]
            syncScope.launch {
                runCatching { firebaseDataSource.saveZone(uid, zone.toDto()) }
                    .onSuccess { dao.clearPending(zone.id) }
                    .onFailure { e -> PaparcarLogger.w(TAG, "saveZone: remote failed ${zone.id}", e) }
            }
        }
    }

    override suspend fun getPrivateZonesSnapshot(): List<Zone> =
        observeZones().first().filter { it.isPrivate }

    override suspend fun deleteZone(id: String): Result<Unit> = runCatching {
        val uid = currentUserId() ?: return@runCatching
        dao.deleteById(id, uid) // local — gone immediately
        syncScope.launch {
            runCatching { firebaseDataSource.deleteZone(uid, id) }
                .onFailure { e -> PaparcarLogger.w(TAG, "deleteZone: remote failed $id", e) }
        }
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> = runCatching {
        firebaseDataSource.deleteAllZones(userId)
        dao.deleteByUser(userId)
    }

    override suspend fun pushPendingZones(): Result<Unit> = runCatching {
        val pending = dao.getPendingSync()
        if (pending.isEmpty()) return@runCatching
        PaparcarLogger.d(TAG, "▶ pushPendingZones — draining ${pending.size} pending zone(s)")
        pending.forEach { entity ->
            runCatching { firebaseDataSource.saveZone(entity.userId, entity.toDomain().toDto()) }
                .onSuccess { dao.clearPending(entity.id) }
                .onFailure { e -> PaparcarLogger.w(TAG, "pushPendingZones: push failed for zone=${entity.id}", e) }
        }
    }

    private companion object {
        const val TAG = "ZoneRepo"
    }
}
