package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.data.cache.LocationKeyedCache
import com.rndeveloper.paparcar.data.cache.locationCacheKey
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import kotlin.time.Clock

/**
 * Keeps the last answers around for a few minutes so opening and closing the screen does not
 * re-download a province. [FUEL-STATIONS-LIST-001]
 *
 * **In memory, not in Room, and that is a decision with a reason.** Persisting would mean a schema
 * bump and a `Migration`, and we do not yet know how long a fuel price stays useful — Italy's own
 * feed says less than it looks: 69% of its prices are two to seven days old and a tail reaches back
 * to 2013. Deciding a disk TTL before measuring one would bake a guess into the database. This
 * covers the real case (open, glance, close, open again) and costs nothing to change.
 *
 * ⛔ Only successes are cached. A failure is the one answer that must be retried, because the
 * network coming back is exactly the event the user is waiting for.
 */
class CachingFuelStationsSource(
    private val delegate: FuelStationsSource,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : FuelStationsSource {

    private val cache = LocationKeyedCache<FuelStationsResult>(ttlMs, clock)

    override fun covers(countryCode: String): Boolean = delegate.covers(countryCode)

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
        val key = with(query) {
            locationCacheKey(latitude, longitude, radiusMeters, countryCode, regionName)
        }
        cache.get(key)?.let { return Result.success(it) }

        return delegate.stationsNear(query).onSuccess { cache.put(key, it) }
    }

    private companion object {
        /**
         * Fifteen minutes. Long enough that a glance costs one request, short enough that a station
         * that changed its price at the pump is not misquoted for the rest of the afternoon.
         */
        const val DEFAULT_TTL_MS = 15L * 60 * 1_000
    }
}
