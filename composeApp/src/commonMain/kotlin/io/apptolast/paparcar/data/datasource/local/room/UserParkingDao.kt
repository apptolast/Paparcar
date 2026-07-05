package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UserParkingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: UserParkingEntity)

    /** REPLACE-conflict bulk insert used by [UserParkingRepository.syncFromRemote]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<UserParkingEntity>)

    /** Ids of every locally-known session — the import guard of `syncFromRemote` uses this to
     *  insert only rows Room has never seen. [SYNC-UP-GUARD-001] */
    @Query("SELECT id FROM parking_sessions")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 AND geofenceId = :geofenceId LIMIT 1")
    suspend fun getActiveByGeofence(geofenceId: String): UserParkingEntity?

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 AND vehicleId = :vehicleId LIMIT 1")
    suspend fun getActiveByVehicle(vehicleId: String): UserParkingEntity?

    @Query("SELECT * FROM parking_sessions ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParkingEntity>

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId AND isActive = 0 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getEndedSessionsByVehiclePaged(vehicleId: String, limit: Int, offset: Int): List<UserParkingEntity>

    @Query("SELECT * FROM parking_sessions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<UserParkingEntity>>

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 ORDER BY timestamp DESC")
    fun observeActive(): Flow<List<UserParkingEntity>>

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 ORDER BY timestamp DESC")
    suspend fun getAllActive(): List<UserParkingEntity>

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun observeByVehicle(vehicleId: String): Flow<List<UserParkingEntity>>

    @Query("UPDATE parking_sessions SET isActive = 0 WHERE id = :sessionId")
    suspend fun clearActiveById(sessionId: String)

    @Query("UPDATE parking_sessions SET isActive = 0 WHERE isActive = 1 AND vehicleId = :vehicleId")
    suspend fun clearActiveByVehicle(vehicleId: String)

    /** Deactivates legacy/unidentified active sessions (no vehicleId). Without this, a new
     *  vehicle-less session would ACCUMULATE next to previous vehicle-less actives. [DET-SOLID-001] */
    @Query("UPDATE parking_sessions SET isActive = 0 WHERE isActive = 1 AND vehicleId IS NULL")
    suspend fun clearActiveOrphans()

    /**
     * Atomic replace of the vehicle's active session: deactivate-then-insert in ONE transaction,
     * so process death can never leave the vehicle with zero (or two) active sessions — the
     * invariant the non-transactional clear+insert pair could not guarantee. Returns the id of
     * the session that was active before the swap (the caller removes its orphan geofence).
     * [DET-SOLID-001][MULTI-PARKING-001]
     */
    @Transaction
    suspend fun replaceActiveSession(session: UserParkingEntity): String? {
        val previous = session.vehicleId?.let { getActiveByVehicle(it) }
        if (session.vehicleId != null) clearActiveByVehicle(session.vehicleId) else clearActiveOrphans()
        insert(session)
        return previous?.id
    }

    /** Hygiene probe for the janitor's self-repair sweep: vehicles holding more than one
     *  active session (invariant violation — should always return empty). [DET-SOLID-001] */
    @Query("""
        SELECT * FROM parking_sessions WHERE isActive = 1 AND vehicleId IN (
            SELECT vehicleId FROM parking_sessions
            WHERE isActive = 1 AND vehicleId IS NOT NULL
            GROUP BY vehicleId HAVING COUNT(*) > 1
        ) ORDER BY vehicleId, timestamp DESC
    """)
    suspend fun getActiveDuplicates(): List<UserParkingEntity>

    @Query("DELETE FROM parking_sessions WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)

    /** Unconditional wipe of every row. Used by [LocalSessionCache.wipe] on sign-out. */
    @Query("DELETE FROM parking_sessions")
    suspend fun deleteAll()

    @Query("""
        UPDATE parking_sessions SET
            addressStreet      = :street,
            addressCity        = :city,
            addressRegion      = :region,
            addressCountry     = :country,
            placeInfoName      = :placeInfoName,
            placeInfoCategory  = :placeInfoCategory
        WHERE id = :id
    """)
    suspend fun updateAddressAndPlace(
        id: String,
        street: String?,
        city: String?,
        region: String?,
        country: String?,
        placeInfoName: String?,
        placeInfoCategory: String?,
    )

    /**
     * Manual-edit path — overwrites lat/lon/accuracy/timestamp and **clears
     * address + POI fields** so the re-scheduled enrichment worker fills them
     * with the new location's geocode. Used by `UpdateParkingLocationUseCase`
     * when the user drags the parked-car pin to a new spot via the
     * `HomeMode.AddingParking` edit flow.
     */
    @Query("""
        UPDATE parking_sessions SET
            latitude            = :lat,
            longitude           = :lon,
            accuracy            = :accuracy,
            timestamp           = :timestamp,
            addressStreet       = NULL,
            addressCity         = NULL,
            addressRegion       = NULL,
            addressCountry      = NULL,
            placeInfoName       = NULL,
            placeInfoCategory   = NULL
        WHERE id = :id
    """)
    suspend fun updateLocation(
        id: String,
        lat: Double,
        lon: Double,
        accuracy: Float,
        timestamp: Long,
    )

    @Query("SELECT * FROM parking_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserParkingEntity?
}
