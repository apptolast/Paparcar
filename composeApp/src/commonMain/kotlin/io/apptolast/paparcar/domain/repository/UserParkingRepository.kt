package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
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
    /** Reactive stream of all currently-active sessions (0..N, one per parked vehicle). */
    fun observeActiveSessions(): Flow<List<UserParking>>
    fun observeAllSessions(): Flow<List<UserParking>>
    fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>>
    suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking>
    suspend fun getSessionsByVehiclePaged(vehicleId: String, limit: Int, offset: Int): List<UserParking>
    /** Clears the active flag of the session with [sessionId] and schedules Firestore reconciliation. */
    suspend fun clearActiveParkingSession(sessionId: String): Result<Unit>
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

    /** Deletes all local parking sessions for [userId]. Called during account deletion. */
    override suspend fun deleteAllData(userId: String): Result<Unit>
}
