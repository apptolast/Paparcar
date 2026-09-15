package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow

/**
 * What one look found, and **what it cost**. [ZONE-A-LOOK-THAT-READS-THE-CACHE-IS-NOT-A-LOOK-001]
 *
 * [documentsRead] is not decoration: Firestore bills per DOCUMENT, the geohash ranges overfetch
 * relative to the circle (which is why the circle is applied afterwards, in Kotlin), and that number
 * is the only unknown in the cost of running the zone watch. Carried out of the data layer because
 * it is the only place that knows it.
 */
data class NearbySpotsRead(
    val spots: List<Spot>,
    val documentsRead: Int,
)

interface SpotRepository {

    fun observeNearbySpots(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>>

    /**
     * ONE look at what is published around [location], asked of the server.
     * [ZONE-A-LOOK-THAT-READS-THE-CACHE-IS-NOT-A-LOOK-001]
     *
     * ⛔ **Not `observeNearbySpots(...).first()`.** That flow's only emitter is its ROOM branch — the
     * Firestore half writes into Room and lets Room re-emit — so the first emission is always the
     * local cache, and a background caller would be producing a verdict about the world by reading
     * its own memory. With the app closed that cache is frozen at whenever it was last collected,
     * which is exactly the moment the question stops being worth asking.
     *
     * Refreshes the cache on its way through: a look already paid for should not be thrown away.
     */
    suspend fun getNearbySpotsOnce(location: GpsPoint, radiusMeters: Double): Result<NearbySpotsRead>

    suspend fun reportSpotReleased(spot: Spot): Result<Unit>

    /**
     * [DET-HANDOFF-NOT-MANUAL-001 §B.3] Withdraws a spot this user published, because the departure
     * it was deduced from turned out never to have happened.
     *
     * Retracting is a STATE, not a delete: the spot is flagged [com.rndeveloper.paparcar.domain.model.SpotStatus.RETRACTED]
     * and its expiry is pulled in to [com.rndeveloper.paparcar.domain.model.SpotTtlPolicy.RETRACTION_GRACE_MS]
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

    /** Restarts the spot's age — an eyewitness confirmed it is still free. Does not extend its
     *  expiry. [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] */
    suspend fun refreshSpot(spotId: String): Result<Unit>

    /** Wipes the local spot cache. Called during account deletion. */
    suspend fun clearCache(): Result<Unit>
}
