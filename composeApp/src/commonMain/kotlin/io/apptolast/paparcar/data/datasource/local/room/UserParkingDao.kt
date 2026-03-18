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

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getActive(): UserParkingEntity?

    @Query("SELECT * FROM parking_sessions ORDER BY timestamp DESC LIMIT 50")
    suspend fun getAll(): List<UserParkingEntity>

    @Query("SELECT * FROM parking_sessions ORDER BY timestamp DESC LIMIT 50")
    fun observeAll(): Flow<List<UserParkingEntity>>

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 ORDER BY timestamp DESC LIMIT 1")
    fun observeActive(): Flow<UserParkingEntity?>

    @Query("UPDATE parking_sessions SET isActive = 0 WHERE isActive = 1")
    suspend fun clearActive()

    @Query("DELETE FROM parking_sessions WHERE isActive = 0 AND timestamp < :olderThanMs")
    suspend fun deleteOldSessions(olderThanMs: Long)

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
}
