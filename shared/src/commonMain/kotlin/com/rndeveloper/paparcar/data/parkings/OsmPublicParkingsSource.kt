package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.data.overpass.OverpassClient
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsResult
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource
import com.rndeveloper.paparcar.domain.util.haversineMeters
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OpenStreetMap, everywhere. [PARKINGS-ON-THE-MAP-001]
 *
 * ⛔ **No registry above it, on purpose.** Fuel has one because each country has a feed that answers
 * the WHOLE question, so routing by country is the entire composition. Car parks do not work that
 * way: a city's live feed carries occupancy for the handful of car parks it operates and nothing
 * else, so `PARKINGS-LIVE-OCCUPANCY-001` has to *enrich* this base rather than replace it. Building
 * a country registry now would be inventing the wrong joint before the second source exists to
 * shape it.
 *
 * The mirror failover is NOT reimplemented here: [OverpassClient] already walks the list and drops
 * the servers that are down **and the ones that lie** — a broken mirror answers 200 in 0,3 s with an
 * empty list, and only its own `timestamp_osm_base` gives it away. This lane is its third consumer,
 * after Spain's OSM fallback and France's name lookup.
 * [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
 *
 * [OverpassClient] lives in the neutral `data/overpass/` since
 * [OVERPASS-CLIENT-IS-NOT-FUEL-001] — this lane was the one that made its old home under
 * `data/fuel/` a lie.
 */
class OsmPublicParkingsSource(
    client: HttpClient,
) : PublicParkingsSource {

    private val overpass = OverpassClient(client)

    /** The floor: it covers wherever nothing better does — which today is everywhere. */
    override fun covers(countryCode: String): Boolean = true

    override suspend fun parkingsNear(query: PublicParkingsQuery): Result<PublicParkingsResult> =
        overpass
            .fetch(
                query = OsmPublicParkingParser
                    .buildQuery(query.latitude, query.longitude, query.radiusMeters, TIMEOUT_S),
                tag = TAG,
            )
            // ⛔ A failure stays a FAILURE, never an empty list: "we could not ask" and "there are
            // none" are different sentences, and the section says them differently.
            .map { body -> withContext(Dispatchers.Default) { body.toResult(query) } }

    /**
     * ⛔ **Off the caller's thread, and that is not tidiness.**
     * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
     *
     * This lane is launched from `viewModelScope`, whose dispatcher is the MAIN one, and a coroutine
     * resumes on the dispatcher its context names — so every line after the `fetch` used to run on
     * the UI thread. What runs here was measured at **681 elements / 238 KB of JSON in Madrid**
     * (`ParkingZoneGrid.ZONE_RADIUS_METERS`), decoded, then one haversine per element, then a sort.
     * That is a frame budget blown several times over, during the exact moment the map is being
     * panned.
     *
     * `Default` rather than `IO`: there is no file and no socket left here, only CPU.
     */
    private fun String.toResult(query: PublicParkingsQuery) = PublicParkingsResult(
        parkings = OsmPublicParkingParser.parse(this).sortedBy {
            haversineMeters(query.latitude, query.longitude, it.latitude, it.longitude)
        },
        // This one came off the wire just now, and says so by saying nothing.
        rememberedAtMs = null,
    )

    /** ⛔ The network remembers nothing. The layers above this one are what hold an answer. */
    override suspend fun remembered(query: PublicParkingsQuery): PublicParkingsResult? = null

    private companion object {
        const val TIMEOUT_S = 20
        const val TAG = "OsmPublicParkings"
    }
}
