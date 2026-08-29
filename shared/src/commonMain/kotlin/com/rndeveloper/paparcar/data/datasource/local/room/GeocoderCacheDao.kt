package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GeocoderCacheDao {

    @Query("SELECT * FROM geocoder_cache WHERE locationKey = :key LIMIT 1")
    suspend fun getByKey(key: String): GeocoderCacheEntity?

    /**
     * Every live cell that can name a street — the candidate set for the offline
     * nearest-cell lookup. The set is small (one row per ~11 m cell the user has
     * actually geocoded, 30-day TTL), so the distance pick happens in Kotlin.
     * [GEO-CACHE-ANSWERS-NEARBY-001]
     */
    @Query("SELECT * FROM geocoder_cache WHERE addressStreet IS NOT NULL AND cachedAt >= :minCachedAt")
    suspend fun getStreetCells(minCachedAt: Long): List<GeocoderCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: GeocoderCacheEntity)

    @Query("DELETE FROM geocoder_cache WHERE cachedAt < :expiryMs")
    suspend fun evictExpired(expiryMs: Long)

    /** Unconditional wipe of every row. Used by [LocalSessionCache.wipe] on sign-out. */
    @Query("DELETE FROM geocoder_cache")
    suspend fun deleteAll()
}
