package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.data.overpass.freshBase
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The User-Agent is not decoration. [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
 *
 * ⛔ Overpass answers **406** to OkHttp's default agent (`okhttp/x.y.z` is in its bot filter),
 * measured on 09-09-2026 by bisecting headers against the live endpoint: identical request, 200
 * with curl's agent, 406 with okhttp's. That was the unexplained device failure of
 * FUEL-STATIONS-LIST-001 — Spain never noticed because the Ministry answers first and does not
 * filter, so the whole fuel feature looked fine while every country but Spain was broken.
 *
 * Pinned here because a header nobody asserts is a header the next refactor deletes.
 */
class FuelHttpClientTest {

    @Test
    fun should_identifyTheApp_onEveryRequestItMakes() = runTest {
        var seenAgent: String? = null
        val engine = MockEngine { request ->
            seenAgent = request.headers[HttpHeaders.UserAgent]
            respond(
                // With the base timestamp a healthy server publishes — without it the source now
                // (rightly) rejects the answer and moves on.
                // [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]
                content = """{"osm3s":{"timestamp_osm_base":"${freshBase()}"},"elements":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        OsmFuelStationsSource(fuelHttpClient(engine))
            .stationsNear(FuelStationsQuery(40.4168, -3.7038, countryCode = "FR"))
            .getOrThrow()

        assertEquals(FUEL_HTTP_USER_AGENT, seenAgent)
        assertTrue(
            seenAgent?.startsWith("okhttp") != true,
            "the default engine agent is what Overpass rejects with 406",
        )
    }
}
