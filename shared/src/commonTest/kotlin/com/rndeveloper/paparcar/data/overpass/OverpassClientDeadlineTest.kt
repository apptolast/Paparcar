@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The walk's deadline, driven by the mirror that caused it.
 * [OVERPASS-A-FAILOVER-MUST-HAVE-A-DEADLINE-001]
 * [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]
 *
 * ⛔ Time here is a FAKE CLOCK, not `delay`: a socket that goes quiet is an engine-level event that
 * `MockEngine` cannot stage, so staging it with sleeps would test the test. What is provable — and
 * what actually broke on device — is how much patience each mirror is HANDED and when the walk stops
 * handing any out.
 */
class OverpassClientDeadlineTest {

    private val endpoints = listOf("https://first/api", "https://second/api", "https://third/api")

    /** The live list's shape since the two dead mirrors were measured out. */
    private val livePair = listOf("https://first/api", "https://second/api")

    /** Car parks and stations both declare this; the POI lookup declares 8. */
    private val query = "[out:json][timeout:20];nwr(around:1500,40.42,-3.70)[amenity=parking];out center 80;"

    /** 2026-09-14T09:00:00Z — the morning the mirrors were measured. */
    private val nowStartMs = 1_789_376_400_000L

    private val healthy = """
        {"version":0.6,"osm3s":{"timestamp_osm_base":"2026-09-14T08:54:36Z"},"elements":[]}
    """.trimIndent()

    /** What `overpass.private.coffee` served on 14-09-2026: a real date, 105 days old. */
    private val stale = """
        {"version":0.6,"osm3s":{"timestamp_osm_base":"2026-06-01T08:52:28Z"},"elements":[]}
    """.trimIndent()

    private fun clientOf(engine: MockEngine) = HttpClient(engine) { install(HttpTimeout) }

    @Test
    fun should_splitTheWalkBetweenTheTwoLiveMirrors_when_bothGoQuiet() = runTest {
        val patience = mutableListOf<Pair<String, Long?>>()
        var now = nowStartMs
        val engine = MockEngine { request ->
            val handed = request.getCapabilityOrNull(HttpTimeoutCapability)?.socketTimeoutMillis
            patience += request.url.host to handed
            // Each silent mirror burns exactly the patience it was handed.
            now += handed ?: 0L
            respondError(HttpStatusCode.GatewayTimeout)
        }

        val result = OverpassClient(clientOf(engine), livePair) { now }.fetch(query, "test")

        assertEquals<List<Pair<String, Long?>>>(
            listOf("first" to 15_000L, "second" to 15_000L),
            patience,
            "two mirrors split the 30 s ceiling; neither is cut at the old 8 s",
        )
        assertTrue(result.isFailure, "two refusals are a failure, never an empty answer")
    }

    @Test
    fun should_rebalanceWithoutAnyoneEditingANumber_when_aThirdMirrorIsAdded() = runTest {
        // ⛔ The regression that produced this ticket: the list rotted while the cut stayed at 8 s.
        // The share is derived, so a longer list re-divides itself — floored at what a real answer
        // was measured to need.
        val patience = mutableListOf<Long?>()
        var now = nowStartMs
        val engine = MockEngine { request ->
            val handed = request.getCapabilityOrNull(HttpTimeoutCapability)?.socketTimeoutMillis
            patience += handed
            now += handed ?: 0L
            respondError(HttpStatusCode.GatewayTimeout)
        }

        OverpassClient(clientOf(engine), endpoints) { now }.fetch(query, "test")

        assertEquals<List<Long?>>(listOf(12_000L, 12_000L, 6_000L), patience)
        assertEquals(30_000L, patience.filterNotNull().sum(), "and the whole walk still fits the ceiling")
    }

    @Test
    fun should_stopAskingAndSaySo_when_theBudgetIsSpentBeforeTheListEnds() = runTest {
        val asked = mutableListOf<String>()
        var now = nowStartMs
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
        var now = nowStartMs
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
    fun should_passTheTurnOn_when_aMirrorAnswers200AboutLastQuarter() = runTest {
        // The exact shape of the old first fallback: HTTP 200, parseable date, 105 days behind.
        val asked = mutableListOf<String>()
        var now = nowStartMs
        val engine = MockEngine { request ->
            asked += request.url.host
            now += 2_000
            val body = if (request.url.host == "first") stale else healthy
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = OverpassClient(clientOf(engine), endpoints) { now }.fetch(query, "test")

        assertEquals(listOf("first", "second"), asked, "a stale 200 must not end the walk")
        assertEquals(healthy, result.getOrNull(), "the fresh mirror's answer is the one that wins")
    }

    /**
     * ⛔ El camino que el cupo estrenó y que nadie ejecutaba.
     * [OVERPASS-THE-QUOTAS-WAIT-IS-PART-OF-THE-WALK-001]
     *
     * Importa más de lo que parece por quién lo consume: `AddressAndPlaceRepositoryImpl` **sella la
     * celda 30 días** cuando la búsqueda de POI contesta bien (`poiChecked = true`). Si quedarse sin
     * turno se colase como "pregunté y no hay nada", una tanda de carriles pisándose enseñaría a la
     * caché que tu propia calle no tiene ningún sitio, durante un mes. Por eso no basta con que hoy
     * devuelva `failure`: hace falta que alguien lo compruebe.
     */
    @Test
    fun should_failWithoutAskingAnybody_when_anotherLaneHoldsTheOnlySlot() = runTest {
        val asked = mutableListOf<String>()
        val engine = MockEngine { request ->
            asked += request.url.host
            respond(healthy, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        // Un cupo cuyo único permiso ya está tomado y que no se suelta dentro de la espera.
        val busy = OverpassQuota(permits = 1)
        backgroundScope.launch { busy.withSlot { awaitCancellation() } }
        runCurrent()

        val result = OverpassClient(clientOf(engine), endpoints, busy) { nowStartMs }
            .fetch(query, "test")

        assertTrue(result.isFailure, "sin turno NO se inventa una respuesta vacía")
        assertTrue(asked.isEmpty(), "y no se molesta a ningún espejo: nunca salió la petición")
        assertTrue(
            result.exceptionOrNull()?.message?.contains("slot") == true,
            "el motivo tiene que decir que fue el cupo, no un fallo del servidor: " +
                "${result.exceptionOrNull()?.message}",
        )
    }

    /** Y en cuanto el carril de delante suelta, el siguiente pasa: el cupo retrasa, no condena. */
    @Test
    fun should_answerNormally_when_theSlotComesFreeInTime() = runTest {
        val engine = MockEngine {
            respond(healthy, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val quota = OverpassQuota(permits = 1)
        val gate = CompletableDeferred<Unit>()
        backgroundScope.launch { quota.withSlot { gate.await() } }
        runCurrent()
        val waiter = backgroundScope.async {
            OverpassClient(clientOf(engine), endpoints, quota) { nowStartMs }.fetch(query, "test")
        }

        // Se suelta MUY por debajo del techo de espera: el cupo retrasa, no condena.
        gate.complete(Unit)

        assertTrue(waiter.await().isSuccess)
    }

    @Test
    fun should_askWithTheBudgetTheQueryDeclared_when_itIsTheShortOne() = runTest {
        // The POI lookup asks the server for 8 s; the client must not wait on 20.
        val requestBudgets = mutableListOf<Long?>()
        var now = nowStartMs
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
