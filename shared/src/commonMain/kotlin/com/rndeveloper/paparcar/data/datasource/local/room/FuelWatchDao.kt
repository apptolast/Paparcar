package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The user's followed forecourts and their price series.
 *  [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
@Dao
interface FuelWatchDao {

    @Query("SELECT * FROM followed_fuel_stations ORDER BY followedAtMs DESC")
    fun observeFollowed(): Flow<List<FollowedFuelStationEntity>>

    @Query("SELECT * FROM followed_fuel_stations ORDER BY followedAtMs DESC")
    suspend fun getFollowed(): List<FollowedFuelStationEntity>

    @Upsert
    suspend fun follow(row: FollowedFuelStationEntity)

    @Query("DELETE FROM followed_fuel_stations WHERE stationKey = :stationKey")
    suspend fun unfollow(stationKey: String)

    @Upsert
    suspend fun upsertSamples(rows: List<FuelPriceHistoryEntity>)

    /**
     * The series of one station for one fuel, oldest first — the shape the verdict reads.
     *
     * ⛔ Ordered by [FuelPriceHistoryEntity.observedDay], never by `reportedAtMs`: the feed's stamp
     * can run backwards between answers (a source re-publishing an older reading), and a series
     * sorted by it would claim a movement that is just two answers arriving out of order.
     */
    @Query(
        "SELECT * FROM fuel_price_history WHERE stationKey = :stationKey AND fuelType = :fuelType " +
            "AND observedDay >= :sinceDay ORDER BY observedDay ASC"
    )
    suspend fun historyFor(stationKey: String, fuelType: String, sinceDay: Long): List<FuelPriceHistoryEntity>

    @Query(
        "SELECT * FROM fuel_price_history WHERE stationKey = :stationKey AND fuelType = :fuelType " +
            "AND observedDay >= :sinceDay ORDER BY observedDay ASC"
    )
    fun observeHistoryFor(stationKey: String, fuelType: String, sinceDay: Long): Flow<List<FuelPriceHistoryEntity>>

    /** Retention, not a TTL: the row is dropped because the window ended, not because it went false. */
    @Query("DELETE FROM fuel_price_history WHERE observedDay < :oldestDayKept")
    suspend fun pruneHistoryBefore(oldestDayKept: Long)

    /**
     * Las lecturas de una gasolinera **que nadie sigue**, pasada su ventana corta.
     * [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
     *
     * ⛔ Esto sustituye a `deleteOrphanedHistory`, que borraba **toda** fila sin follow detrás. Esa
     * consulta era correcta mientras la serie solo existía para las seguidas; desde que el carril
     * free mide tu alrededor, las filas sin follow son la mayoría y aquel barrido borraba justo lo
     * que se acababa de medir. Misma subconsulta, un `observedDay` de más: la referencia no se cierra
     * borrando, se cierra **caducando antes**.
     */
    @Query(
        "DELETE FROM fuel_price_history WHERE observedDay < :oldestDayKept AND stationKey NOT IN " +
            "(SELECT stationKey FROM followed_fuel_stations)"
    )
    suspend fun pruneUnfollowedHistoryBefore(oldestDayKept: Long)

    @Query("DELETE FROM followed_fuel_stations")
    suspend fun deleteAllFollowed()

    @Query("DELETE FROM fuel_price_history")
    suspend fun deleteAllHistory()
}
