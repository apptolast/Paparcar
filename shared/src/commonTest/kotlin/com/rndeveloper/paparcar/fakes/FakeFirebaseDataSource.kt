package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.data.datasource.remote.FirebaseDataSource
import com.rndeveloper.paparcar.data.datasource.remote.dto.SpotDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ZoneDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

/**
 * Scriptable fake for [FirebaseDataSource]. Tests drive emissions through
 * [observeSpotsFlow] and can configure a failure mode via [observeNearbyThrows].
 */
class FakeFirebaseDataSource : FirebaseDataSource {

    val observeSpotsFlow = MutableSharedFlow<Map<String, SpotDto>>(replay = 0, extraBufferCapacity = 64)

    var observeNearbyThrows: Throwable? = null

    var reportSpotReleasedCallCount = 0
        private set
    var lastReportedSpot: SpotDto? = null

    var sendSpotSignalCallCount = 0
        private set
    var lastSignal: Pair<String, Boolean>? = null

    override fun observeNearbySpots(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): Flow<Map<String, SpotDto>> = flow {
        observeNearbyThrows?.let { throw it }
        observeSpotsFlow.collect { emit(it) }
    }

    override suspend fun reportSpotReleased(spotDto: SpotDto) {
        reportSpotReleasedCallCount++
        lastReportedSpot = spotDto
    }

    override suspend fun deleteSpot(spotId: String) {
        // No-op for now
    }

    /** [DET-HANDOFF-NOT-MANUAL-001 §B.3] (spotId, new expiry) of every retraction, in order. */
    val retractedSpots = mutableListOf<Pair<String, Long>>()

    override suspend fun retractSpot(spotId: String, expiresAt: Long) {
        retractedSpots += spotId to expiresAt
    }

    /** [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] `spotId to reportedAt` of each refresh. */
    val refreshedSpots = mutableListOf<Pair<String, Long>>()

    override suspend fun refreshSpot(spotId: String, reportedAt: Long) {
        refreshedSpots += spotId to reportedAt
    }

    override suspend fun sendSpotSignal(spotId: String, accepted: Boolean) {
        sendSpotSignalCallCount++
        lastSignal = spotId to accepted
    }

    // ─── Zones ────────────────────────────────────────────────────────────────

    var zonesToReturn: List<ZoneDto> = emptyList()

    override suspend fun getZones(userId: String): List<ZoneDto> = zonesToReturn

    override suspend fun saveZone(userId: String, zone: ZoneDto) {
        // No-op for now
    }

    override suspend fun deleteZone(userId: String, zoneId: String) {
        // No-op for now
    }

    override suspend fun deleteAllZones(userId: String) {
        // No-op for now
    }
}
