package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.repository.ZoneWatchRepository

/**
 * In-memory twin of the zone watch's memory. [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * ⛔ It really REMEMBERS across calls — that is the entire point of the fake. One that forgot would
 * let a test pass over the exact bug this ticket exists to close: a second look re-announcing
 * everything the first one already said.
 */
class FakeZoneWatchRepository : ZoneWatchRepository {

    private val alerted = mutableMapOf<String, MutableSet<String>>()
    private val baselines = mutableSetOf<String>()

    var pruneCount: Int = 0
        private set

    override suspend fun alertedSpotIds(zoneId: String): Set<String> =
        alerted[zoneId].orEmpty().toSet()

    override suspend fun hasBaseline(zoneId: String): Boolean = zoneId in baselines

    override suspend fun recordLook(
        zoneId: String,
        spots: List<Spot>,
        nowMs: Long,
        markBaseline: Boolean,
    ) {
        alerted.getOrPut(zoneId) { mutableSetOf() } += spots.map { it.id }
        if (markBaseline) baselines += zoneId
    }

    override suspend fun hasAnnounced(zoneId: String, spotId: String): Boolean =
        spotId in alerted[zoneId].orEmpty()

    override suspend fun recordAnnounced(
        zoneId: String,
        spotId: String,
        spotExpiresAt: Long,
        nowMs: Long,
    ) {
        alerted.getOrPut(zoneId) { mutableSetOf() } += spotId
    }

    /** Counted, not simulated: what expiry pruning does to real rows is the store's test, not this. */
    override suspend fun prune(nowMs: Long) {
        pruneCount++
    }
}
