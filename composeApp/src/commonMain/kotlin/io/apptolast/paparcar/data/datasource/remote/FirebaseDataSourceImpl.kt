package io.apptolast.paparcar.data.datasource.remote

import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FirebaseDataSourceImpl : FirebaseDataSource {
    override suspend fun getNearbySpots(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): Map<String, SpotDto> {
        return emptyMap()
    }

    override fun observeNearbySpots(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): Flow<Map<String, SpotDto>> {
        return flowOf(emptyMap())
    }

    override suspend fun reportSpotReleased(spotDto: SpotDto) {
        // No-op
    }
}