package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

/**
 * Scriptable fake for [FirebaseDataSource]. Tests drive emissions through
 * [observeSpotsFlow] and can configure failure modes via [getNearbyThrows]
 * and [observeNearbyThrows].
 */
class FakeFirebaseDataSource : FirebaseDataSource {

    val observeSpotsFlow = MutableSharedFlow<Map<String, SpotDto>>(replay = 0, extraBufferCapacity = 64)

    var getNearbyResponse: Map<String, SpotDto> = emptyMap()
    var getNearbyThrows: Throwable? = null
    var observeNearbyThrows: Throwable? = null

    var reportSpotReleasedCallCount = 0
        private set
    var lastReportedSpot: SpotDto? = null

    var sendSpotSignalCallCount = 0
        private set
    var lastSignal: Pair<String, Boolean>? = null

    override suspend fun getNearbySpots(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): Map<String, SpotDto> {
        getNearbyThrows?.let { throw it }
        return getNearbyResponse
    }

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
}
