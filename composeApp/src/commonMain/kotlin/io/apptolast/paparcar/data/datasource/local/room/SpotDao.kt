package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(spots: List<SpotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(spot: SpotEntity)

    /** Stream cached spots within the given bounding box. Emits on every write. */
    @Query(
        "SELECT * FROM cached_spots " +
        "WHERE latitude BETWEEN :minLat AND :maxLat " +
        "AND longitude BETWEEN :minLon AND :maxLon"
    )
    fun observeNearby(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): Flow<List<SpotEntity>>

    /** One-shot read of cached spots within the given bounding box. Used as offline fallback. */
    @Query(
        "SELECT * FROM cached_spots " +
        "WHERE latitude BETWEEN :minLat AND :maxLat " +
        "AND longitude BETWEEN :minLon AND :maxLon"
    )
    suspend fun getNearby(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<SpotEntity>

    @Query("DELETE FROM cached_spots WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cached_spots WHERE expiresAt != 0 AND expiresAt < :nowMillis")
    suspend fun deleteExpired(nowMillis: Long)

    @Query("DELETE FROM cached_spots")
    suspend fun deleteAll()

    /**
     * Atomically replaces all cached spots within the bounding box with [spots].
     * Ensures that spots removed from Firestore are also removed from the local cache —
     * a plain upsertAll() would leave deleted spots indefinitely in Room.
     */
    @Transaction
    suspend fun replaceForBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        spots: List<SpotEntity>,
    ) {
        deleteInBoundingBox(minLat, maxLat, minLon, maxLon)
        upsertAll(spots)
    }

    @Query(
        "DELETE FROM cached_spots " +
        "WHERE latitude BETWEEN :minLat AND :maxLat " +
        "AND longitude BETWEEN :minLon AND :maxLon"
    )
    suspend fun deleteInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    )
}
