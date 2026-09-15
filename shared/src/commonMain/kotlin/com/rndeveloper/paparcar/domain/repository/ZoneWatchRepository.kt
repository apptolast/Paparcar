package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.model.Spot

/**
 * What the zone watch has already announced. [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * The memory the alert never had. Both lanes — the live stream and the background worker — read and
 * write the same rows, which is what stops them announcing the same free space twice or, worse,
 * disagreeing about what has already been said.
 */
interface ZoneWatchRepository {

    /** Spot ids already announced for this zone. */
    suspend fun alertedSpotIds(zoneId: String): Set<String>

    /** True once this device has looked at this zone at all — see the baseline rule. */
    suspend fun hasBaseline(zoneId: String): Boolean

    /**
     * Writes down what was seen, and marks the baseline when this was the first look.
     *
     * One call for both because they are one fact: a look happened, and this is what it found.
     * Split in two, a crash between them leaves a zone marked as looked-at with nothing recorded —
     * which is the state that dumps the whole zone on the next run.
     */
    suspend fun recordLook(zoneId: String, spots: List<Spot>, nowMs: Long, markBaseline: Boolean)

    /** Drops rows whose spot has expired, and rows of zones that no longer exist. */
    suspend fun prune(nowMs: Long)
}
