package io.apptolast.paparcar.data.datasource.remote

import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.data.datasource.remote.dto.ZoneDto
import kotlinx.coroutines.flow.Flow

interface FirebaseDataSource {
    fun observeNearbySpots(latitude: Double, longitude: Double, radiusMeters: Double): Flow<Map<String, SpotDto>>
    suspend fun reportSpotReleased(spotDto: SpotDto)
    /** Atomically increments the accept or reject counter for the given spot. */
    suspend fun sendSpotSignal(spotId: String, accepted: Boolean)

    // ─── Zones ────────────────────────────────────────────────────────────────

    suspend fun getZones(userId: String): List<ZoneDto>
    suspend fun saveZone(userId: String, zone: ZoneDto)
    suspend fun deleteZone(userId: String, zoneId: String)
    suspend fun deleteAllZones(userId: String)
}
