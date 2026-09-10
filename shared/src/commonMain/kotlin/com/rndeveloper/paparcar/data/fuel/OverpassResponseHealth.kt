package com.rndeveloper.paparcar.data.fuel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Does this Overpass answer come from a server that knows what it is talking about?
 * [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
 *
 * ⛔ **A 200 can lie.** Measured on 09-09-2026: `overpass.osm.ch` answered the Madrid control query
 * — a place with 60 known stations — with **HTTP 200, in 0,3 s, and an empty list**. Its database
 * was broken and it said so in the one field nobody was reading: `timestamp_osm_base` came back as
 * `116992`, a counter, where a healthy server publishes an ISO date.
 *
 * So validity is decided by the server's own statement about ITSELF, never by the number of
 * elements: **an empty list is a legitimate answer** in the middle of nowhere, and treating it as a
 * failure would make deserts retry forever while a broken mirror still passed.
 */
internal object OverpassResponseHealth {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * True when the body carries a base timestamp that looks like a real date.
     *
     * Deliberately a shape check and not a parse into an instant: what separates a working server
     * from a broken one here is `2026-07-15T15:22:01Z` versus `116992`, and dragging a date library
     * into a health check would be answering a harder question than the one being asked.
     */
    fun isTrustworthy(body: String): Boolean {
        val stamp = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["osm3s"]?.jsonObject
                ?.get("timestamp_osm_base")?.jsonPrimitive?.content
        }.getOrNull() ?: return false
        return stamp.length >= MIN_ISO_DATE_LENGTH &&
            stamp.getOrNull(4) == '-' &&
            stamp.getOrNull(7) == '-' &&
            stamp.take(4).all { it.isDigit() }
    }

    /** `2026-07-15` — the shortest thing that can still be read as a date. */
    private const val MIN_ISO_DATE_LENGTH = 10
}
