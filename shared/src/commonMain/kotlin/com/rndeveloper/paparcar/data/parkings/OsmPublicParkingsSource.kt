package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.data.fuel.OverpassClient
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource
import com.rndeveloper.paparcar.domain.util.haversineMeters
import io.ktor.client.HttpClient

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
 * ⚠️ [OverpassClient] still lives in `data/fuel/` because that is where it was born. Now that a
 * non-fuel lane uses it, its home should become a neutral `data/overpass/` — noted rather than done
 * mid-merge, because moving a file another session is actively building on buys a conflict and no
 * behaviour.
 */
class OsmPublicParkingsSource(
    client: HttpClient,
) : PublicParkingsSource {

    private val overpass = OverpassClient(client)

    /** The floor: it covers wherever nothing better does — which today is everywhere. */
    override fun covers(countryCode: String): Boolean = true

    override suspend fun parkingsNear(query: PublicParkingsQuery): Result<List<PublicParking>> =
        overpass
            .fetch(
                query = OsmPublicParkingParser
                    .buildQuery(query.latitude, query.longitude, query.radiusMeters, TIMEOUT_S),
                tag = TAG,
            )
            // ⛔ A failure stays a FAILURE, never an empty list: "we could not ask" and "there are
            // none" are different sentences, and the section says them differently.
            .map { body ->
                OsmPublicParkingParser.parse(body).sortedBy {
                    haversineMeters(query.latitude, query.longitude, it.latitude, it.longitude)
                }
            }

    private companion object {
        const val TIMEOUT_S = 20
        const val TAG = "OsmPublicParkings"
    }
}
