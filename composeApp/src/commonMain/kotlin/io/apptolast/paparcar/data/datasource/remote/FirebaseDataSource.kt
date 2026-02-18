package io.apptolast.paparcar.data.datasource.remote

import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import kotlinx.coroutines.flow.Flow

interface FirebaseDataSource {
    suspend fun getNearbySpots(latitude: Double, longitude: Double, radiusMeters: Double): Map<String, SpotDto>
    fun observeNearbySpots(latitude: Double, longitude: Double, radiusMeters: Double): Flow<Map<String, SpotDto>>
    suspend fun reportSpotReleased(spotDto: SpotDto)
}
