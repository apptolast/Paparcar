package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.repository.NearbySpotsRead
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class FakeSpotRepository : SpotRepository {

    /** What the SERVER holds. `spots = ...` sets this, so existing tests keep their meaning. */
    private val _spots = MutableStateFlow<List<Spot>>(emptyList())

    /** What ROOM holds — what the live flow emits. Set apart to reproduce a stale cache. */
    private val _cachedSpots = MutableStateFlow<List<Spot>>(emptyList())

    /**
     * Sets BOTH halves — the common case, where the cache is up to date.
     * Use [serverOnlySpots] to leave the cache behind, which is what a closed app looks like.
     */
    var spots: List<Spot>
        get() = _spots.value
        set(value) {
            _spots.value = value
            _cachedSpots.value = value
        }

    /** Published, but NOT in this device's cache: exactly the state the zone watch exists for. */
    var serverOnlySpots: List<Spot>
        get() = _spots.value
        set(value) { _spots.value = value }

    /** What the stale cache holds on its own. */
    var cachedSpots: List<Spot>
        get() = _cachedSpots.value
        set(value) { _cachedSpots.value = value }

    /** When non-null, [observeNearbySpots] throws this error instead of emitting spots. */
    var observeError: Throwable? = null

    var reportCallCount = 0
        private set
    var reportResult: Result<Unit> = Result.success(Unit)

    /**
     * ⛔ **The CACHE half.** [ZONE-A-LOOK-THAT-READS-THE-CACHE-IS-NOT-A-LOOK-001] — production's
     * flow emits only from Room, so this fake emits only [cachedSpots]. It used to serve the same
     * list as the server, which is why twelve green tests said nothing about a worker reading a
     * frozen cache. A fake that cannot reproduce the split cannot fail on it.
     */
    override fun observeNearbySpots(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>> {
        val error = observeError
        return if (error != null) flow { throw error } else _cachedSpots
    }

    /** What the SERVER would answer — deliberately a different list from [cachedSpots]. */
    override suspend fun getNearbySpotsOnce(
        location: GpsPoint,
        radiusMeters: Double,
    ): Result<NearbySpotsRead> {
        onceCallCount++
        lastOnceRadiusMeters = radiusMeters
        observeError?.let { return Result.failure(it) }
        val answered = _spots.value
        return Result.success(NearbySpotsRead(spots = answered, documentsRead = answered.size + overfetchDocs))
    }

    /** Calls to the one-shot read — the witness that a caller asked the SERVER, not the cache. */
    var onceCallCount = 0
        private set

    /** The radius the last one-shot was asked for — how the alert-radius floor is proved. */
    var lastOnceRadiusMeters: Double? = null
        private set

    /** Documents the geohash ranges return beyond the circle; feeds the cost count. */
    var overfetchDocs = 0

    override suspend fun reportSpotReleased(spot: Spot): Result<Unit> {
        reportCallCount++
        return reportResult
    }

    /** [DET-HANDOFF-NOT-MANUAL-001 §B.3] Spot ids withdrawn through [retractSpot], in order. */
    val retractedSpotIds = mutableListOf<String>()
    var retractResult: Result<Unit> = Result.success(Unit)

    override suspend fun retractSpot(spotId: String): Result<Unit> {
        retractedSpotIds += spotId
        return retractResult
    }

    var signalCallCount = 0
        private set
    var lastSignalAccepted: Boolean? = null
        private set
    var signalResult: Result<Unit> = Result.success(Unit)

    override suspend fun sendSpotSignal(spotId: String, accepted: Boolean): Result<Unit> {
        signalCallCount++
        lastSignalAccepted = accepted
        return signalResult
    }

    /** [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] Spot ids rejuvenated through [refreshSpot]. */
    val refreshedSpotIds = mutableListOf<String>()
    var refreshResult: Result<Unit> = Result.success(Unit)

    override suspend fun refreshSpot(spotId: String): Result<Unit> {
        refreshedSpotIds += spotId
        return refreshResult
    }

    var clearCacheCallCount = 0
        private set
    var clearCacheResult: Result<Unit> = Result.success(Unit)

    override suspend fun clearCache(): Result<Unit> {
        clearCacheCallCount++
        if (clearCacheResult.isSuccess) _spots.value = emptyList()
        return clearCacheResult
    }
}
