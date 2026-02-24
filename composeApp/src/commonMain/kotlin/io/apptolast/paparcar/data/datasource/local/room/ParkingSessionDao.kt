package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ParkingSessionEntity)

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): ParkingSessionEntity?

    @Query("SELECT * FROM parking_sessions ORDER BY timestamp DESC")
    suspend fun getAll(): List<ParkingSessionEntity>

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<ParkingSessionEntity?>

    @Query("UPDATE parking_sessions SET isActive = 0 WHERE isActive = 1")
    suspend fun clearActive()
}
