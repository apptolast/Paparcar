package com.rndeveloper.paparcar.data.overpass

/**
 * The Overpass servers, in the order they get asked.
 * [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
 * [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]
 *
 * All of Paparcar's worldwide fuel coverage — every country without a national feed — used to hang
 * on the first one alone, and on 09-09-2026 it went down **twice in one afternoon** (504s, then
 * connect timeouts), taking the feature with it outside Spain.
 *
 * ⛔ **A failover is only as real as its last measurement.** On 14-09-2026 this list was re-measured
 * with the app's own car-park query and **two of its three entries were dead** — the app had been
 * walking a list of one live server and two placebos, taking 30 s to find that out. What replaced
 * them was not guessed either; six candidates were asked the same question and only one answered.
 *
 * A constant and not remote config on purpose: a list that can change under you is a list nobody
 * can debug when a user reports "it showed nothing yesterday".
 */
internal object OverpassEndpoints {

    /** Asked in this order; the first VALID answer wins. See [OverpassResponseHealth]. */
    val ALL: List<String> = listOf(
        // The reference instance. First because it is the freshest and the one OSM itself runs.
        // Measured 14-09-2026: 200 in 4,68 s cold, 1,4 s warm, base timestamp of the same morning.
        "https://overpass-api.de/api/interpreter",
        // Measured 14-09-2026: **200 in 1,35 s** with a base timestamp minutes old and the same 73
        // elements as the reference instance. The only one of five candidates that answered at all.
        "https://overpass.openstreetmap.fr/api/interpreter",
    )

    /**
     * ⛔ **Servers kept out, each for a reason that was measured — so nobody re-adds them by
     * hopefulness.** All checked on 14-09-2026 with the live car-park query.
     *
     * - `overpass.osm.ch` — answers **200 in 0,3 s with an EMPTY list**. Its database is broken and
     *   it says so itself: `timestamp_osm_base` is a counter (`116992`), not a date. A failover that
     *   trusted HTTP codes would pick it for being the fastest and the app would announce "no fuel
     *   stations in Madrid".
     * - `overpass.kumi.systems` — **504 after 54,9 s**, and later no TCP answer at all within 20 s,
     *   `/api/status` included. It was the third entry of the old list.
     * - `overpass.private.coffee` — answered **200 after 52,0 s** with a base timestamp of
     *   **2026-06-01, 105 days stale**, then stopped answering entirely. It was the SECOND entry of
     *   the old list, i.e. the first fallback, and it is what [OverpassResponseHealth] grew a
     *   freshness check for.
     * - `overpass.osm.jp` — **expired TLS certificate** (`SEC_E_CERT_EXPIRED`). A client-side hard
     *   failure, not something patience could fix.
     * - `overpass.monicz.dev` — `/api/status` returns 200 with an empty body and the interpreter
     *   accepts the connection then sends nothing for 60 s. The worst shape for a failover: alive
     *   enough to pass a connect, dead enough to eat a whole turn.
     */
    val EXCLUDED_BY_MEASUREMENT: List<String> = listOf(
        "https://overpass.osm.ch/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass.osm.jp/api/interpreter",
        "https://overpass.monicz.dev/api/interpreter",
    )
}
