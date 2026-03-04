package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow

interface SpotRepository {

    suspend fun getNearbySpots(location: GpsPoint, radiusMeters: Double): Result<List<Spot>>

    fun observeNearbySpots(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>>

    suspend fun reportSpotReleased(spot: Spot): Result<Unit>
}
