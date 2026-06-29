package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GeocoderCacheDao {

    @Query("SELECT * FROM geocoder_cache WHERE locationKey = :key LIMIT 1")
    suspend fun getByKey(key: String): GeocoderCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: GeocoderCacheEntity)

    @Query("DELETE FROM geocoder_cache WHERE cachedAt < :expiryMs")
    suspend fun evictExpired(expiryMs: Long)

    /** Unconditional wipe of every row. Used by [LocalSessionCache.wipe] on sign-out. */
    @Query("DELETE FROM geocoder_cache")
    suspend fun deleteAll()
}
