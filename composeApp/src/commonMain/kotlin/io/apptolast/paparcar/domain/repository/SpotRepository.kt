package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow

interface SpotRepository {

    suspend fun getNearbySpots(location: GpsPoint, radiusMeters: Double): Result<List<Spot>>

    fun observeNearbySpots(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>>

    suspend fun reportSpotReleased(spot: Spot): Result<Unit>

    /**
     * Sends an accept (still there) or reject (gone) signal for the given spot.
     * The signal atomically increments the counter in Firestore; the real-time
     * listener will propagate the update to the local Room cache.
     */
    suspend fun sendSpotSignal(spotId: String, accepted: Boolean): Result<Unit>

    /** Wipes the local spot cache. Called during account deletion. */
    suspend fun clearCache(): Result<Unit>
}
