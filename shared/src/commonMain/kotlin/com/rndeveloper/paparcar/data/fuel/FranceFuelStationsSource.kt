package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.data.overpass.OverpassClient
import com.rndeveloper.paparcar.data.overpass.OverpassEndpoints
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * France, straight from the ministry of the economy.
 * [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
 *
 * The best feed of the whole survey and the reason France goes first: no key, 9.805 stations, a
 * **native radius filter**, prices already numeric, and each price stamped with its own hour — the
 * property that made [com.rndeveloper.paparcar.domain.fuel.StationPrice] exist.
 *
 * Unlike Spain, nothing is downloaded whole and nothing is filtered here: the API takes the radius,
 * the ordering and the field list, so this class asks a question and hands the answer to a parser.
 *
 * The one thing it adds is the name, which the feed does not have — see [OsmStationNames], joined
 * on the official station id and never on distance.
 */
class FranceFuelStationsSource(
    private val client: HttpClient,
    endpoints: List<String> = OverpassEndpoints.ALL,
) : FuelStationsSource {

    private val names = OsmStationNames(OverpassClient(client, endpoints))

    override fun covers(countryCode: String): Boolean = countryCode.uppercase() == COUNTRY

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> =
        runCatching {
            val stations = FranceStationParser.parse(fetch(query))
            FuelStationsResult(
                stations = names.named(
                    stations = stations,
                    latitude = query.latitude,
                    longitude = query.longitude,
                    radiusMeters = query.radiusMeters,
                ),
                // ⛔ Null, and that is the honest answer: France stamps every PRICE and never the
                // response. Measured on 100 Paris stations, the ages inside one response run from
                // 0,7 h to thirteen months, so any single hour quoted for the list as a whole would
                // be a claim no station made. The hour rides with the price instead.
                reportedAt = null,
            )
        }

    private suspend fun fetch(query: FuelStationsQuery): String {
        val response = client.get(ENDPOINT) {
            url {
                parameters.append("where", whereWithinRadius(query))
                parameters.append("select", FranceStationParser.selectFields)
                // Nearest first, decided by the server — the only order this list can have before
                // the user has said anything about price.
                parameters.append("order_by", orderByDistance(query))
                // 100 is the API's ceiling (101 answers 400). With the ordering above, that is the
                // hundred nearest, which is what a 5 km list wants anyway.
                parameters.append("limit", MAX_RESULTS.toString())
            }
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("French feed answered ${response.status.value}")
        }
        return response.bodyAsText()
    }

    /**
     * ⛔ The radius **must carry its unit**. Measured: `5000` returns `total_count: 0` — not an
     * error, not a 400, just an empty list that reads exactly like "no stations near you". The
     * same class of lie as a mirror answering 200 with an empty base.
     * [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
     */
    private fun whereWithinRadius(query: FuelStationsQuery): String =
        "distance(geom, ${point(query)}, ${query.radiusMeters}m)"

    private fun orderByDistance(query: FuelStationsQuery): String = "distance(geom, ${point(query)})"

    /** ⚠️ `POINT` is longitude first — the opposite order to every other coordinate in the app. */
    private fun point(query: FuelStationsQuery): String =
        "geom'POINT(${query.longitude} ${query.latitude})'"

    private companion object {
        const val COUNTRY = "FR"
        const val MAX_RESULTS = 100
        const val ENDPOINT = "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/" +
            "prix-des-carburants-en-france-flux-instantane-v2/records"
    }
}
