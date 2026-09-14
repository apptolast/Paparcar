package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Persistence of the current trip's driven route, one row per admitted fix.
 *  [TRIP-A-LONG-DRIVE-KEEPS-ITS-ORIGIN-001] */
@Dao
interface TripRoutePointDao {

    @Insert
    suspend fun insert(point: TripRoutePointEntity)

    /** The whole recorded route in driving order (insertion order — see [TripRoutePointEntity.seq]). */
    @Query("SELECT * FROM trip_route_points ORDER BY seq ASC")
    suspend fun getAllOrdered(): List<TripRoutePointEntity>

    /** The newest kept point — what the admission rule decimates against. */
    @Query("SELECT * FROM trip_route_points ORDER BY seq DESC LIMIT 1")
    suspend fun getLast(): TripRoutePointEntity?

    @Query("DELETE FROM trip_route_points")
    suspend fun clear()
}
