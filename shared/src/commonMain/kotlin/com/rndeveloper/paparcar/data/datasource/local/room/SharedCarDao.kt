package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedCarDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(car: SharedCarEntity)

    @Query("SELECT * FROM shared_cars WHERE carId = :carId LIMIT 1")
    fun observeById(carId: String): Flow<SharedCarEntity?>

    @Query("SELECT * FROM shared_cars")
    fun observeAll(): Flow<List<SharedCarEntity>>

    @Query("SELECT * FROM shared_cars WHERE carId = :carId LIMIT 1")
    suspend fun getById(carId: String): SharedCarEntity?

    @Query("DELETE FROM shared_cars WHERE carId = :carId")
    suspend fun deleteById(carId: String)

    /** Account deletion and sign-out: the cache of a group is not a memory to keep after leaving. */
    @Query("DELETE FROM shared_cars")
    suspend fun deleteAll()
}
