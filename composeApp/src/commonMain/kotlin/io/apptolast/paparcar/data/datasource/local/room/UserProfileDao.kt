package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE userId = :userId LIMIT 1")
    suspend fun getProfile(userId: String): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<UserProfileEntity?>

    @Query("UPDATE user_profile SET defaultVehicleId = :vehicleId WHERE userId = :userId")
    suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?)

    @Query("DELETE FROM user_profile WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)
}
