package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(zone: ZoneEntity)

    @Query("SELECT * FROM zones WHERE userId = :userId ORDER BY createdAt ASC")
    fun observeByUser(userId: String): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ZoneEntity?

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun deleteById(id: String)

    /** REPLACE-conflict bulk insert used by [ZoneRepository.syncFromRemote]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(zones: List<ZoneEntity>)

    @Query("DELETE FROM zones WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)
}
