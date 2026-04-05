package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vehicle: VehicleEntity)

    @Query("SELECT * FROM vehicles WHERE userId = :userId ORDER BY isDefault DESC")
    fun observeByUser(userId: String): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE userId = :userId AND isDefault = 1 LIMIT 1")
    fun observeDefault(userId: String): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE userId = :userId ORDER BY isDefault DESC")
    suspend fun getByUser(userId: String): List<VehicleEntity>

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE vehicles SET isDefault = 0 WHERE userId = :userId")
    suspend fun clearDefault(userId: String)

    @Query("UPDATE vehicles SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: String)
}
