package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserParkingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: UserParkingSessionEntity)

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getActive(): UserParkingSessionEntity?

    @Query("SELECT * FROM parking_sessions ORDER BY timestamp DESC")
    suspend fun getAll(): List<UserParkingSessionEntity>

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 ORDER BY timestamp DESC LIMIT 1")
    fun observeActive(): Flow<UserParkingSessionEntity?>

    @Query("UPDATE parking_sessions SET isActive = 0 WHERE isActive = 1")
    suspend fun clearActive()
}
