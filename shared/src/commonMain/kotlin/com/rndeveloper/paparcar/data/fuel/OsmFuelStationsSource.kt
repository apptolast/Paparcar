package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.util.haversineMeters
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess

/**
 * OpenStreetMap, for every country with no official feed. [FUEL-STATIONS-LIST-001]
 *
 * Answers stations and never prices, so [FuelStationsResult.reportedAt] is null by construction:
 * there is no price here whose hour could be quoted, and inventing one would be the exact dishonesty
 * this ticket exists to avoid.
 */
class OsmFuelStationsSource(
    private val client: HttpClient,
) : FuelStationsSource {

    /** The floor: it covers wherever nothing better does. The registry decides who asks first. */
    override fun covers(countryCode: String): Boolean = true

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> =
        runCatching {
            val overpass = OsmFuelStationParser
                .buildQuery(query.latitude, query.longitude, query.radiusMeters, TIMEOUT_S)
            val response = client.submitForm(ENDPOINT, Parameters.build { append("data", overpass) })
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Overpass answered ${response.status.value}")
            }
            FuelStationsResult(
                stations = OsmFuelStationParser.parse(response.bodyAsText()).sortedBy {
                    haversineMeters(query.latitude, query.longitude, it.latitude, it.longitude)
                },
                reportedAt = null,
            )
        }

    private companion object {
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val TIMEOUT_S = 20
    }
}
