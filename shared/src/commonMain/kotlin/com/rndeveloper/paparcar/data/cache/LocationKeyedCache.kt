package com.rndeveloper.paparcar.data.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.round
import kotlin.time.Clock

/**
 * A short-lived, in-memory cache keyed by *where you were standing*, shared by every nearby-things
 * source. [PARKINGS-ON-THE-MAP-001]
 *
 * It exists as one class because the non-obvious part is not the map — it is [locationCacheKey], and
 * a second hand-rolled copy of that rounding would drift from the first. See its own doc for why the
 * coordinates are rounded and the radius is not.
 *
 * ⛔ Only successes are stored. A failure is the one answer that must be retried: the network coming
 * back is exactly the event the user is waiting for.
 */
class LocationKeyedCache<T>(
    private val ttlMs: Long,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private data class Entry<T>(val value: T, val storedAtMs: Long)

    private val lock = Mutex()
    private val entries = mutableMapOf<String, Entry<T>>()

    /** The cached value for [key] while it is still fresh, or null. */
    suspend fun get(key: String): T? = lock.withLock {
        entries[key]?.takeIf { clock() - it.storedAtMs <= ttlMs }?.value
    }

    suspend fun put(key: String, value: T) {
        lock.withLock { entries[key] = Entry(value, clock()) }
    }
}

/**
 * The key: coordinates rounded to ~110 m, the radius exact, plus whatever else routed the request.
 *
 * Standing at a window makes GPS wander by tens of metres, and an exact-coordinate key would miss on
 * every single reading — a cache that never hits, which is worse than none because it still costs
 * the memory. The radius stays exact: asking for 5 km and being served a 1 km answer would silently
 * shrink the list.
 */
fun locationCacheKey(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int,
    vararg routing: String?,
): String {
    val lat = round(latitude * KEY_PRECISION) / KEY_PRECISION
    val lon = round(longitude * KEY_PRECISION) / KEY_PRECISION
    return routing.joinToString("|") { it.orEmpty() } + "|$radiusMeters|$lat,$lon"
}

/** Three decimal places of a degree ≈ 110 m. */
private const val KEY_PRECISION = 1_000.0
