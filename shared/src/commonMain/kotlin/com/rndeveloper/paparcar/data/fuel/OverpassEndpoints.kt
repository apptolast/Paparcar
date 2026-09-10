package com.rndeveloper.paparcar.data.fuel

/**
 * The Overpass servers, in the order they get asked.
 * [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
 *
 * All of Paparcar's worldwide fuel coverage — every country without a national feed — used to hang
 * on the first one alone, and on 09-09-2026 it went down **twice in one afternoon** (504s, then
 * connect timeouts), taking the feature with it outside Spain.
 *
 * A constant and not remote config on purpose: a list that can change under you is a list nobody
 * can debug when a user reports "it showed nothing yesterday".
 */
internal object OverpassEndpoints {

    /** Asked in this order; the first VALID answer wins. See [OverpassResponseHealth]. */
    val ALL: List<String> = listOf(
        // The reference instance. First because it is the freshest and the one OSM itself runs.
        "https://overpass-api.de/api/interpreter",
        // Measured healthy on 09-09-2026 (60/60 on the Madrid control query), though its base ran
        // ~2 months behind. Fine for forecourts — they do not move — and it carries no prices to
        // go stale.
        "https://overpass.private.coffee/api/interpreter",
        // Community instance, kept as a last resort.
        "https://overpass.kumi.systems/api/interpreter",
    )

    /**
     * ⛔ **`overpass.osm.ch` is deliberately absent.** It answers **200 in 0,3 s with an EMPTY
     * list** — its database is broken, and it says so itself: its `timestamp_osm_base` is a
     * counter (`116992`), not a date. A failover that trusted HTTP codes would pick it for being
     * the fastest and the app would announce "no fuel stations in Madrid". Excluded by measurement,
     * and [OverpassResponseHealth] exists so that the next server to break this way is caught even
     * if nobody notices in time to edit this list.
     */
    const val EXCLUDED_BROKEN_MIRROR = "https://overpass.osm.ch/api/interpreter"
}
