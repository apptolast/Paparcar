package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** What has already been announced, per zone. [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001] */
@Dao
interface ZoneWatchDao {

    @Query("SELECT spotId FROM zone_alerted_spots WHERE zoneId = :zoneId")
    suspend fun alertedSpotIds(zoneId: String): List<String>

    @Upsert
    suspend fun recordAlerted(rows: List<ZoneAlertedSpotEntity>)

    @Query("SELECT * FROM zone_watch_baseline WHERE zoneId = :zoneId")
    suspend fun baselineFor(zoneId: String): ZoneWatchBaselineEntity?

    @Upsert
    suspend fun markBaseline(row: ZoneWatchBaselineEntity)

    /** Rows whose spot has died: what stopped a repeat was the space still being there. */
    @Query("DELETE FROM zone_alerted_spots WHERE spotExpiresAt != 0 AND spotExpiresAt <= :nowMs")
    suspend fun pruneExpired(nowMs: Long)

    /**
     * Rows of a zone the user deleted, or stopped subscribing to. Swept in one place rather than
     * remembered at every site that touches a zone — the same way the fuel cache closes its own
     * referential gap.
     */
    @Query("DELETE FROM zone_alerted_spots WHERE zoneId NOT IN (SELECT id FROM zones)")
    suspend fun deleteOrphanedAlerts()

    @Query("DELETE FROM zone_watch_baseline WHERE zoneId NOT IN (SELECT id FROM zones)")
    suspend fun deleteOrphanedBaselines()

    @Query("DELETE FROM zone_alerted_spots")
    suspend fun deleteAllAlerted()

    @Query("DELETE FROM zone_watch_baseline")
    suspend fun deleteAllBaselines()
}
