package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.round
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

    private data class Entry(val result: FuelStationsResult, val storedAtMs: Long)

    private val lock = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    override fun covers(countryCode: String): Boolean = delegate.covers(countryCode)

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
        val key = query.cacheKey()
        lock.withLock {
            entries[key]?.takeIf { clock() - it.storedAtMs <= ttlMs }
        }?.let { return Result.success(it.result) }

        return delegate.stationsNear(query).onSuccess { result ->
            lock.withLock { entries[key] = Entry(result, clock()) }
        }
    }

    /**
     * Coordinates rounded to ~110 m before they become part of the key.
     *
     * Standing at a window makes GPS wander by tens of metres, and an exact-coordinate key would
     * miss on every single reading — a cache that never hits, which is worse than none because it
     * still costs the memory. The radius stays exact: asking for 5 km and being served a 1 km answer
     * would silently shrink the list.
     */
    private fun FuelStationsQuery.cacheKey(): String {
        val lat = round(latitude * KEY_PRECISION) / KEY_PRECISION
        val lon = round(longitude * KEY_PRECISION) / KEY_PRECISION
        return "$countryCode|${regionName.orEmpty()}|$radiusMeters|$lat,$lon"
    }

    private companion object {
        /**
         * Fifteen minutes. Long enough that a glance costs one request, short enough that a station
         * that changed its price at the pump is not misquoted for the rest of the afternoon.
         */
        const val DEFAULT_TTL_MS = 15L * 60 * 1_000

        /** Three decimal places of a degree ≈ 110 m. */
        const val KEY_PRECISION = 1_000.0
    }
}
