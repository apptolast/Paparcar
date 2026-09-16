@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.model.FuelType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The whole Italian adapter — request, `success` gate and parse — with no network.
 * [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
 *
 * The fixture is four real stations from the live zone search (Rome, 15/16-09-2026), chosen because
 * between them they carry every measured trap:
 *  - **36505** quotes petrol twice, `self` 2,084 and `servito` 2,434 — the **0,35 €/L** worst case
 *    that made this ticket exist, kept verbatim so a regression has to disagree with the field.
 *  - **35318** carries `Metano` (sold by the kilo) beside `GPL` (by the litre), and `Blue Diesel`
 *    dearer than its plain `Gasolio` — the two products that must never be quoted.
 *  - **61874** has only attended prices, so it exercises the marked branch.
 *  - **50790** is branded `PompeBianche`, which is not a brand.
 */
class ItalyFuelStationsSourceTest {

    private val feedJson = """
        {"success":true,"center":{"lat":41.903,"lng":12.495},"results":[
          {"id":36505,"name":"CAR SERVICE SAS","fuels":[
            {"id":122729580,"price":2.434,"name":"Benzina","fuelId":1,"isSelf":false},
            {"id":122729579,"price":2.084,"name":"Benzina","fuelId":1,"isSelf":true},
            {"id":122729582,"price":2.544,"name":"Gasolio","fuelId":2,"isSelf":false},
            {"id":122729581,"price":2.194,"name":"Gasolio","fuelId":2,"isSelf":true}],
           "location":{"lat":41.877360843032235,"lng":12.519165504156376},
           "insertDate":"2026-09-15T17:52:45+02:00","address":null,"brand":"Api-Ip",
           "distance":"3.398937757317914"},
          {"id":35318,"name":"CIRULLI CARBURANTI MULTISERVIZI","fuels":[
            {"id":122730543,"price":2.345,"name":"Benzina","fuelId":1,"isSelf":false},
            {"id":122730542,"price":2.13,"name":"Benzina","fuelId":1,"isSelf":true},
            {"id":122730541,"price":2.464,"name":"Gasolio","fuelId":2,"isSelf":false},
            {"id":122730540,"price":2.249,"name":"Gasolio","fuelId":2,"isSelf":true},
            {"id":122595003,"price":2.299,"name":"Metano","fuelId":3,"isSelf":false},
            {"id":122595002,"price":0.789,"name":"GPL","fuelId":4,"isSelf":false},
            {"id":122730539,"price":2.564,"name":"Blue Diesel","fuelId":20,"isSelf":false},
            {"id":122730538,"price":2.349,"name":"Blue Diesel","fuelId":20,"isSelf":true}],
           "location":{"lat":41.97603842811321,"lng":12.553422823548317},
           "insertDate":"2026-09-15T18:18:03+02:00","address":null,"brand":"AgipEni",
           "distance":"9.410913100892998"},
          {"id":61874,"name":"Roma viale delle Provincie, 220","fuels":[
            {"id":122600236,"price":2.149,"name":"Benzina","fuelId":1,"isSelf":false},
            {"id":122600237,"price":2.299,"name":"Gasolio","fuelId":2,"isSelf":false}],
           "location":{"lat":41.9316087,"lng":12.5233128},
           "insertDate":"2026-09-15T22:52:26+02:00","address":null,"brand":"Api-Ip",
           "distance":"3.9013568198298207"},
          {"id":50790,"name":"T24 NOMENTANA","fuels":[
            {"id":122736192,"price":2.099,"name":"Benzina","fuelId":1,"isSelf":true},
            {"id":122736191,"price":2.199,"name":"Gasolio","fuelId":2,"isSelf":true}],
           "location":{"lat":41.92338951987742,"lng":12.52034187316894},
           "insertDate":"2026-09-15T21:42:45+02:00","address":null,"brand":"PompeBianche",
           "distance":"3.0276505802396247"}]}
    """.trimIndent()

    /**
     * El punto colisionado REAL de Roma: cuatro instalaciones declarando `41.8947, 12.49348`, con
     * Trionfale y Valco San Paolo en extremos opuestos de la ciudad. La quinta fila está sola en su
     * coordenada y tiene que sobrevivir.
     */
    private val collidingJson = """
        {"success":true,"center":{"lat":41.903,"lng":12.495},"results":[
          {"id":1,"name":"UNION - BORGHESANO LUCCHESE","fuels":[
            {"id":1,"price":2.078,"name":"Benzina","fuelId":1,"isSelf":true}],
           "location":{"lat":41.8947,"lng":12.49348},
           "insertDate":"2026-09-15T17:52:45+02:00","address":null,"brand":"PompeBianche",
           "distance":"0.9"},
          {"id":2,"name":"UNION - VALCO S. PAOLO","fuels":[
            {"id":2,"price":2.117,"name":"Benzina","fuelId":1,"isSelf":true}],
           "location":{"lat":41.8947,"lng":12.49348},
           "insertDate":"2026-09-15T17:52:45+02:00","address":null,"brand":"PompeBianche",
           "distance":"0.9"},
          {"id":3,"name":"UNION - TRIONFALE","fuels":[
            {"id":3,"price":2.097,"name":"Benzina","fuelId":1,"isSelf":true}],
           "location":{"lat":41.8947,"lng":12.49348},
           "insertDate":"2026-09-15T17:52:45+02:00","address":null,"brand":"PompeBianche",
           "distance":"0.9"},
          {"id":4,"name":"PORTA MAGGIORE 8","fuels":[
            {"id":4,"price":2.089,"name":"Benzina","fuelId":1,"isSelf":true}],
           "location":{"lat":41.8947,"lng":12.49348},
           "insertDate":"2026-09-15T17:52:45+02:00","address":null,"brand":"PompeBianche",
           "distance":"0.9"},
          {"id":5,"name":"Api-Ip","fuels":[
            {"id":5,"price":2.129,"name":"Benzina","fuelId":1,"isSelf":true}],
           "location":{"lat":41.9001,"lng":12.5010},
           "insertDate":"2026-09-15T17:52:45+02:00","address":null,"brand":"Api-Ip",
           "distance":"0.7"}]}
    """.trimIndent()

    /** What a malformed ask really answers: HTTP 200, and `center` repeating the latitude twice. */
    private val refusalJson =
        """{"success":false,"center":{"lat":41.890546,"lng":41.890546},"results":[]}"""

    /** What an area with no forecourts answers — a real answer, and not the same thing. */
    private val emptyJson =
        """{"success":true,"center":{"lat":45.0,"lng":12.0},"results":[]}"""

    private var lastBody: String = ""

    private fun rome(radiusMeters: Int = 20_000) = FuelStationsQuery(
        latitude = 41.9028,
        longitude = 12.4964,
        countryCode = "IT",
        radiusMeters = radiusMeters,
    )

    private fun source(
        feed: String = feedJson,
        feedStatus: HttpStatusCode = HttpStatusCode.OK,
    ): ItalyFuelStationsSource {
        val engine = MockEngine { request ->
            lastBody = request.body.toByteArray().decodeToString()
            if (feedStatus == HttpStatusCode.OK) {
                respond(feed, feedStatus, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respondError(feedStatus)
            }
        }
        return ItalyFuelStationsSource(HttpClient(engine))
    }

    private suspend fun stations(radiusMeters: Int = 20_000) =
        source().stationsNear(rome(radiusMeters)).getOrThrow().stations.associateBy { it.id }

    @Test
    fun `should_answerForItalyOnly`() {
        val source = source()
        assertTrue(source.covers("IT"))
        assertTrue(source.covers("it"))
        assertFalse(source.covers("FR"))
        assertFalse(source.covers(""))
    }

    @Test
    fun `should_askWithOnePointAndEveryProduct_when_theFeedFiltersStationsAndNotPrices`() = runTest {
        source().stationsNear(rome(radiusMeters = 5_000)).getOrThrow()

        // ⛔ One point. A polygon answers `success:false` with an empty list, which on screen is
        // indistinguishable from "no forecourts near you".
        assertTrue(lastBody.contains(""""points":[{"lat":41.9028,"lng":12.4964}]"""), lastBody)
        // ⛔ `x-x`, because asking for petrol drops STATIONS without dropping a single price of the
        // ones that remain: measured 229 stations for `x-x` against 167 for `1-0`.
        assertTrue(lastBody.contains(""""fuelType":"x-x""""), lastBody)
    }

    @Test
    fun `should_askInKilometres_when_theQuerySpeaksMetres`() = runTest {
        source().stationsNear(rome(radiusMeters = 5_000)).getOrThrow()

        // Germany takes km and stops at 25; France takes metres and answers an empty list without
        // a unit. Three countries, three conventions, one line each.
        assertTrue(lastBody.contains(""""radius":5"""), lastBody)
    }

    @Test
    fun `should_capTheRadiusAtTen_and_sayItWasTruncated_when_moreIsAsked`() = runTest {
        val result = source().stationsNear(rome(radiusMeters = 25_000)).getOrThrow()

        // ⛔ Measured: 25 and 50 both come back as the ten-kilometre answer, farthest station at
        // 9,99 km, with no error and nothing in the body saying so. Asking beyond the cap is a
        // question the feed silently narrows, so the answer has to admit it was narrowed.
        assertTrue(lastBody.contains(""""radius":10"""), lastBody)
        assertTrue(result.isTruncated)
        assertFalse(source().stationsNear(rome(radiusMeters = 5_000)).getOrThrow().isTruncated)
    }

    @Test
    fun `should_quoteTheSelfPrice_when_aStationQuotesBothModes`() = runTest {
        val petrol = stations().getValue("36505").prices.getValue(FuelType.GASOLINE)

        // The measured worst case, kept as the real numbers: 2,434 attended against 2,084 self is
        // 0,35 €/L, and FuelWide was disqualified from this feature over one or two cents.
        assertEquals(2.084, petrol.perUnit.amount)
        assertEquals(true, petrol.isSelf)
        assertEquals(2.194, stations().getValue("36505").prices.getValue(FuelType.DIESEL).perUnit.amount)
    }

    @Test
    fun `should_markThePriceAttended_when_onlyServitoIsQuoted`() = runTest {
        val petrol = stations().getValue("61874").prices.getValue(FuelType.GASOLINE)

        // ⛔ Shown, not hidden: 99 of 229 Roman stations are in this state, and dropping them would
        // empty the lane. Shown MARKED, because the figure is not comparable to a self one.
        assertEquals(2.149, petrol.perUnit.amount)
        assertEquals(false, petrol.isSelf)
    }

    @Test
    fun `should_leaveTheModeNull_when_theSourceDoesNotDistinguish`() {
        // The four other adapters quote one price per pump and never raise the question. Null is
        // their answer, and it is what keeps the marked branch off every price in Europe.
        assertNull(
            GermanyStationParser
                .parse("""{"ok":true,"stations":[{"id":"a","lat":52.5,"lng":13.4,"e5":1.9}]}""")
                .single().prices.getValue(FuelType.GASOLINE).isSelf,
        )
    }

    @Test
    fun `should_quoteOnlyTheBaseProducts_when_theStationRegistersItsOwnPremium`() = runTest {
        val diesel = stations().getValue("35318").prices.getValue(FuelType.DIESEL)

        // ⛔ `Blue Diesel` (fuelId 20) at 2,349 self is a DIFFERENT product, not a cheaper way of
        // saying diesel. `fuelId` is an open vocabulary — thirty values across four cities, with
        // `HVO` alone under seven of them — so only the ministry's own categories are read.
        assertEquals(2.249, diesel.perUnit.amount)
    }

    @Test
    fun `should_notQuoteMethaneAsLpg_when_theStationSellsBoth`() = runTest {
        val gas = stations().getValue("35318").prices.getValue(FuelType.LPG_CNG)

        // ⛔ Methane is sold by the KILO (2,299) and GPL by the litre (0,789), and both would land
        // in this one slot. Quoting the first would make an LPG driver read a forecourt as three
        // times its neighbour's price — on a list whose only job is that comparison.
        assertEquals(0.789, gas.perUnit.amount)
        assertEquals(false, gas.isSelf)
    }

    @Test
    fun `should_wearTheBrandAsName_and_fallBackToTheStationName_when_theBrandIsNoBrand`() = runTest {
        val all = stations()

        assertEquals("Api-Ip", all.getValue("36505").name)
        // ⛔ `PompeBianche` is Italian for "white pumps" — the trade word for an unbranded
        // forecourt. 216 of 1.544 stations carry it, so taken as a brand it would put one word on
        // a seventh of the map.
        assertEquals("T24 NOMENTANA", all.getValue("50790").name)
    }

    @Test
    fun `should_stampEveryPriceWithTheStationHour_when_theFeedDatesNothingFiner`() = runTest {
        val result = source().stationsNear(rome()).getOrThrow()
        val station = result.stations.single { it.id == "36505" }

        // Verified against `registry/servicearea/{id}`, which DOES date each pump: across the three
        // products quoted here the worst spread inside one station was 24,0 hours in 45 sampled,
        // and 93% had none — the multi-year tail lives in the premiums that are dropped anyway.
        val expected = Instant.parse("2026-09-15T17:52:45+02:00").toEpochMilliseconds()
        assertTrue(station.prices.values.all { it.reportedAtMs == expected })
        // ⛔ And the RESPONSE carries none: a single hour for the list would be a claim no station
        // made — the same reason France leaves it null.
        assertNull(result.reportedAt)
        assertFalse(result.pricesAreLive)
    }

    @Test
    fun `should_orderByDistance_and_trimToTheRadius_when_theFeedCanOnlySortByPrice`() = runTest {
        // ⛔ `priceOrder` does what it says — measured, `asc` comes back sorted by price — and there
        // is no distance option at all, so the port's "nearest first" is this class's job.
        val ordered = source().stationsNear(rome()).getOrThrow().stations.map { it.id }
        assertEquals(listOf("50790", "36505", "61874", "35318"), ordered)

        // And the trim: 3,1 km has to be ASKED as 4 km — rounding down would hide forecourts the
        // question covered — and then cut back to the 3,1 that was actually asked. The next
        // station out sits at 3.399 m, so a missing trim shows up here and nowhere else.
        val near = source().stationsNear(rome(radiusMeters = 3_100)).getOrThrow().stations.map { it.id }
        assertTrue(lastBody.contains(""""radius":4"""), lastBody)
        assertEquals(listOf("50790"), near)
    }

    @Test
    fun `should_sayNothingAboutPumpsTheFeedDidNotMention`() = runTest {
        val station = stations().getValue("50790")

        // Like Germany, this feed cannot state a negative: an unmentioned pump is UNKNOWN, never
        // NOT_SOLD, so the station stays on the list for a driver it may well serve.
        assertEquals(FuelAvailability.UNKNOWN, station.availabilityOf(FuelType.LPG_CNG))
        assertFalse(station.isKnownNotToSell(FuelType.LPG_CNG))
        // ⛔ And no address is invented from the name: the zone search carries none, in 229 of 229.
        assertNull(station.address)
        assertNull(station.town)
    }

    @Test
    fun `should_fail_when_theFeedSaysSuccessFalse_despiteHttp200`() = runTest {
        val result = source(feed = refusalJson).stationsNear(rome())

        // ⛔ Third country, third spelling of the same lie — France's unitless radius answered 200
        // with `total_count: 0`, Tankerkönig's dead key answers 200 with `ok:false`. A failure is
        // what lets the registry fall back to OSM instead of reporting an empty Italy.
        assertTrue(result.isFailure)
    }

    @Test
    fun `should_answerEmpty_when_theAreaGenuinelyHasNoForecourts`() = runTest {
        val result = source(feed = emptyJson).stationsNear(rome())

        // ⛔ The asymmetry that matters: `success:true` with no results IS an answer, and turning
        // it into a failure would hand the middle of nowhere to OSM for no reason.
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().stations.isEmpty())
    }

    @Test
    fun `should_fail_when_theEndpointAnswersAnError`() = runTest {
        assertTrue(
            source(feedStatus = HttpStatusCode.ServiceUnavailable).stationsNear(rome()).isFailure,
        )
    }

    @Test
    fun `should_dropEveryStationOfAColldingPoint_when_theFeedRegistersSeveralAtOne`() = runTest {
        val result = source(feed = collidingJson).stationsNear(rome()).getOrThrow()

        // ⛔ Las CUATRO se van, no tres. Como mucho una está ahí y el feed no dice cuál, así que
        // quedarse con una es una moneda al aire que se lee como una fila normal y miente 3 de
        // cada 4 veces. Visto en device: tres UNION distintas a 932 m exactos.
        assertEquals(listOf("5"), result.stations.map { it.id })
        assertEquals("Api-Ip", result.stations.single().name)
    }

    @Test
    fun `should_keepAStationThatSharesItsPointWithNobody`() = runTest {
        // El testigo del otro lado: sin esto, "no colisiona" y "la fuente devolvió una sola" se
        // confunden, y un filtro que se llevara TODO pasaría el test de arriba igualmente.
        val result = source().stationsNear(rome()).getOrThrow()

        assertEquals(4, result.stations.size, "las cuatro del fixture normal tienen punto propio")
    }

    @Test
    fun `should_nameItsSource_when_threeFeedsShareOneCacheTable`() = runTest {
        // ⛔ Station ids are unique only WITHIN a feed, and the disk keys rows by `source|id`.
        assertEquals("it-osservaprezzi", source().stationsNear(rome()).getOrThrow().source)
    }
}
