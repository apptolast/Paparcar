package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface OsmCoverageDao {

    /**
     * Every live disc for a layer. Read whole and tested geometrically in Kotlin, like the geocoder's
     * candidate cells: the table is bounded by its own eviction, and "does this disc contain that
     * query" is not something SQL should be asked to answer.
     */
    @Query("SELECT * FROM osm_coverage WHERE layer = :layer AND fetchedAt >= :minFetchedAt")
    suspend fun getLive(layer: String, minFetchedAt: Long): List<OsmCoverageEntity>

    @Upsert
    suspend fun upsert(disc: OsmCoverageEntity)

    /**
     * ⛔ Always paired with the eviction of the rows it backed. A disc left standing over pruned rows
     * claims coverage of data that is no longer there, which turns retention into a false
     * "nothing here" — the exact lie this table exists to prevent.
     */
    @Query("DELETE FROM osm_coverage WHERE fetchedAt < :expiryMs")
    suspend fun evictExpired(expiryMs: Long)

    /**
     * The count cap, and it applies to **discs rather than to rows** on purpose. Capping rows
     * directly would delete facilities while the disc that promised them stayed behind, turning
     * retention into a false "nothing here". Discs are the unit that can be dropped honestly: what
     * goes with them is whatever nothing else covers any more.
     */
    @Query(
        "DELETE FROM osm_coverage WHERE layer = :layer AND discKey NOT IN " +
            "(SELECT discKey FROM osm_coverage WHERE layer = :layer ORDER BY fetchedAt DESC LIMIT :keep)"
    )
    suspend fun evictBeyond(layer: String, keep: Int)

    @Query("DELETE FROM osm_coverage")
    suspend fun deleteAll()
}
