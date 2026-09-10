package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.model.FuelType
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole French adapter — request, parse and the OSM name join — with no network.
 * [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
 *
 * The request itself is asserted, not just the answer: France pushes the radius, the ordering and
 * the field list onto the server, so a wrong parameter here does not fail loudly — it comes back
 * `200` with a list that is quietly wrong, which is the failure mode this whole area keeps hitting.
 */
class FranceFuelStationsSourceTest {

    private val feedJson = """
        {
          "total_count": 2,
          "results": [
            {
              "id": 31200022,
              "geom": { "lon": 1.421, "lat": 43.616 },
              "adresse": "55 BOULEVARD DE SUISSE",
              "ville": "Toulouse",
              "gazole_prix": 2.25,
              "gazole_maj": "2026-09-10T00:01:00+00:00",
              "gazole_rupture_type": null,
              "e10_prix": 1.99,
              "e10_maj": "2026-09-10T00:01:00+00:00",
              "e10_rupture_type": null
            },
            {
              "id": 31500007,
              "geom": { "lon": 1.459, "lat": 43.626 },
              "adresse": "231 FAUBOURG BONNEFOY",
              "ville": "Toulouse",
              "gazole_prix": 2.27,
              "gazole_maj": "2026-09-09T08:06:00+00:00",
              "gazole_rupture_type": null
            }
          ]
        }
    """.trimIndent()

    /**
     * Real OSM shape: the official id lives in `ref:FR:prix-carburants`, which is the join. The
     * second element is a DIFFERENT station 40 m away with no ref — the neighbour a proximity join
     * would have grabbed.
     */
    private val osmJson = """
        {
          "version": 0.6,
          "osm3s": { "timestamp_osm_base": "2026-09-09T21:00:00Z" },
          "elements": [
            { "type": "node", "id": 1, "lat": 43.6161, "lon": 1.4211,
              "tags": { "amenity": "fuel", "brand": "TotalEnergies",
                        "ref:FR:prix-carburants": "31200022" } },
            { "type": "node", "id": 2, "lat": 43.6164, "lon": 1.4214,
              "tags": { "amenity": "fuel", "brand": "Esso" } }
          ]
        }
    """.trimIndent()

    private val asked = mutableListOf<String>()

    /** Decoded query parameters of the feed request — the URL string is percent-encoded, so
     *  asserting on it would test the encoder instead of the request we meant to send. */
    private val feedParams = mutableMapOf<String, String>()

    private fun toulouse(radiusMeters: Int = 5_000) = FuelStationsQuery(
        latitude = 43.6047,
        longitude = 1.4442,
        countryCode = "FR",
        radiusMeters = radiusMeters,
    )

    private fun source(
        feed: String = feedJson,
        feedStatus: HttpStatusCode = HttpStatusCode.OK,
        osm: String? = osmJson,
    ): FranceFuelStationsSource {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            asked += url
            when {
                url.contains("data.economie.gouv.fr") -> {
                    request.url.parameters.names().forEach { name ->
                        request.url.parameters[name]?.let { feedParams[name] = it }
                    }
                    if (feedStatus == HttpStatusCode.OK) {
                        respond(feed, feedStatus, headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        respondError(feedStatus)
                    }
                }

                osm != null ->
                    respond(osm, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

                else -> respondError(HttpStatusCode.ServiceUnavailable)
            }
        }
        return FranceFuelStationsSource(HttpClient(engine), endpoints = listOf(OVERPASS))
    }

    @Test
    fun `should_answerForFranceOnly`() {
        val source = source()
        assertTrue(source.covers("FR"))
        assertTrue(source.covers("fr"))
        assertFalse(source.covers("ES"))
        assertFalse(source.covers(""))
    }

    @Test
    fun `should_sendTheRadiusWithItsUnit_when_askingTheFeed`() = runTest {
        source().stationsNear(toulouse(radiusMeters = 5_000)).getOrThrow()

        val where = feedParams.getValue("where")
        // ⛔ Measured: a unitless `5000` answers 200 with `total_count: 0` — not an error, just an
        // empty list that reads exactly like "no stations near you".
        assertTrue("5000m" in where, "radius must carry its unit: $where")
        // ⚠️ POINT is longitude first, the opposite of every other coordinate in the app.
        assertTrue("POINT(1.4442 43.6047)" in where, "lon before lat: $where")
    }

    @Test
    fun `should_askTheServerToOrderAndTrim_when_askingTheFeed`() = runTest {
        source().stationsNear(toulouse()).getOrThrow()

        // Nearest-first comes from the server; 100 is the API's hard ceiling (101 answers 400).
        assertTrue("distance" in feedParams.getValue("order_by"), feedParams.toString())
        assertEquals("100", feedParams.getValue("limit"))
        // The field list is what took Paris/8 km from 275 KB to 13,7 KB, and it is built from the
        // same enum the parser reads, so a product cannot be parsed but never fetched.
        val select = feedParams.getValue("select")
        assertTrue("gazole_maj" in select && "e10_prix" in select, select)
    }

    @Test
    fun `should_nameTheStation_when_osmCarriesItsOfficialId`() = runTest {
        val result = source().stationsNear(toulouse()).getOrThrow()

        // Joined on `ref:FR:prix-carburants`, measured at 97% coverage and 100% named.
        assertEquals("TotalEnergies", result.stations.single { it.id == "31200022" }.name)
    }

    @Test
    fun `should_leaveTheNeighbourAlone_when_itCarriesNoOfficialId`() = runTest {
        val result = source().stationsNear(toulouse()).getOrThrow()

        // ⛔ The Esso node sits 40 m from the other station — inside every proximity threshold that
        // would name anything at all — and it must not lend its name to anyone. Measured p50
        // distance between a French station and its OWN twin is 43 m, so proximity cannot tell
        // these two apart. An id can.
        assertNull(result.stations.single { it.id == "31500007" }.name)
    }

    @Test
    fun `should_stillDeliverPrices_when_overpassIsDown`() = runTest {
        val result = source(osm = null).stationsNear(toulouse()).getOrThrow()

        // ⛔ A name is a nicety; the price is the product. Overpass has no SLA — it went down twice
        // in one afternoon — and that must never cost France its prices.
        assertEquals(2, result.stations.size)
        assertTrue(result.stations.all { it.name == null })
        assertEquals(
            2.25,
            result.stations.single { it.id == "31200022" }
                .prices.getValue(FuelType.DIESEL).perUnit.amount,
        )
    }

    @Test
    fun `should_reportNoResponseHour_when_theSourceStampsEachPriceInstead`() = runTest {
        val result = source().stationsNear(toulouse()).getOrThrow()

        // ⛔ Null on purpose. Quoting one hour for a French response would be a claim no station
        // made: prices inside one answer were measured from 0,7 h to thirteen months old.
        assertNull(result.reportedAt)
        assertEquals(
            1788998460000L,
            result.stations.single { it.id == "31200022" }
                .prices.getValue(FuelType.DIESEL).reportedAtMs,
        )
    }

    @Test
    fun `should_declineToName_when_twoOsmElementsClaimTheSameOfficialIdWithDifferentNames`() =
        runTest {
            // Measured: 3 refs appear twice within Paris alone. Asymmetric failure — nameless beats
            // the neighbour's name, because a wrong brand on a right price is a sentence the user
            // has no way to check.
            val conflicting = """
                {
                  "osm3s": { "timestamp_osm_base": "2026-09-09T21:00:00Z" },
                  "elements": [
                    { "type": "node", "id": 1, "lat": 43.6161, "lon": 1.4211,
                      "tags": { "brand": "TotalEnergies",
                                "ref:FR:prix-carburants": "31200022" } },
                    { "type": "way", "id": 2, "center": { "lat": 43.6162, "lon": 1.4212 },
                      "tags": { "brand": "Avia", "ref:FR:prix-carburants": "31200022" } }
                  ]
                }
            """.trimIndent()

            val result = source(osm = conflicting).stationsNear(toulouse()).getOrThrow()
            assertNull(result.stations.single { it.id == "31200022" }.name)
        }

    @Test
    fun `should_keepTheNameOfADuplicatedId_when_bothElementsAgree`() = runTest {
        // A forecourt mapped as both a node and a way is one station said twice, not a conflict.
        val agreeing = """
            {
              "osm3s": { "timestamp_osm_base": "2026-09-09T21:00:00Z" },
              "elements": [
                { "type": "node", "id": 1, "lat": 43.6161, "lon": 1.4211,
                  "tags": { "brand": "TotalEnergies", "ref:FR:prix-carburants": "31200022" } },
                { "type": "way", "id": 2, "center": { "lat": 43.6162, "lon": 1.4212 },
                  "tags": { "brand": "TotalEnergies", "ref:FR:prix-carburants": "31200022" } }
              ]
            }
        """.trimIndent()

        val result = source(osm = agreeing).stationsNear(toulouse()).getOrThrow()
        assertEquals("TotalEnergies", result.stations.single { it.id == "31200022" }.name)
    }

    @Test
    fun `should_declineToName_when_theIdMatchesButTheTwinIsKilometresAway`() = runTest {
        // One pair of the 156 measured sat 2.359 m apart: the id matched but one of the two records
        // is wrong, and we cannot tell which. The sanity radius is generous on purpose — the feed's
        // own coordinate is p90 = 134 m out — so it only catches the genuinely absurd.
        val faraway = """
            {
              "osm3s": { "timestamp_osm_base": "2026-09-09T21:00:00Z" },
              "elements": [
                { "type": "node", "id": 1, "lat": 43.7000, "lon": 1.5000,
                  "tags": { "brand": "Esso", "ref:FR:prix-carburants": "31200022" } }
              ]
            }
        """.trimIndent()

        val result = source(osm = faraway).stationsNear(toulouse()).getOrThrow()
        assertNull(result.stations.single { it.id == "31200022" }.name)
    }

    @Test
    fun `should_fail_when_theFeedIsDown`() = runTest {
        val result = source(feedStatus = HttpStatusCode.ServiceUnavailable).stationsNear(toulouse())

        // A failure, never an empty list — it is what lets the registry fall back to OSM instead of
        // announcing "no stations in France".
        assertTrue(result.isFailure)
    }

    private companion object {
        const val OVERPASS = "https://overpass.test/api/interpreter"
    }
}
