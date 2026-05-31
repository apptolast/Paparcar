package io.apptolast.paparcar.fakes.data.datasource.remote

import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.data.datasource.remote.dto.ZoneDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeFirebaseDataSource : FirebaseDataSource {
    override fun observeNearbySpots(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): Flow<Map<String, SpotDto>> = emptyFlow()

    override suspend fun reportSpotReleased(spotDto: SpotDto) {}

    override suspend fun deleteSpot(spotId: String) {}

    override suspend fun sendSpotSignal(spotId: String, accepted: Boolean) {}

    override suspend fun getZones(userId: String): List<ZoneDto> = emptyList()

    override suspend fun saveZone(userId: String, zone: ZoneDto) {}

    override suspend fun deleteZone(userId: String, zoneId: String) {}

    override suspend fun deleteAllZones(userId: String) {}
}
