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
        "AND longitude BETWEEN :minLon AND :maxLon " +
        "ORDER BY reportedAt DESC"
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

    /** Returns the IDs of all cached spots within the given bounding box. */
    @Query(
        "SELECT id FROM cached_spots " +
        "WHERE latitude BETWEEN :minLat AND :maxLat " +
        "AND longitude BETWEEN :minLon AND :maxLon"
    )
    suspend fun getIdsInBbox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<String>

    /**
     * Diff-based atomic update for the bounding-box cache slice.
     *
     * Unlike the old delete-all+insert approach, this only removes spots that
     * Firestore no longer reports, and upserts new or changed ones. Room's
     * InvalidationTracker dispatches ONE notification after the transaction
     * commits, so the UI Flow sees a single consistent state — no empty-list
     * flicker between the delete and insert steps.
     */
    @Transaction
    suspend fun smartReplaceForBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        spots: List<SpotEntity>,
    ) {
        val newIds = spots.map { it.id }.toSet()
        getIdsInBbox(minLat, maxLat, minLon, maxLon)
            .filter { it !in newIds }
            .forEach { delete(it) }
        if (spots.isNotEmpty()) upsertAll(spots)
    }
}
