@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.UserParkingDao
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.data.mapper.toParkingHistoryDto
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserParkingRepositoryImpl(
    private val dao: UserParkingDao,
    private val userProfileDataSource: RemoteUserProfileDataSource,
    private val parkingSyncScheduler: ParkingSyncScheduler,
) : UserParkingRepository {

    /**
     * Writes [session] to Room and atomically enqueues the Firestore sync worker. [PIPE-001]
     *
     * Both operations are co-located so that a process death after [dao.insert] but
     * before [parkingSyncScheduler.enqueueSaveNewParkingSession] cannot leave the session orphaned
     * in Room without a pending WorkManager job. If enqueue throws (WorkManager unavailable)
     * the failure is logged but does not fail the save — the session is already durable
     * in Room and will sync on the next manual enrichment or app restart.
     *
     * Multi-parking semantics: clears the previously-active session **only for the
     * same vehicleId** so each vehicle keeps its own independent active session.
     * Sessions saved without a vehicleId (legacy / unidentified) clear no rows. [MULTI-PARKING-001]
     */
    override suspend fun saveNewParkingSession(session: UserParking): Result<String?> =
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            // Atomic deactivate+insert in one Room transaction — process death can no longer
            // leave the vehicle without an active session mid-swap. [DET-SOLID-001]
            // Stamp updatedAt=now + pendingSync so BOTH the new active row AND the previous-session
            // clear win the inbound reconcile over any stale remote snapshot. [SYNC-RECONCILE-USERPARKING-001]
            val previousId = dao.replaceActiveSession(session.toEntity(updatedAt = now, pendingSync = true), now)
            runCatching { parkingSyncScheduler.enqueueSaveNewParkingSession(session, previousId) }
                .onFailure { e -> PaparcarLogger.e(TAG, "enqueueSaveNewParkingSession failed for session ${session.id} — may miss Firestore sync", e) }
            previousId
        }

    private companion object {
        const val TAG = "UserParkingRepository"
    }

    override suspend fun getActiveSessionByGeofence(geofenceId: String): UserParking? =
        dao.getActiveByGeofence(geofenceId)?.toDomain()

    override suspend fun getActiveSessionByVehicle(vehicleId: String): UserParking? =
        dao.getActiveByVehicle(vehicleId)?.toDomain()

    override fun observeActiveSessions(): Flow<List<UserParking>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAllSessions(): Flow<List<UserParking>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>> =
        dao.observeByVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    override suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking> =
        dao.getSessionsPaged(limit, offset).map { it.toDomain() }

    override suspend fun getSessionsByVehiclePaged(vehicleId: String, limit: Int, offset: Int): List<UserParking> =
        dao.getEndedSessionsByVehiclePaged(vehicleId, limit, offset).map { it.toDomain() }

    /**
     * Room-only clear of a specific session. Firestore reconciliation is scheduled via
     * [ParkingSyncScheduler.enqueueClearActiveParkingSession] so this never suspends on network I/O. [PIPE-002]
     */
    override suspend fun clearActiveParkingSession(sessionId: String): Result<Unit> = runCatching {
        // Stamp updatedAt=now + pendingSync so the deactivation wins the reconcile over a stale
        // remote isActive=true and gets drained to Firestore. [SYNC-RECONCILE-USERPARKING-001]
        dao.clearActiveById(sessionId, Clock.System.now().toEpochMilliseconds())
        parkingSyncScheduler.enqueueClearActiveParkingSession(sessionId)
    }

    /**
     * Inbound sync with Last-Write-Wins reconcile — supersedes the SYNC-UP-GUARD-001 stopgap. Local
     * is authoritative: a pending local edit strictly newer than remote is preserved; otherwise the
     * remote row wins (carrying local-only detection provenance). This is what stops a stale remote
     * snapshot from resurrecting an ended session or duplicating an active one (field incident
     * 2026-07-05). The one-active-per-vehicle invariant is enforced by [GeofenceJanitorWorker]'s
     * dedup sweep — not duplicated here. [SYNC-RECONCILE-USERPARKING-001]
     */
    override suspend fun syncFromRemote(userId: String): Result<Unit> =
        runCatching {
            val remoteEntities = userProfileDataSource.getParkingHistory(userId).map { it.toEntity() }
            val local = dao.getByUser(userId)
            val merged = reconcileParkingSessions(local = local, remote = remoteEntities)
            val keptPending = merged.count { it.pendingSync }
            PaparcarLogger.i(
                TAG,
                "syncFromRemote: ${remoteEntities.size} remote, ${local.size} local → ${merged.size} merged " +
                    "($keptPending pending kept local-truth) for user=$userId",
            )
            if (merged.isEmpty()) return@runCatching
            dao.upsertAll(merged)
            // [GEOF-001] Room now holds this user's active session(s); restore their GMS geofences
            // immediately. A reinstall wipes BOTH Room and the registered geofences, so without this
            // the geofence would not come back until the periodic janitor's next run (the gap that left
            // a fresh install undetected until a manual re-mark). Idempotent; no-op on platforms w/o WM.
            parkingSyncScheduler.enqueueGeofenceRestore()
        }

    /**
     * Outbox drainer: pushes every session with an un-synced local edit to Firestore as a full
     * document (covers save / clear-active / move / enrich in one write, stamping updatedAt so the
     * merge self-heals), clearing the pending flag on ack. Wired to the same triggers as vehicles
     * (fresh online start + reconnect) so an offline clear/move reliably reaches the cloud and
     * converges the remote mirror. [SYNC-RECONCILE-USERPARKING-001]
     */
    override suspend fun pushPendingParkingSessions(): Result<Unit> = runCatching {
        val pending = dao.getPendingSync()
        if (pending.isEmpty()) return@runCatching
        PaparcarLogger.d(TAG, "▶ pushPendingParkingSessions — draining ${pending.size} pending session(s)")
        pending.forEach { entity ->
            runCatching {
                userProfileDataSource.saveParkingSession(
                    entity.userId,
                    entity.toDomain().toParkingHistoryDto(updatedAt = entity.updatedAt),
                )
            }
                .onSuccess { dao.clearPending(entity.id) }
                .onFailure { e -> PaparcarLogger.w(TAG, "pushPendingParkingSessions: push failed for session=${entity.id}", e) }
        }
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> =
        runCatching { dao.deleteByUser(userId) }

    /**
     * Room-only update of geocoder fields. Firestore reconciliation is scheduled via
     * [ParkingSyncScheduler.enqueueUpdateParkingSessionAddressAndPlace]. [PIPE-002]
     */
    override suspend fun updateParkingSessionAddressAndPlace(
        id: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ): Result<Unit> = runCatching {
        dao.updateAddressAndPlace(
            id = id,
            street = address?.street,
            city = address?.city,
            region = address?.region,
            country = address?.country,
            placeInfoName = placeInfo?.name,
            placeInfoCategory = placeInfo?.category?.name,
            now = Clock.System.now().toEpochMilliseconds(),
        )
        parkingSyncScheduler.enqueueUpdateParkingSessionAddressAndPlace(id, address, placeInfo)
    }

    /**
     * Manual-edit path for the parked-car pin. Overwrites lat/lon in Room +
     * clears the cached address/POI so the re-scheduled enrichment fills them
     * with the new spot's geocode. Firestore reconciliation via a full
     * [ParkingSyncScheduler.enqueueSaveNewParkingSession] with `previousSessionId = null`
     * (we're mutating the active session in place, not transitioning).
     *
     * TODO[Phase 2]: replace with dedicated `enqueueUpdateParkingSessionPosition` to
     * avoid full set() — see refactor plan.
     */
    override suspend fun updateParkingSessionPosition(
        id: String,
        location: GpsPoint,
    ): Result<UserParking> = runCatching {
        dao.updateLocation(
            id = id,
            lat = location.latitude,
            lon = location.longitude,
            accuracy = location.accuracy,
            timestamp = location.timestamp,
            now = Clock.System.now().toEpochMilliseconds(),
        )
        val updated = dao.getById(id)?.toDomain()
            ?: error("No parking session with id=$id")
        parkingSyncScheduler.enqueueSaveNewParkingSession(updated, previousSessionId = null)
        updated
    }
}
