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
 * Three collaborators, one per question this class refuses to answer itself:
 * - [OverpassQuota] — *may I ask at all right now?* The server allows 2 asks per IP and the app has
 *   five lanes; without this they 504 each other. [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]
 * - [OverpassWalkPatience] — *how long does this server get?* Every number, and the measurements
 *   behind them, live there.
 * - [OverpassResponseHealth] — *can this answer be believed?*
 *
 * Here there is only I/O. [OVERPASS-A-FAILOVER-MUST-HAVE-A-DEADLINE-001]
 */
internal class OverpassClient(
    private val client: HttpClient,
    private val endpoints: List<String> = OverpassEndpoints.ALL,
    private val quota: OverpassQuota = SHARED_QUOTA,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /**
     * The first answer that is BOTH successful and trustworthy, or the last failure.
     *
     * A server that is down, a server that lies and a server that says nothing all just pass their
     * turn: a broken database answers 200 in 0,3 s with an empty list, and only its own
     * `timestamp_osm_base` gives it away.
     *
     * ⛔ The slot is taken around the WHOLE walk, not around each mirror. Releasing it between
     * mirrors would let a second lane slip in mid-failover, which is the very interleaving that
     * turns a 1,4 s query into a 14 s 504.
     */
    suspend fun fetch(query: String, tag: String): Result<String> =
        quota.withSlot { walk(query, tag) }
            ?: Result.failure(
                IllegalStateException(
                    "another Overpass lane held the only slot for ${OverpassQuota.WAIT_CEILING_MS} ms",
                ).also {
                    // ⛔ Never silent: a lane that never got to ask must not look like a lane that
                    // asked and found nothing.
                    PaparcarLogger.w(tag, "overpass slot never came free; not asking")
                },
            )

    private suspend fun walk(query: String, tag: String): Result<String> {
        val serverBudgetMs = OverpassWalkPatience.declaredServerBudgetMs(query)
        val startedAtMs = nowMs()
        var lastFailure: Throwable? = null

        endpoints.forEachIndexed { index, endpoint ->
            val attempt = OverpassWalkPatience.attempt(
                spentMs = nowMs() - startedAtMs,
                serverBudgetMs = serverBudgetMs,
                mirrorsLeft = endpoints.size - index,
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
        // ⛔ The 200 is not enough: a mirror with a broken database answers 200 with an empty list,
        // and one nobody maintains answers 200 about last quarter.
        if (!OverpassResponseHealth.isTrustworthy(body, nowMs())) {
            throw IllegalStateException("answered 200 with a missing or stale base timestamp")
        }
        return body
    }

    private fun budgetSpent(startedAtMs: Long) = IllegalStateException(
        "No Overpass mirror answered within ${nowMs() - startedAtMs} ms",
    )

    /** Just for the log line — the whole URL would drown the message it is trying to make. */
    private fun String.host(): String = substringAfter("://").substringBefore("/")

    private companion object {
        /**
         * ⛔ The process-wide budget, shared by every lane that constructs a client. See
         * [OverpassQuota] for why this is a singleton and not something DI hands out: five
         * constructors that must remember to be given the budget are five chances to forget it, and
         * that is the bug being fixed.
         */
        val SHARED_QUOTA = OverpassQuota()
    }
}
