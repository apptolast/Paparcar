package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.util.haversineMeters
import io.ktor.client.HttpClient

/**
 * OpenStreetMap, for every country with no official feed. [FUEL-STATIONS-LIST-001]
 *
 * Answers stations and never prices, so [FuelStationsResult.reportedAt] is null by construction:
 * there is no price here whose hour could be quoted, and inventing one would be the exact dishonesty
 * this ticket exists to avoid.
 *
 * Asks the servers in [OverpassEndpoints.ALL] in order and keeps the first answer that is BOTH
 * successful and trustworthy — a server that is down and a server that lies both just pass their
 * turn. [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
 */
class OsmFuelStationsSource(
    private val client: HttpClient,
    private val endpoints: List<String> = OverpassEndpoints.ALL,
) : FuelStationsSource {

    /** The floor: it covers wherever nothing better does. The registry decides who asks first. */
    override fun covers(countryCode: String): Boolean = true

    private val overpass = OverpassClient(client, endpoints)

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> =
        overpass.fetch(
            query = OsmFuelStationParser
                .buildQuery(query.latitude, query.longitude, query.radiusMeters, TIMEOUT_S),
            tag = TAG,
        ).map { body ->
            // Every server refused or lied → a FAILURE arrives here untouched, never an empty list.
            // "We could not ask" and "there are none" are different sentences and the UI says them
            // differently.
            FuelStationsResult(
                stations = OsmFuelStationParser.parse(body).sortedBy {
                    haversineMeters(query.latitude, query.longitude, it.latitude, it.longitude)
                },
                reportedAt = null,
            )
        }

    private companion object {
        const val TIMEOUT_S = 20
        const val TAG = "OsmFuelStations"
    }
}
