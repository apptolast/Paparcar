package com.rndeveloper.paparcar.data.overpass

import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlin.time.Clock

/**
 * Asks Overpass, walking down the mirror list until one answers **and can be believed**.
 * [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
 *
 * Extracted from `OsmFuelStationsSource` when France arrived needing the very same failover for a
 * different question — station names by official id. Two copies of a retry loop are two places for
 * a mirror to be trusted that should not be.
 * [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
 *
 * The walk is on a budget it SPENDS — see [OverpassWalkPatience], which owns every number and the
 * reasoning behind them. Here there is only I/O. [OVERPASS-A-FAILOVER-MUST-HAVE-A-DEADLINE-001]
 */
internal class OverpassClient(
    private val client: HttpClient,
    private val endpoints: List<String> = OverpassEndpoints.ALL,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /**
     * The first answer that is BOTH successful and trustworthy, or the last failure.
     *
     * A server that is down, a server that lies and a server that says nothing all just pass their
     * turn: a broken database answers 200 in 0,3 s with an empty list, and only its own
     * `timestamp_osm_base` gives it away.
     */
    suspend fun fetch(query: String, tag: String): Result<String> {
        val serverBudgetMs = OverpassWalkPatience.declaredServerBudgetMs(query)
        val startedAtMs = nowMs()
        var lastFailure: Throwable? = null

        endpoints.forEachIndexed { index, endpoint ->
            val attempt = OverpassWalkPatience.attempt(
                spentMs = nowMs() - startedAtMs,
                serverBudgetMs = serverBudgetMs,
                isLast = index == endpoints.lastIndex,
            )
            // ⛔ Never a silent cap: giving up with servers left unasked is a decision, and a
            // decision the log does not carry is one nobody can diagnose. The walk still fails with
            // the REASON the last server gave, never with an empty answer.
            if (attempt == null) {
                PaparcarLogger.w(
                    tag,
                    "overpass budget spent after ${nowMs() - startedAtMs} ms; " +
                        "${endpoints.size - index} mirror(s) left unasked",
                )
                return Result.failure(lastFailure ?: budgetSpent(startedAtMs))
            }

            val body = runCatching { request(endpoint, query, attempt) }
                .onFailure { error ->
                    // Log WHICH mirror passed, why, and how much patience it was given — the three
                    // things a diagnosis needs to tell "it is down" from "we did not wait".
                    PaparcarLogger.w(
                        tag,
                        "overpass ${endpoint.host()} passed after ${attempt.firstByteMs} ms of " +
                            "patience: ${error.message}",
                    )
                    lastFailure = error
                }
                .getOrNull() ?: return@forEachIndexed
            return Result.success(body)
        }
        return Result.failure(lastFailure ?: IllegalStateException("No Overpass endpoint configured"))
    }

    private suspend fun request(
        endpoint: String,
        query: String,
        attempt: OverpassWalkPatience.Attempt,
    ): String {
        val response = client.submitForm(endpoint, Parameters.build { append("data", query) }) {
            // ⛔ Per REQUEST, not on the shared client: `fuelHttpClient()` is also what Spain's
            // Ministry and France's feed download whole provinces through, and those need the long
            // patience this walk is taking away.
            timeout {
                requestTimeoutMillis = attempt.requestMs
                socketTimeoutMillis = attempt.firstByteMs
                connectTimeoutMillis = attempt.connectMs
            }
        }
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

    private fun budgetSpent(startedAtMs: Long) = IllegalStateException(
        "No Overpass mirror answered within ${nowMs() - startedAtMs} ms",
    )

    /** Just for the log line — the whole URL would drown the message it is trying to make. */
    private fun String.host(): String = substringAfter("://").substringBefore("/")
}
