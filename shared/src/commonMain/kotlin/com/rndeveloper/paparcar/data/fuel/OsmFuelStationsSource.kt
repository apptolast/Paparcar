package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
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

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
        val overpass = OsmFuelStationParser
            .buildQuery(query.latitude, query.longitude, query.radiusMeters, TIMEOUT_S)
        var lastFailure: Throwable? = null

        for (endpoint in endpoints) {
            val body = runCatching { fetch(endpoint, overpass) }
                .onFailure { error ->
                    // Down, timed out, or answered with a broken database: log WHICH one and why,
                    // so a diagnosis can say where the answer finally came from.
                    PaparcarLogger.w(TAG, "overpass ${endpoint.host()} passed: ${error.message}")
                    lastFailure = error
                }
                .getOrNull() ?: continue

            return Result.success(
                FuelStationsResult(
                    stations = OsmFuelStationParser.parse(body).sortedBy {
                        haversineMeters(query.latitude, query.longitude, it.latitude, it.longitude)
                    },
                    reportedAt = null,
                ),
            )
        }
        // Every server refused or lied: a FAILURE, never an empty list. "We could not ask" and
        // "there are none" are different sentences and the UI says them differently.
        return Result.failure(
            lastFailure ?: IllegalStateException("No Overpass endpoint configured"),
        )
    }

    private suspend fun fetch(endpoint: String, overpassQuery: String): String {
        val response = client.submitForm(endpoint, Parameters.build { append("data", overpassQuery) })
        if (!response.status.isSuccess()) {
            throw IllegalStateException("answered ${response.status.value}")
        }
        val body = response.bodyAsText()
        // ⛔ The 200 is not enough: a mirror with a broken database answers 200 with an empty list.
        if (!OverpassResponseHealth.isTrustworthy(body)) {
            throw IllegalStateException("answered 200 with no usable base timestamp")
        }
        return body
    }

    /** Just for the log line — the whole URL would drown the message it is trying to make. */
    private fun String.host(): String = substringAfter("://").substringBefore("/")

    private companion object {
        const val TIMEOUT_S = 20
        const val TAG = "OsmFuelStations"
    }
}
