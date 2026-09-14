package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.data.overpass.freshBase
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The failover, driven by the three answers Overpass actually gave on 09-09-2026.
 * [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
 */
class OsmFuelStationsFailoverTest {

    private val endpoints = listOf("https://first/api", "https://second/api", "https://third/api")

    /** A healthy answer: real base timestamp + one station. */
    private fun healthy(name: String = "Repsol") = """
        {"version":0.6,"osm3s":{"timestamp_osm_base":"${freshBase()}"},
         "elements":[{"type":"node","id":1,"lat":40.42,"lon":-3.70,"tags":{"brand":"$name"}}]}
    """.trimIndent()

    /** What `overpass.osm.ch` really answered: 200, empty, and a COUNTER where a date belongs. */
    private val brokenBase = """
        {"version":0.6,"osm3s":{"timestamp_osm_base":"116992"},"elements":[]}
    """.trimIndent()

    /** A legitimate "nothing here" from a healthy server — the middle of nowhere. */
    private val honestlyEmpty = """
        {"version":0.6,"osm3s":{"timestamp_osm_base":"${freshBase()}"},"elements":[]}
    """.trimIndent()

    private val query = FuelStationsQuery(40.4168, -3.7038, countryCode = "FR")

    @Test
    fun should_useTheFirstServer_andAskNobodyElse_when_itAnswersWell() = runTest {
        val asked = mutableListOf<String>()
        val engine = MockEngine { request ->
            asked += request.url.host
            respond(healthy(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = OsmFuelStationsSource(HttpClient(engine), endpoints).stationsNear(query).getOrThrow()

        assertEquals(listOf("first"), asked)
        assertEquals(1, result.stations.size)
    }

    @Test
    fun should_moveOn_when_aServerIsDown() = runTest {
        val asked = mutableListOf<String>()
        val engine = MockEngine { request ->
            asked += request.url.host
            if (request.url.host == "first") respondError(HttpStatusCode.GatewayTimeout)
            else respond(healthy(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = OsmFuelStationsSource(HttpClient(engine), endpoints).stationsNear(query).getOrThrow()

        assertEquals(listOf("first", "second"), asked)
        assertEquals(1, result.stations.size)
    }

    @Test
    fun should_moveOn_when_aServerAnswers200_withABrokenDatabase() = runTest {
        // ⛔ The measured trap: 200, fast, empty — and the app would have said "no stations here".
        val asked = mutableListOf<String>()
        val engine = MockEngine { request ->
            asked += request.url.host
            val body = if (request.url.host == "first") brokenBase else healthy()
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = OsmFuelStationsSource(HttpClient(engine), endpoints).stationsNear(query).getOrThrow()

        assertEquals(listOf("first", "second"), asked, "a 200 with a counter for a timestamp is not an answer")
        assertEquals(1, result.stations.size)
    }

    @Test
    fun should_acceptAnEmptyList_when_theServerIsHealthy() = runTest {
        // The desert: nothing around, said by a server that knows what day it is. Trying the next
        // one here would retry forever wherever there genuinely are no stations.
        val asked = mutableListOf<String>()
        val engine = MockEngine { request ->
            asked += request.url.host
            respond(honestlyEmpty, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = OsmFuelStationsSource(HttpClient(engine), endpoints).stationsNear(query).getOrThrow()

        assertEquals(listOf("first"), asked)
        assertTrue(result.stations.isEmpty())
    }

    @Test
    fun should_failHonestly_when_everyServerRefusesOrLies() = runTest {
        val asked = mutableListOf<String>()
        val engine = MockEngine { request ->
            asked += request.url.host
            when (request.url.host) {
                "first" -> respondError(HttpStatusCode.GatewayTimeout)
                "second" -> respond(brokenBase, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respondError(HttpStatusCode.ServiceUnavailable)
            }
        }

        val result = OsmFuelStationsSource(HttpClient(engine), endpoints).stationsNear(query)

        assertEquals(listOf("first", "second", "third"), asked)
        assertTrue(result.isFailure, "no station list may be invented out of three refusals")
    }
}
