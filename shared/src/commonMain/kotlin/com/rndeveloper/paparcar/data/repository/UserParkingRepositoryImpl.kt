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
import com.rndeveloper.paparcar.domain.model.RefuelStopAnswer
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
        refuelStop: RefuelStopAnswer?,
    ): Result<Unit> = runCatching {
        // Stamp updatedAt=now + pendingSync so the deactivation wins the reconcile over a stale
        // remote isActive=true. [SYNC-RECONCILE-USERPARKING-001] [VEH-STATS-SAY-SOMETHING-USEFUL-001]
        dao.clearActiveById(
            sessionId,
            endedAtMs,
            publishedSpot,
            refuelStop?.name,
            Clock.System.now().toEpochMilliseconds(),
        )
        // Camino RÁPIDO: parchea `isActive` remoto y avisa al espejo del grupo.
        parkingSyncScheduler.enqueueClearActiveParkingSession(sessionId)
        // Y la red durable detrás. [SYNC-A-CLOSED-SESSION-MUST-REACH-FIRESTORE-WHOLE-001]
        //
        // ⛔ Aquí ponía que `endedAtMs`/`publishedSpot` viajaban «en el push de documento completo
        // del outbox drainer». **No existía tal push**: la op `ClearActive` del drainer llama al
        // mismo parche de un campo, y quien sube el documento entero es `pushPendingParkingSessions`,
        // que sólo dispara `AppViewModel` al arrancar online. Un cierre en background —el caso
        // NORMAL, la detección cierra con el móvil en el bolsillo— dejaba el espejo diciendo
        // «cerrada» sin decir cuándo hasta que el usuario volviera a abrir la app. Medido en
        // Firestore el 22-09: `isActive:false`, `endedAtMs:null` y el `updatedAt` del alta, tres
        // días después.
        pushSessionSnapshot(sessionId)
    }

    /**
     * [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] The user's answer to «¿Repostaste aquí?».
     *
     * Sube por el push de documento ENTERO y no por el parche de un campo: no hay op del drainer
     * para esta columna, y un parche inventado para ella sería el cuarto carril de escritura de
     * esta misma fila. El `pendingSync` que estampa el DAO es lo que la hace sobrevivir a quedarse
     * sin red — la respuesta se da muchas veces con la app recién abierta.
     */
    override suspend fun answerRefuelStop(
        sessionId: String,
        answer: RefuelStopAnswer,
    ): Result<Unit> = runCatching {
        dao.answerRefuelStopById(sessionId, answer.name, Clock.System.now().toEpochMilliseconds())
        pushSessionSnapshot(sessionId)
    }

    override fun observePendingRefuelStop(): Flow<UserParking?> =
        dao.observeLatestPendingRefuelStop().map { it?.toDomain() }

    /**
     * [DET-A-TRAIN-IS-NOT-A-CAR-001] Re-opens a refuted departure's session.
     *
     * ⛔ **Deliberately does NOT enqueue [ParkingSyncScheduler.enqueueClearActiveParkingSession]**,
     * which every other write in this neighbourhood does. That worker is not a generic "sync this
     * session" — it calls `clearParkingSessionActiveFlag`, a targeted update that hard-sets
     * `isActive = false` remotely. Enqueuing it here would schedule a worker whose whole job is to
     * undo the re-opening we just performed, and it would win as soon as the network came back.
     *
     * La reapertura sube por [ParkingSyncScheduler.enqueueParkingSessionSnapshot], que es el push de
     * documento completo — el mismo carril por el que ahora viaja la provenance del cierre.
     * ⛔ Antes decía que «viajaba en `pendingSync` sobre el push del outbox drainer»; ese push no
     * existía y la reapertura se quedaba sin llegar al espejo hasta el siguiente arranque de app.
     * [SYNC-A-CLOSED-SESSION-MUST-REACH-FIRESTORE-WHOLE-001]
     */
    override suspend fun restoreParkingSession(sessionId: String): Result<Unit> = runCatching {
        dao.reopenById(sessionId, Clock.System.now().toEpochMilliseconds())
        pushSessionSnapshot(sessionId)
    }

    /** [DET-HANDOFF-NOT-MANUAL-001 §B] Room-only write — no sync enqueue: the marker is a local
     *  coordination flag between the deduced departure and the moment a drive is (or is not)
     *  proven. Firestore never sees it, and a remote reconcile preserves it (see
     *  [com.rndeveloper.paparcar.data.repository.reconcileParkingSession]). */
    override suspend fun markProvisionalDeparture(sessionId: String, atMs: Long?): Result<Unit> = runCatching {
        dao.setProvisionalDeparture(sessionId, atMs, Clock.System.now().toEpochMilliseconds())
    }

    /**
     * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] Room write plus las MISMAS dos encoladas que el
     * cierre: el parche rápido del flag y el push del documento completo, que es quien lleva el
     * `retractedAtMs` al espejo. ⛔ La frase anterior —«viaja en `pendingSync` sobre el push del
     * outbox drainer»— describía un push que no existía.
     * [SYNC-A-CLOSED-SESSION-MUST-REACH-FIRESTORE-WHOLE-001]
     */
    override suspend fun retractParkingSession(sessionId: String, retractedAtMs: Long): Result<Unit> =
        runCatching {
            dao.retractById(sessionId, retractedAtMs, Clock.System.now().toEpochMilliseconds())
            parkingSyncScheduler.enqueueClearActiveParkingSession(sessionId)
            pushSessionSnapshot(sessionId)
        }

    /**
     * Encola el push de documento completo de [sessionId], releyendo la fila ya escrita: lo que sube
     * es el estado de AHORA, no el que tuviera el llamante en la mano.
     * [SYNC-A-CLOSED-SESSION-MUST-REACH-FIRESTORE-WHOLE-001]
     *
     * Una fila que ya no existe no es un error: significa que no hay nada que espejar.
     */
    private suspend fun pushSessionSnapshot(sessionId: String) {
        val session = dao.getById(sessionId)?.toDomain() ?: return
        parkingSyncScheduler.enqueueParkingSessionSnapshot(session)
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
            countryCode = address?.countryCode,
            province = address?.province,
            postalCode = address?.postalCode,
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
        detectionPath: String,
        detectionReliability: Float,
    ): Result<UserParking> = runCatching {
        dao.updateLocation(
            id = id,
            lat = location.latitude,
            lon = location.longitude,
            accuracy = location.accuracy,
            // El reloj de la sesión no viaja con la POSICIÓN: lo que se corrige es dónde está el
            // coche, no desde cuándo. [PARK-A-CORRECTION-MOVES-THE-PLACE-NOT-THE-CLOCK-001]
            detectionPath = detectionPath,
            detectionReliability = detectionReliability,
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
