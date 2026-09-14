package com.rndeveloper.paparcar.data.overpass

import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Does this Overpass answer come from a server that knows what it is talking about?
 * [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
 * [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]
 *
 * ⛔ **A 200 can lie, and it lies in two different ways.**
 *
 * 1. *It does not know what it holds.* Measured 09-09-2026: `overpass.osm.ch` answered the Madrid
 *    control query — a place with 60 known stations — with **HTTP 200, in 0,3 s, and an empty
 *    list**. Its `timestamp_osm_base` came back as `116992`, a counter, where a healthy server
 *    publishes an ISO date.
 * 2. *It knows, but it is describing a different month.* Measured 14-09-2026:
 *    `overpass.private.coffee` — the **first fallback**, the one that answers when the reference
 *    instance is down — served a base timestamp of **2026-06-01**, 105 days stale, and the old
 *    shape-only check waved it through. A server that far behind is not slightly out of date; it is
 *    a server nobody is maintaining, and it will break in ways this check cannot see next time.
 *
 * So validity is decided by the server's own statement about ITSELF, never by the number of
 * elements: **an empty list is a legitimate answer** in the middle of nowhere, and treating it as a
 * failure would make deserts retry forever while a broken mirror still passed.
 */
internal object OverpassResponseHealth {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * True when the body carries a base timestamp that is both a real date and a recent one.
     *
     * ⛔ [nowMs] is a parameter rather than a clock read inside, for the same reason the walk's
     * budget is: a health check whose verdict depends on the wall clock is a health check no test
     * can pin down.
     */
    fun isTrustworthy(body: String, nowMs: Long): Boolean {
        val stamp = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["osm3s"]?.jsonObject
                ?.get("timestamp_osm_base")?.jsonPrimitive?.content
        }.getOrNull() ?: return false

        // Parsed, not shape-checked, now that the ANSWER depends on the value and not just on it
        // looking like a date. `116992` fails here exactly as it failed the old check.
        val base = runCatching { Instant.parse(stamp) }.getOrNull() ?: return false
        val ageMs = nowMs - base.toEpochMilliseconds()
        // A clock skewed forward on the phone must not condemn a healthy server, so only staleness
        // counts: a base timestamp in the future is the device's problem, not the mirror's.
        return ageMs <= MAX_BASE_AGE_MS
    }

    /**
     * Seven days.
     *
     * OSM publishes minutely diffs and a healthy mirror runs minutes to hours behind — both live
     * servers measured on 14-09-2026 carried a base timestamp from that same morning. A week is far
     * beyond any honest lag and still forgiving of a mirror reloading its database, while catching
     * the 105-day case with room to spare.
     */
    internal const val MAX_BASE_AGE_MS = 7L * 24 * 60 * 60 * 1_000
}
