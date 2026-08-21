package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow

interface SpotRepository {

    fun observeNearbySpots(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>>

    suspend fun reportSpotReleased(spot: Spot): Result<Unit>

    /**
     * [DET-HANDOFF-NOT-MANUAL-001 §B.3] Withdraws a spot this user published, because the departure
     * it was deduced from turned out never to have happened.
     *
     * Retracting is a STATE, not a delete: the spot is flagged [io.apptolast.paparcar.domain.model.SpotStatus.RETRACTED]
     * and its expiry is pulled in to [io.apptolast.paparcar.domain.model.SpotTtlPolicy.RETRACTION_GRACE_MS]
     * from now. Anyone looking at it right now gets told the report was withdrawn instead of
     * watching the marker disappear; the existing expiry sweep deletes the document afterwards.
     */
    suspend fun retractSpot(spotId: String): Result<Unit>

    /**
     * Sends an accept (still there) or reject (gone) signal for the given spot.
     * The signal atomically increments the counter in Firestore; the real-time
     * listener will propagate the update to the local Room cache.
     */
    suspend fun sendSpotSignal(spotId: String, accepted: Boolean): Result<Unit>

    /** Wipes the local spot cache. Called during account deletion. */
    suspend fun clearCache(): Result<Unit>
}
