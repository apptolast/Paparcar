package com.rndeveloper.paparcar.data.overpass

import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess

/**
 * Asks Overpass, walking down the mirror list until one answers **and can be believed**.
 * [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
 *
 * Extracted from `OsmFuelStationsSource` when France arrived needing the very same failover for a
 * different question — station names by official id. Two copies of a retry loop are two places for
 * a mirror to be trusted that should not be.
 * [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
 */
internal class OverpassClient(
    private val client: HttpClient,
    private val endpoints: List<String> = OverpassEndpoints.ALL,
) {

    /**
     * The first answer that is BOTH successful and trustworthy, or the last failure.
     *
     * A server that is down and a server that lies both just pass their turn: a broken database
     * answers 200 in 0,3 s with an empty list, and only its own `timestamp_osm_base` gives it away.
     */
    suspend fun fetch(query: String, tag: String): Result<String> {
        var lastFailure: Throwable? = null
        for (endpoint in endpoints) {
            val body = runCatching { request(endpoint, query) }
                .onFailure { error ->
                    // Log WHICH mirror passed and why, so a diagnosis can say where the answer
                    // finally came from.
                    PaparcarLogger.w(tag, "overpass ${endpoint.host()} passed: ${error.message}")
                    lastFailure = error
                }
                .getOrNull() ?: continue
            return Result.success(body)
        }
        return Result.failure(lastFailure ?: IllegalStateException("No Overpass endpoint configured"))
    }

    private suspend fun request(endpoint: String, query: String): String {
        val response = client.submitForm(endpoint, Parameters.build { append("data", query) })
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
}
