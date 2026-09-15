package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
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
 * The whole German adapter — request, `ok` gate and parse — with no network.
 * [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
 *
 * The fixture is the live endpoint's real shape (Berlin, 14/15-09-2026), including the two rows
 * that carry the measured traps: a station whose petrol answer must come from E10 because E5 is
 * null, and a closed station that still prices everything.
 */
class GermanyFuelStationsSourceTest {

    private val feedJson = """
        {
          "ok": true,
          "license": "CC BY 4.0 -  https://creativecommons.tankerkoenig.de",
          "data": "MTS-K",
          "status": "ok",
          "stations": [
            {
              "id": "278130b1-e062-4a0f-80cc-19e486b4c024",
              "name": "Aral Tankstelle",
              "brand": "ARAL",
              "street": "Holzmarktstraße",
              "place": "Berlin",
              "lat": 52.514153,
              "lng": 13.421487,
              "dist": 1.4,
              "diesel": 2.399,
              "e5": 2.329,
              "e10": 2.269,
              "isOpen": true,
              "houseNumber": "12/14",
              "postCode": 10179
            },
            {
              "id": "e1a15081-24ca-9107-e040-0b0a3dfe563c",
              "name": "Berlin, Wittestr. 16",
              "brand": "HEM",
              "street": "Wittestr.",
              "place": "Berlin",
              "lat": 52.577588,
              "lng": 13.306636,
              "dist": 10.9,
              "diesel": null,
              "e5": null,
              "e10": 2.279,
              "isOpen": false,
              "houseNumber": "16",
              "postCode": 13509
            }
          ]
        }
    """.trimIndent()

    /** Real shape of a dead key — HTTP 200, and only the body says no (measured). */
    private val deadKeyJson =
        """{"status":"error","ok":false,"message":"Key existiert nicht oder ist deaktiviert"}"""

    private val feedParams = mutableMapOf<String, String>()

    private fun berlin(radiusMeters: Int = 5_000) = FuelStationsQuery(
        latitude = 52.521,
        longitude = 13.438,
        countryCode = "DE",
        radiusMeters = radiusMeters,
    )

    private fun source(
        feed: String = feedJson,
        feedStatus: HttpStatusCode = HttpStatusCode.OK,
        apiKey: String = KEY,
    ): GermanyFuelStationsSource {
        val engine = MockEngine { request ->
            request.url.parameters.names().forEach { name ->
                request.url.parameters[name]?.let { feedParams[name] = it }
            }
            if (feedStatus == HttpStatusCode.OK) {
                respond(feed, feedStatus, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respondError(feedStatus)
            }
        }
        return GermanyFuelStationsSource(HttpClient(engine)) { apiKey }
    }

    @Test
    fun `should_answerForGermanyOnly`() {
        val source = source()
        assertTrue(source.covers("DE"))
        assertTrue(source.covers("de"))
        assertFalse(source.covers("FR"))
        assertFalse(source.covers(""))
    }

    @Test
    fun `should_notClaimGermany_when_theKeyIsBlank`() {
        // ⛔ A keyless build (anyone cloning the public repo) must read as "Germany has no feed",
        // never as "Germany's feed is down": unclaimed, the registry drops to OSM without marking
        // a fallback that would apologise for a feed that was never askable.
        assertFalse(source(apiKey = "").claims(berlin()))
        assertTrue(source().claims(berlin()))
    }

    @Test
    fun `should_askInKilometres_when_theQuerySpeaksMetres`() = runTest {
        source().stationsNear(berlin(radiusMeters = 5_000)).getOrThrow()

        // The same parameter is metres in France — the mismatch would be silent: 5.000 km is
        // simply clamped and the answer reads like a busier map, never like an error.
        assertEquals("5.0", feedParams.getValue("rad"))
        assertEquals("dist", feedParams.getValue("sort"))
        assertEquals("all", feedParams.getValue("type"))
        assertEquals(KEY, feedParams.getValue("apikey"))
    }

    @Test
    fun `should_wearTheBrandAsName_when_theFeedNamesLikeAnAddress`() = runTest {
        val result = source().stationsNear(berlin()).getOrThrow()

        // `name` on the second row is "Berlin, Wittestr. 16" — an address wearing a name's field.
        // The brand is what reads like one, and only 2 of 347 measured brands came back blank.
        assertEquals("ARAL", result.stations[0].name)
        assertEquals("HEM", result.stations[1].name)
        assertEquals("Holzmarktstraße 12/14", result.stations[0].address)
    }

    @Test
    fun `should_preferE5ForPetrol_and_fallBackToE10_when_e5IsNull`() = runTest {
        val result = source().stationsNear(berlin()).getOrThrow()

        // Measured coverage decided the order: E5 at 344/347 and 130/131, and every petrol car
        // burns E5. The HEM row is the real fallback case.
        assertEquals(2.329, result.stations[0].prices.getValue(FuelType.GASOLINE).perUnit.amount)
        assertEquals(2.279, result.stations[1].prices.getValue(FuelType.GASOLINE).perUnit.amount)
        assertEquals(2.399, result.stations[0].prices.getValue(FuelType.DIESEL).perUnit.amount)
    }

    @Test
    fun `should_leaveANullFuelUnknown_when_theFeedCannotStateANegative`() = runTest {
        val result = source().stationsNear(berlin()).getOrThrow()

        // `diesel: null` is ambiguous — "does not sell" and "did not report" look identical in
        // MTS-K — so the station may not be hidden for it.
        val hem = result.stations[1]
        assertNull(hem.prices[FuelType.DIESEL])
        assertEquals(FuelAvailability.UNKNOWN, hem.availabilityOf(FuelType.DIESEL))
        assertFalse(hem.isKnownNotToSell(FuelType.DIESEL))
    }

    @Test
    fun `should_stampNothing_and_declareLivePrices_when_theFeedGuaranteesRecencyByLaw`() = runTest {
        val result = source().stationsNear(berlin()).getOrThrow()

        // Neither the response nor any price carries an hour — MTS-K's near-real-time obligation
        // IS the freshness fact, and it rides the flag instead of an invented stamp.
        assertNull(result.reportedAt)
        assertTrue(result.stations.flatMap { it.prices.values }.all { it.reportedAtMs == null })
        assertTrue(result.pricesAreLive)
        assertEquals(GermanyFuelStationsSource.SOURCE, result.source)
    }

    @Test
    fun `should_fail_when_theKeyIsDeadBehindA200`() = runTest {
        // ⛔ Measured: a dead key answers HTTP 200 with `ok:false`. Gating on the status code would
        // hand the lane an empty priced list instead of letting the registry fall back.
        val result = source(feed = deadKeyJson).stationsNear(berlin())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Key existiert"))
    }

    @Test
    fun `should_fail_when_theFeedIsDown`() = runTest {
        val result = source(feedStatus = HttpStatusCode.ServiceUnavailable).stationsNear(berlin())

        // A failure, never an empty list — it is what lets the registry fall back to OSM instead
        // of announcing "no stations in Germany".
        assertTrue(result.isFailure)
    }

    private companion object {
        const val KEY = "00000000-0000-0000-0000-000000000002"
    }
}
