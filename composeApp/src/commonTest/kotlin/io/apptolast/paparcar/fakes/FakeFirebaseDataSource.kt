package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.data.datasource.remote.dto.ZoneDto
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
