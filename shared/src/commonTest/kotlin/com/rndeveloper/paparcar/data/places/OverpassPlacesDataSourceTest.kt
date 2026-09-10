package com.rndeveloper.paparcar.data.places

import com.rndeveloper.paparcar.domain.model.PlaceCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What this lane may CLAIM, and that it walks the mirrors like every other one.
 * [OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001]
 *
 * The failover itself is already pinned by `OsmFuelStationsFailoverTest`
 * [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]; what nothing covered is that **this** source
 * goes through it — it used to speak HttpURLConnection against a single hard-coded mirror.
 */
class OverpassPlacesDataSourceTest {

    @Test
    fun should_nameTheNearestPlace_when_theServerAnswers() = runTest {
        val source = OverpassPlacesDataSource(HttpClient(MockEngine { healthy() }), MIRRORS)

        val place = source.getNearbyPlace(LAT, LON).getOrThrow()

        assertEquals("Café Comercial", place?.name)
        assertEquals(PlaceCategory.CAFE, place?.category)
    }

    @Test
    fun should_moveOnToTheNextMirror_when_theFirstOneIsDown() = runTest {
        val asked = mutableListOf<String>()
        val engine = MockEngine { request ->
            asked += request.url.host
            if (request.url.host == "first") respondError(HttpStatusCode.GatewayTimeout) else healthy()
        }

        val place = OverpassPlacesDataSource(HttpClient(engine), MIRRORS)
            .getNearbyPlace(LAT, LON)
            .getOrThrow()

        assertEquals(listOf("first", "second"), asked, "this lane must walk the list, not stop at [0]")
        assertEquals("Café Comercial", place?.name)
    }

    /**
     * ⛔ The distinction the hand-rolled transports got wrong: `success(null)` means *"we asked and
     * there is no place here"*, and `AddressAndPlaceRepositoryImpl` believes it enough to **seal
     * that cell for 30 days** (`poiChecked = true`). They returned `null` for any non-200, so one
     * 504 taught the cache that your own street has no POI for a month — exactly what that
     * repository's own KDoc says must never happen.
     */
    @Test
    fun should_reportAFailure_when_everyMirrorIsDown() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.GatewayTimeout) }

        val result = OverpassPlacesDataSource(HttpClient(engine), MIRRORS).getNearbyPlace(LAT, LON)

        assertTrue(
            result.isFailure,
            "a network failure must NOT arrive as success(null): that seals the cell for 30 days",
        )
    }

    @Test
    fun should_answerThatThereIsNoPlace_when_theServerAnswersWithNothingNearby() = runTest {
        val source = OverpassPlacesDataSource(HttpClient(MockEngine { emptyButHealthy() }), MIRRORS)

        val result = source.getNearbyPlace(LAT, LON)

        assertTrue(result.isSuccess, "an honest empty answer IS an answer, and does seal the cell")
        assertNull(result.getOrThrow())
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    private fun MockRequestHandleScope.healthy() = respond(
        content = """{"osm3s":{"timestamp_osm_base":"$HEALTHY_BASE"},"elements":[
            {"type":"node","lat":$LAT,"lon":$LON,"tags":{"name":"Café Comercial","amenity":"cafe"}}
        ]}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun MockRequestHandleScope.emptyButHealthy() = respond(
        content = """{"osm3s":{"timestamp_osm_base":"$HEALTHY_BASE"},"elements":[]}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private companion object {
        const val LAT = 40.4168
        const val LON = -3.7038
        const val HEALTHY_BASE = "2026-09-10T12:48:02Z"
        val MIRRORS = listOf("https://first/api", "https://second/api", "https://third/api")
    }
}
