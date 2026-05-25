package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserParkingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: UserParkingEntity)

    /** REPLACE-conflict bulk insert used by [UserParkingRepository.syncParkingHistoryFromRemote]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<UserParkingEntity>)

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 AND geofenceId = :geofenceId LIMIT 1")
    suspend fun getActiveByGeofence(geofenceId: String): UserParkingEntity?

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 AND vehicleId = :vehicleId LIMIT 1")
    suspend fun getActiveByVehicle(vehicleId: String): UserParkingEntity?

    @Query("SELECT * FROM parking_sessions ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParkingEntity>

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

    @Query("DELETE FROM parking_sessions WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)

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
    suspend fun updateLocationInfo(
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
