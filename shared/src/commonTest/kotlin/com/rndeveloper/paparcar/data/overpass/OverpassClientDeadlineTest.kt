package com.rndeveloper.paparcar.data.overpass

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The walk's deadline, driven by the mirror that caused it.
 * [OVERPASS-A-FAILOVER-MUST-HAVE-A-DEADLINE-001]
 *
 * ⛔ Time here is a FAKE CLOCK, not `delay`: a socket that goes quiet is an engine-level event that
 * `MockEngine` cannot stage, so staging it with sleeps would test the test. What is provable — and
 * what actually broke on device — is how much patience each mirror is HANDED and when the walk stops
 * handing any out.
 */
class OverpassClientDeadlineTest {

    private val endpoints = listOf("https://first/api", "https://second/api", "https://third/api")

    /** Car parks and stations both declare this; the POI lookup declares 8. */
    private val query = "[out:json][timeout:20];nwr(around:1500,40.42,-3.70)[amenity=parking];out center 80;"

    private val healthy = """
        {"version":0.6,"osm3s":{"timestamp_osm_base":"2026-09-10T16:49:26Z"},"elements":[]}
    """.trimIndent()

    private fun clientOf(engine: MockEngine) = HttpClient(engine) { install(HttpTimeout) }

    @Test
    fun should_handAQuietMirrorEightSeconds_andTheLastOneWhatIsLeft() = runTest {
        val patience = mutableListOf<Pair<String, Long?>>()
        // Each silent mirror burns exactly the patience it was handed, which is what the walk then
        // has to subtract from what it gives the next one.
        var now = 0L
        val engine = MockEngine { request ->
            patience += request.url.host to
                request.getCapabilityOrNull(HttpTimeoutCapability)?.socketTimeoutMillis
            now += OverpassWalkPatience.QUIET_MIRROR_MS
            respondError(HttpStatusCode.GatewayTimeout)
        }

        val result = OverpassClient(clientOf(engine), endpoints) { now }.fetch(query, "test")

        assertEquals<List<Pair<String, Long?>>>(
            listOf("first" to 8_000L, "second" to 8_000L, "third" to 14_000L),
            patience,
            "a quiet mirror gets its turn; the last one inherits the rest of the walk",
        )
        assertTrue(result.isFailure, "three refusals are a failure, never an empty answer")
    }

    @Test
    fun should_stopAskingAndSaySo_when_theBudgetIsSpentBeforeTheListEnds() = runTest {
        val asked = mutableListOf<String>()
        var now = 0L
        val engine = MockEngine { request ->
            asked += request.url.host
            // A mirror that burns the whole ceiling on its own — what a 60 s silence looked like.
            now += OverpassWalkPatience.WALK_CEILING_MS
            respondError(HttpStatusCode.GatewayTimeout)
        }

        val result = OverpassClient(clientOf(engine), endpoints) { now }.fetch(query, "test")

        assertEquals(listOf("first"), asked, "the walk stops paying once its budget is gone")
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("504") == true,
            "the failure keeps the reason the last server gave, not a generic timeout: " +
                "${result.exceptionOrNull()?.message}",
        )
    }

    @Test
    fun should_keepTheFailoverWhole_when_anEarlyMirrorRefuses() = runTest {
        // ⛔ The regression this ticket must not cause: a deadline that eats the failover.
        val asked = mutableListOf<String>()
        var now = 0L
        val engine = MockEngine { request ->
            asked += request.url.host
            now += 2_000
            if (request.url.host == "first") {
                respondError(HttpStatusCode.GatewayTimeout)
            } else {
                respond(healthy, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val result = OverpassClient(clientOf(engine), endpoints) { now }.fetch(query, "test")

        assertEquals(listOf("first", "second"), asked)
        assertTrue(result.isSuccess, "the second mirror answered and the walk must return it")
    }

    @Test
    fun should_askWithTheBudgetTheQueryDeclared_when_itIsTheShortOne() = runTest {
        // The POI lookup asks the server for 8 s; the client must not wait on 20.
        val requestBudgets = mutableListOf<Long?>()
        var now = 0L
        val engine = MockEngine { request ->
            requestBudgets += request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
            now += 1_000
            respond(healthy, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        OverpassClient(clientOf(engine), endpoints) { now }
            .fetch("[out:json][timeout:8];node(around:60,40.42,-3.70);out center 1;", "test")

        assertEquals<List<Long?>>(
            listOf(8_000L + OverpassWalkPatience.NETWORK_GRACE_MS),
            requestBudgets,
            "the client's patience is the budget the query granted the server, plus the network's slack",
        )
    }
}
