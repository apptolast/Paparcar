package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.data.datasource.local.room.ZoneAlertedSpotEntity
import com.rndeveloper.paparcar.data.datasource.local.room.ZoneWatchBaselineEntity
import com.rndeveloper.paparcar.data.datasource.local.room.ZoneWatchDao
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.repository.ZoneWatchRepository

/** Room behind [ZoneWatchRepository]. [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001] */
class RoomZoneWatchRepository(
    private val dao: ZoneWatchDao,
) : ZoneWatchRepository {

    override suspend fun alertedSpotIds(zoneId: String): Set<String> =
        dao.alertedSpotIds(zoneId).toSet()

    override suspend fun hasBaseline(zoneId: String): Boolean = dao.baselineFor(zoneId) != null

    override suspend fun recordLook(
        zoneId: String,
        spots: List<Spot>,
        nowMs: Long,
        markBaseline: Boolean,
    ) {
        if (spots.isNotEmpty()) {
            dao.recordAlerted(
                spots.map { spot ->
                    ZoneAlertedSpotEntity(
                        zoneId = zoneId,
                        spotId = spot.id,
                        alertedAtMs = nowMs,
                        spotExpiresAt = spot.expiresAt,
                    )
                },
            )
        }
        // ⛔ The mark goes LAST. A crash between the two leaves a zone with rows and no baseline,
        // which reads as "never looked at" and takes a fresh, silent baseline — one missed alert.
        // The other order leaves it marked with nothing recorded, and that dumps the whole zone.
        // Of the two ways to fail, this one stays quiet.
        if (markBaseline) {
            dao.markBaseline(ZoneWatchBaselineEntity(zoneId = zoneId, baselineAtMs = nowMs))
        }
    }

    override suspend fun prune(nowMs: Long) {
        dao.pruneExpired(nowMs)
        dao.deleteOrphanedAlerts()
        dao.deleteOrphanedBaselines()
    }
}
