package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import kotlinx.coroutines.flow.Flow

interface UserParkingRepository {
    /**
     * Inserts [session] into Room as the new active session, clearing any previously active
     * row. Returns the id of the previous active session (if any) wrapped in a [Result] so
     * the caller can hand it to [io.apptolast.paparcar.domain.service.ParkingSyncScheduler]
     * for remote backfill — Firestore writes happen off the critical path.
     */
    suspend fun saveSession(session: UserParking): Result<String?>
    suspend fun getActiveSession(): UserParking?
    fun observeActiveSession(): Flow<UserParking?>
    fun observeAllSessions(): Flow<List<UserParking>>
    fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>>
    suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking>
    suspend fun clearActive(): Result<Unit>
    /**
     * Downloads parking history from Firestore and populates Room.
     * No-op if Room already has data — covers new installs and device switches.
     */
    suspend fun syncParkingHistoryFromRemote(userId: String): Result<Unit>
    /** In-place update of address+POI for an existing session. Does not affect [isActive]. */
    suspend fun updateLocationInfo(
        id: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ): Result<Unit>

    /**
     * In-place update of lat/lon for an existing session. Used by the manual
     * "Move location" flow when the user re-positions an already-parked
     * vehicle. Clears the cached address+POI fields so the next enrichment
     * pass overwrites them with the new location's geocode.
     */
    suspend fun updateLocation(
        id: String,
        location: GpsPoint,
    ): Result<UserParking>

    /** Deletes all local parking sessions for [userId]. Called during account deletion. */
    suspend fun deleteAllData(userId: String): Result<Unit>
}
