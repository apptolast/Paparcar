package com.rndeveloper.paparcar.data.places

import com.rndeveloper.paparcar.data.overpass.OverpassClient
import com.rndeveloper.paparcar.domain.places.RoadNetworkDataSource
import com.rndeveloper.paparcar.domain.places.RoadWay
import io.ktor.client.HttpClient

private const val TAG = "OverpassRoads"

/**
 * The drivable road network inside a bbox — the free, no-API-key way to map-match the trip trail
 * onto streets. [ROUTE-SNAP-001]
 *
 * The failover is NOT reimplemented here: [OverpassClient] walks the mirror list and drops the
 * servers that are down **and the ones that lie**. Before
 * [OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001] this lane spoke HttpURLConnection from androidMain
 * against a single hard-coded mirror, so one 504 meant the line stayed on raw GPS for the whole
 * trip with nothing to fall back to.
 *
 * ⛔ A failure stays a **failure**: [com.rndeveloper.paparcar.presentation.home.HomeTripController]
 * only replaces its cached road graph on a successful, non-empty answer, and an empty list served as
 * success would read as *"there are no streets here"*.
 */
class OverpassRoadNetworkDataSource(
    client: HttpClient,
) : RoadNetworkDataSource {

    private val overpass = OverpassClient(client)

    override suspend fun getRoads(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
    ): Result<List<RoadWay>> =
        overpass
            .fetch(
                query = OverpassRoadParser.buildQuery(minLat, minLon, maxLat, maxLon, TIMEOUT_S),
                tag = TAG,
            )
            .mapCatching { body -> OverpassRoadParser.parse(body) }

    private companion object {
        /** Overpass' own server-side budget for this query, sent inside it. */
        const val TIMEOUT_S = 20
    }
}
