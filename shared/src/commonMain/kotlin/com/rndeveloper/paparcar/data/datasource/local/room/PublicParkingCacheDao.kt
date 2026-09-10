package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PublicParkingCacheDao {

    /**
     * Every cached facility inside a bounding box — the wide candidate set, narrowed to the real
     * circle by haversine in Kotlin. Same shape the geocoder cache already uses for its nearest-cell
     * lookup: SQL does the cheap rectangular cut, Kotlin does the honest one.
     */
    @Query(
        "SELECT * FROM osm_public_parkings " +
            "WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon"
    )
    suspend fun getInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<PublicParkingCacheEntity>

    @Upsert
    suspend fun upsertAll(rows: List<PublicParkingCacheEntity>)

    /**
     * The removal half of reconciling a disc: ids that were inside it and did not come back in the
     * fresh answer. Without this a bounding-box read is the union of everything ever written and no
     * facility can ever disappear.
     */
    @Query("DELETE FROM osm_public_parkings WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM osm_public_parkings WHERE cachedAt < :expiryMs")
    suspend fun evictExpired(expiryMs: Long)

    /**
     * Every row, for the orphan sweep — bounded by the disc cap, since a row only ever enters here
     * as part of some disc's answer.
     */
    @Query("SELECT * FROM osm_public_parkings")
    suspend fun getAll(): List<PublicParkingCacheEntity>

    @Query("DELETE FROM osm_public_parkings")
    suspend fun deleteAll()
}
