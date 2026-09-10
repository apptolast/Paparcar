package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.data.cache.LocationKeyedCache
import com.rndeveloper.paparcar.data.cache.locationCacheKey
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsResult
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource

/**
 * Keeps the last answers around so panning the map, or switching the layer off and on, does not
 * re-ask Overpass. [PARKINGS-ON-THE-MAP-001]
 *
 * The TTL is far longer than the fuel one (15 min) because these two answers age differently: a
 * price changes at the pump, a car park does not move. Half an hour is about how long a browsing
 * session lasts, and nothing in this payload can go stale inside it.
 *
 * ⚠️ When `PARKINGS-LIVE-OCCUPANCY-001` adds "how full is it", **that number does not belong under
 * this TTL** — it is minutes-fresh by definition. The live source brings its own caching; it does
 * not reuse this one.
 */
class CachingPublicParkingsSource(
    private val delegate: PublicParkingsSource,
    ttlMs: Long = DEFAULT_TTL_MS,
    clock: (() -> Long)? = null,
) : PublicParkingsSource {

    private val cache = clock
        ?.let { LocationKeyedCache<PublicParkingsResult>(ttlMs, it) }
        ?: LocationKeyedCache(ttlMs)

    override fun covers(countryCode: String): Boolean = delegate.covers(countryCode)

    override suspend fun parkingsNear(query: PublicParkingsQuery): Result<PublicParkingsResult> {
        val key = with(query) {
            locationCacheKey(latitude, longitude, radiusMeters, countryCode, regionName)
        }
        cache.get(key)?.let { return Result.success(it) }

        // ⛔ Only successes: a failure is the one answer worth retrying.
        return delegate.parkingsNear(query).onSuccess { cache.put(key, it) }
    }

    private companion object {
        const val DEFAULT_TTL_MS = 30L * 60 * 1_000
    }
}
