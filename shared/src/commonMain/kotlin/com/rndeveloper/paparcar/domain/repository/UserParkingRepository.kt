package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.RouteInferenceResolution
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlinx.coroutines.flow.Flow

interface UserParkingRepository : UserScopedRepository, RemoteSyncable {
    /**
     * Inserts [session] into Room as the new active session, then enqueues a Firestore
     * sync worker in the same logical step. Clears the previously-active row **scoped
     * to the same vehicleId** so concurrent sessions for *different* vehicles remain active
     * in parallel. Returns the id of the previous session that was cleared (if any).
     */
    suspend fun saveNewParkingSession(session: UserParking): Result<String?>
    /** Returns the currently-active session whose geofenceId matches [geofenceId], or null. */
    suspend fun getActiveSessionByGeofence(geofenceId: String): UserParking?
    /** Returns the currently-active session for [vehicleId], or null. Feeds the
     *  repark-plausibility guard in ConfirmParkingUseCase. [DET-SOLID-001] */
    suspend fun getActiveSessionByVehicle(vehicleId: String): UserParking?
    /** Returns the session with [id], or null. Used by the post-park worker to read the raw route it
     *  is about to snap. [DET-ROUTE-SNAP-STORE-001] */
    suspend fun getSessionById(id: String): UserParking?
    /** Reactive stream of all currently-active sessions (0..N, one per parked vehicle). */
    fun observeActiveSessions(): Flow<List<UserParking>>
    fun observeAllSessions(): Flow<List<UserParking>>
    fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>>
    suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking>
    suspend fun getSessionsByVehiclePaged(vehicleId: String, limit: Int, offset: Int): List<UserParking>
    /**
     * Clears the active flag of the session with [sessionId], stamping the close's provenance on
     * the row, and schedules Firestore reconciliation.
     *
     * @param endedAtMs the moment the departure actually happened — `now` for a witnessed/manual
     *   close, the deduced-departure instant when a promotion finalizes one. The first close wins
     *   (idempotent re-clears never move it).
     * @param publishedSpot whether this close published a community spot (tu aparcamiento liberó
     *   una plaza). Confirmable later, never retractable. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
     */
    suspend fun clearActiveParkingSession(
        sessionId: String,
        endedAtMs: Long,
        publishedSpot: Boolean,
    ): Result<Unit>

    /**
     * [DET-HANDOFF-NOT-MANUAL-001 §B] Records (or clears, with [atMs] = null) that a DEDUCED
     * departure published this session's spot provisionally and left the session ALIVE. Local-only:
     * the marker coordinates the promote-vs-expire decision on this device and is never synced.
     */
    suspend fun markProvisionalDeparture(sessionId: String, atMs: Long?): Result<Unit>

    /**
     * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] Withdraws a parking the app itself disproved (a
     * backfill pin whose own departure refuted it within `refutedPinMaxLifeMs`) or that the user
     * reverted. The row is KEPT — a withdrawal is a state, not a delete, for the reason
     * [com.rndeveloper.paparcar.domain.model.SpotStatus] wrote down — and disappears from the
     * history reads while every diagnostic read still sees it. Synced: the withdrawal has to reach
     * the user's other devices like any other close.
     *
     * Idempotent: the first withdrawal instant wins, like the first close.
     */
    suspend fun retractParkingSession(sessionId: String, retractedAtMs: Long): Result<Unit>
    /**
     * Downloads parking history from Firestore and merges it into Room with a Last-Write-Wins
     * reconcile: a pending local edit strictly newer than remote is preserved; otherwise remote
     * wins. Local is authoritative (local-first writes + a lagging outbound mirror), so a stale
     * remote snapshot can never resurrect an ended session or clobber an offline edit.
     * [SYNC-RECONCILE-USERPARKING-001]
     */
    override suspend fun syncFromRemote(userId: String): Result<Unit>
    /**
     * Outbox drainer — pushes every session with an un-synced local edit to Firestore (full-doc
     * write stamping updatedAt), clearing its pending flag on ack. Called on fresh online start and
     * on reconnect so an offline clear/move reliably reaches the cloud and converges the remote
     * mirror. [SYNC-RECONCILE-USERPARKING-001]
     */
    suspend fun pushPendingParkingSessions(): Result<Unit>
    /**
     * Writes geocoder-resolved address and POI fields for an existing session.
     *
     * Only the address/POI columns are touched — lat/lon remain unchanged.
     * Called by background enrichment workers after a successful reverse-geocode.
     * Schedules [ParkingSyncScheduler.enqueueUpdateParkingSessionAddressAndPlace] to propagate to Firestore.
     */
    suspend fun updateParkingSessionAddressAndPlace(
        id: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ): Result<Unit>

    /**
     * Overwrites the GPS coordinates of an existing session and clears the cached
     * address/POI so the next enrichment pass re-geocodes the new position.
     *
     * Used by the manual "Move location" flow when the user re-positions an already-parked
     * vehicle on the map. Schedules a full Firestore set() via [ParkingSyncScheduler.enqueueSaveNewParkingSession].
     */
    suspend fun updateParkingSessionPosition(
        id: String,
        location: GpsPoint,
    ): Result<UserParking>

    /**
     * Writes the driven [routePolyline] + [snapped] flag onto session [id] and schedules Firestore
     * reconciliation. Called by the post-park worker when it snaps the raw route onto streets (once),
     * so the history draws the on-road line without re-computing. [DET-ROUTE-SNAP-STORE-001]
     */
    /** The route's haversine length is stamped alongside it by the implementation, so route and
     *  distance can never diverge. [VEH-STATS-SAY-SOMETHING-USEFUL-001] */
    suspend fun updateParkingSessionRoute(
        id: String,
        routePolyline: String?,
        snapped: Boolean,
        inferredSpans: String? = null,
    ): Result<Unit>

    /**
     * The vehicle's parking session right before [beforeTimestamp] — the origin of the trip that
     * ended at the session in question. Null when there is none. [ROUTE-GAP-HONEST-001]
     */
    suspend fun getPreviousSession(vehicleId: String, beforeTimestamp: Long): UserParking?

    /**
     * Stores the user's verdict on a route's road-inferred stretches — the answer to the
     * "did you drive this way?" question — and schedules Firestore reconciliation.
     * [ROUTE-GAP-HONEST-001]
     */
    suspend fun resolveInferredRoute(
        id: String,
        resolution: RouteInferenceResolution,
    ): Result<Unit>

    /** Deletes all local parking sessions for [userId]. Called during account deletion. */
    override suspend fun deleteAllData(userId: String): Result<Unit>
}
