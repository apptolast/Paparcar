@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingDao
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.mapper.toDomain
import com.rndeveloper.paparcar.data.mapper.toEntity
import com.rndeveloper.paparcar.data.mapper.toParkingHistoryDto
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.RouteInferenceResolution
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.util.PolylineCodec
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
            // Stamp the raw route's length next to it — the repo is the choke point of every route
            // write, so route and distance can never diverge. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
            val stamped = session.copy(
                routeDistanceMeters = session.routeDistanceMeters
                    ?: PolylineCodec.lengthMeters(session.routePolyline),
            )
            // Atomic deactivate+insert in one Room transaction — process death can no longer
            // leave the vehicle without an active session mid-swap. [DET-SOLID-001]
            // Stamp updatedAt=now + pendingSync so BOTH the new active row AND the previous-session
            // clear win the inbound reconcile over any stale remote snapshot. [SYNC-RECONCILE-USERPARKING-001]
            val previousId = dao.replaceActiveSession(stamped.toEntity(updatedAt = now, pendingSync = true), now)
            runCatching { parkingSyncScheduler.enqueueSaveNewParkingSession(stamped, previousId) }
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

    override suspend fun getSessionById(id: String): UserParking? =
        dao.getById(id)?.toDomain()

    override suspend fun getPreviousSession(vehicleId: String, beforeTimestamp: Long): UserParking? =
        dao.getPreviousByVehicle(vehicleId, beforeTimestamp)?.toDomain()

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
    override suspend fun clearActiveParkingSession(
        sessionId: String,
        endedAtMs: Long,
        publishedSpot: Boolean,
    ): Result<Unit> = runCatching {
        // Stamp updatedAt=now + pendingSync so the deactivation wins the reconcile over a stale
        // remote isActive=true and gets drained to Firestore. [SYNC-RECONCILE-USERPARKING-001]
        // endedAtMs/publishedSpot ride the SAME pendingSync: the fast-path worker only patches
        // isActive remotely; the outbox drainer's full-doc push carries the close provenance.
        // [VEH-STATS-SAY-SOMETHING-USEFUL-001]
        dao.clearActiveById(sessionId, endedAtMs, publishedSpot, Clock.System.now().toEpochMilliseconds())
        parkingSyncScheduler.enqueueClearActiveParkingSession(sessionId)
    }

    /** [DET-HANDOFF-NOT-MANUAL-001 §B] Room-only write — no sync enqueue: the marker is a local
     *  coordination flag between the deduced departure and the moment a drive is (or is not)
     *  proven. Firestore never sees it, and a remote reconcile preserves it (see
     *  [com.rndeveloper.paparcar.data.repository.reconcileParkingSession]). */
    override suspend fun markProvisionalDeparture(sessionId: String, atMs: Long?): Result<Unit> = runCatching {
        dao.setProvisionalDeparture(sessionId, atMs, Clock.System.now().toEpochMilliseconds())
    }

    /**
     * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] Room write plus the SAME sync enqueue the close
     * uses. The withdrawal rides `pendingSync` on the outbox drainer's full-document push, so it
     * reaches Firestore through the ordinary reconcile — nothing new in the sync layer.
     */
    override suspend fun retractParkingSession(sessionId: String, retractedAtMs: Long): Result<Unit> =
        runCatching {
            dao.retractById(sessionId, retractedAtMs, Clock.System.now().toEpochMilliseconds())
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

    /**
     * Room update of the driven route + snapped flag. Firestore reconciliation via a full
     * [ParkingSyncScheduler.enqueueSaveNewParkingSession] (previousSessionId = null) — same in-place
     * mutation pattern as the position edit, so the snapped polyline reaches the remote mirror.
     * [DET-ROUTE-SNAP-STORE-001]
     */
    override suspend fun updateParkingSessionRoute(
        id: String,
        routePolyline: String?,
        snapped: Boolean,
        inferredSpans: String?,
    ): Result<Unit> = runCatching {
        dao.updateRoute(
            id = id,
            routePolyline = routePolyline,
            routeSnapped = snapped,
            routeInferredSpans = inferredSpans,
            // Stamped here, not by callers — same write, so they cannot diverge.
            // [VEH-STATS-SAY-SOMETHING-USEFUL-001]
            routeDistanceMeters = PolylineCodec.lengthMeters(routePolyline),
            now = Clock.System.now().toEpochMilliseconds(),
        )
        val updated = dao.getById(id)?.toDomain() ?: error("No parking session with id=$id")
        parkingSyncScheduler.enqueueSaveNewParkingSession(updated, previousSessionId = null)
    }

    /**
     * Room update of the user's verdict on the route's inferred stretches, mirrored to Firestore
     * with the same in-place mutation pattern as the route write. [ROUTE-GAP-HONEST-001]
     */
    override suspend fun resolveInferredRoute(
        id: String,
        resolution: RouteInferenceResolution,
    ): Result<Unit> = runCatching {
        dao.updateRouteResolution(
            id = id,
            resolution = resolution.name,
            now = Clock.System.now().toEpochMilliseconds(),
        )
        val updated = dao.getById(id)?.toDomain() ?: error("No parking session with id=$id")
        parkingSyncScheduler.enqueueSaveNewParkingSession(updated, previousSessionId = null)
    }
}
