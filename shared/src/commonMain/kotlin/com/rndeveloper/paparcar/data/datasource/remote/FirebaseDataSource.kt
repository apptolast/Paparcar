package com.rndeveloper.paparcar.data.datasource.remote

import com.rndeveloper.paparcar.data.datasource.remote.dto.SpotDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ZoneDto
import kotlinx.coroutines.flow.Flow

interface FirebaseDataSource {
    fun observeNearbySpots(latitude: Double, longitude: Double, radiusMeters: Double): Flow<Map<String, SpotDto>>

    /**
     * ONE read of the same ground, for callers that cannot hold a listener open.
     * [ZONE-A-LOOK-THAT-READS-THE-CACHE-IS-NOT-A-LOOK-001]
     *
     * ⛔ Not a convenience twin of [observeNearbySpots]: a background worker asking that one and
     * taking `.first()` gets the LOCAL CACHE, because the remote half of that flow never emits — it
     * writes into Room and Room re-emits. A `get()` is the only shape that answers "what is there
     * right now" without a live collector.
     *
     * Returns the raw documents the geohash ranges matched — deliberately unclipped, so the caller
     * can report how many were actually READ. Firestore bills per document, and that number is the
     * only unknown in the cost of the zone watch.
     */
    suspend fun getNearbySpotsOnce(latitude: Double, longitude: Double, radiusMeters: Double): Map<String, SpotDto>
    suspend fun reportSpotReleased(spotDto: SpotDto)
    suspend fun deleteSpot(spotId: String)

    /**
     * [DET-HANDOFF-NOT-MANUAL-001 §B.3] Withdraws a published spot: flags it RETRACTED and brings
     * its expiry forward to [expiresAt]. A two-field update, not a delete — the document has to
     * survive long enough to tell anyone looking at it why the spot is gone. Owner-only by the
     * Firestore rules, which is exactly who calls this.
     */
    suspend fun retractSpot(spotId: String, expiresAt: Long)
    /** Atomically increments the accept or reject counter for the given spot. */
    suspend fun sendSpotSignal(spotId: String, accepted: Boolean)

    /**
     * Restarts a spot's age after an eyewitness confirmed it is still free, by moving its
     * `reportedAt` to [reportedAt]. [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001]
     *
     * Writes that ONE field: `expiresAt` stays where it was, so a confirmed spot looks new without
     * outliving the lifetime it was published with.
     */
    suspend fun refreshSpot(spotId: String, reportedAt: Long)

    // ─── Zones ────────────────────────────────────────────────────────────────

    suspend fun getZones(userId: String): List<ZoneDto>
    suspend fun saveZone(userId: String, zone: ZoneDto)
    suspend fun deleteZone(userId: String, zoneId: String)
    suspend fun deleteAllZones(userId: String)
}
