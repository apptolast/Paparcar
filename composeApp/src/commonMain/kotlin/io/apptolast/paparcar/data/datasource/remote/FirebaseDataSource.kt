package io.apptolast.paparcar.data.datasource.remote

import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.data.datasource.remote.dto.ZoneDto
import kotlinx.coroutines.flow.Flow

interface FirebaseDataSource {
    fun observeNearbySpots(latitude: Double, longitude: Double, radiusMeters: Double): Flow<Map<String, SpotDto>>
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

    // ─── Zones ────────────────────────────────────────────────────────────────

    suspend fun getZones(userId: String): List<ZoneDto>
    suspend fun saveZone(userId: String, zone: ZoneDto)
    suspend fun deleteZone(userId: String, zoneId: String)
    suspend fun deleteAllZones(userId: String)
}
