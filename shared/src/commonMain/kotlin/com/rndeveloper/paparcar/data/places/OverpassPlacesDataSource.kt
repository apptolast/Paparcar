package com.rndeveloper.paparcar.data.places

import com.rndeveloper.paparcar.data.overpass.OverpassClient
import com.rndeveloper.paparcar.data.overpass.OverpassEndpoints
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.places.NearbyPlacePolicy
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import io.ktor.client.HttpClient

private const val TAG = "OverpassPlaces"

/**
 * Names the place you parked at, against OpenStreetMap. [POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001]
 *
 * ⛔ **One implementation, not one per platform.** This used to be two files —
 * `OverpassPlacesDataSourceImpl` (androidMain, HttpURLConnection) and `IosOverpassPlacesDataSourceImpl`
 * (iosMain, NSURLSession) — and the iOS one's own KDoc warned why that was dangerous: *"a rule could
 * be fixed on Android and stay broken here"*. That is precisely what happened with the mirror
 * failover, which is why both are now gone. [OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001]
 *
 * The failover is NOT reimplemented here: [OverpassClient] walks the mirror list and drops the
 * servers that are down **and the ones that lie** — a broken mirror answers 200 in 0,3 s with an
 * empty list, and only its own `timestamp_osm_base` gives it away.
 * [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
 *
 * ⛔ **A network failure is a `failure`, never `success(null)`.** The two sentences look alike here
 * and are not: `success(null)` means *"we asked and there is no place here"*, and
 * `AddressAndPlaceRepositoryImpl` believes it enough to **seal that cell for 30 days**. The old
 * hand-rolled transports returned `null` for any non-200, so a single 504 taught the cache that your
 * own street has no POI for a month.
 */
class OverpassPlacesDataSource(
    client: HttpClient,
    // Inyectables como en `OsmFuelStationsSource`, y por el mismo motivo: es lo único que permite a
    // un test demostrar que ESTA vía recorre los espejos, y no solo que compila.
    endpoints: List<String> = OverpassEndpoints.ALL,
) : PlacesDataSource {

    private val overpass = OverpassClient(client, endpoints)

    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> =
        overpass
            .fetch(query = OverpassPlaceParser.buildQuery(lat, lon, TIMEOUT_S), tag = TAG)
            .mapCatching { body ->
                val candidates = OverpassPlaceParser.parseCandidates(body)
                NearbyPlacePolicy.pick(candidates, lat, lon).also { picked ->
                    PaparcarLogger.d(TAG, "${candidates.size} candidates near ($lat, $lon) → $picked")
                }
            }
            .onFailure { e -> PaparcarLogger.w(TAG, "Overpass lookup failed for ($lat, $lon)", e) }

    private companion object {
        /**
         * Overpass' own server-side budget for this query, sent inside it.
         *
         * ⛔ Was **8** while car parks, stations, French names and roads all asked for 20 — and
         * [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001] left it standing while it
         * fixed everything around it. It is not a cheap query; it was the odd one out, and being
         * the odd one out COSTS here: `OverpassWalkPatience` caps a server's turn at
         * `serverBudget + NETWORK_GRACE`, so 8 s capped this lane at 13 s per mirror while the
         * derived even share was already handing out 15 s. The one lane whose answer is kept for
         * good was the one that could not use its share.
         *
         * Measured 14-09-2026 (El Puerto): the healthy mirror answered THIS query in **13,2 s** on
         * one of the runs — right through the 13 s cap the 8 was imposing.
         * [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
         */
        const val TIMEOUT_S = 20
    }
}
