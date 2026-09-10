package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface FuelStationCacheDao {

    /** The wide rectangular cut; the real circle is applied in Kotlin afterwards. */
    @Query(
        "SELECT * FROM fuel_stations " +
            "WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon"
    )
    suspend fun getInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<FuelStationCacheEntity>

    @Query("SELECT * FROM fuel_station_fuels WHERE stationKey IN (:stationKeys)")
    suspend fun getFuelsFor(stationKeys: List<String>): List<FuelStationFuelEntity>

    @Upsert
    suspend fun upsertStations(rows: List<FuelStationCacheEntity>)

    @Upsert
    suspend fun upsertFuels(rows: List<FuelStationFuelEntity>)

    /**
     * ⛔ Paired with [upsertFuels] on every write, never skipped: a station whose diesel row
     * disappeared upstream would otherwise keep quoting yesterday's diesel forever. Replacing the
     * set is what makes a fresh answer the truth about that station.
     */
    @Query("DELETE FROM fuel_station_fuels WHERE stationKey IN (:stationKeys)")
    suspend fun deleteFuelsFor(stationKeys: List<String>)

    /** The removal half of reconciling a disc — the fuel rows follow their station. */
    @Query("DELETE FROM fuel_stations WHERE stationKey IN (:stationKeys)")
    suspend fun deleteStations(stationKeys: List<String>)

    @Query("DELETE FROM fuel_stations WHERE cachedAt < :expiryMs")
    suspend fun evictExpiredStations(expiryMs: Long)

    /** Every row, for the orphan sweep — bounded by the disc cap. */
    @Query("SELECT * FROM fuel_stations")
    suspend fun getAllStations(): List<FuelStationCacheEntity>

    /**
     * Fuel rows whose station is gone. Cheaper and more certain than remembering to delete them at
     * every site that removes a station — the referential gap is closed in one place.
     */
    @Query("DELETE FROM fuel_station_fuels WHERE stationKey NOT IN (SELECT stationKey FROM fuel_stations)")
    suspend fun deleteOrphanedFuels()

    @Query("DELETE FROM fuel_stations")
    suspend fun deleteAllStations()

    @Query("DELETE FROM fuel_station_fuels")
    suspend fun deleteAllFuels()
}
