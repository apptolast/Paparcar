package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.rndeveloper.paparcar.presentation.home.model.FuelLaneSort

/** [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001] */
class NearbyOrderingTest {

    private val user = GpsPoint(40.4168, -3.7038, 10f, 0L, 0f)

    /** [northMeters] north of the user, roughly — enough to order by. */
    private fun latNorth(northMeters: Double) = user.latitude + northMeters / METERS_PER_DEGREE

    private fun station(id: String, northMeters: Double, gasoline: Double? = null, askedFrom: Double = 0.0) =
        NearbyFuelStation(
            station = FuelStation(
                id = id, name = id, address = null, town = null,
                latitude = latNorth(northMeters), longitude = user.longitude,
                prices = gasoline?.let { mapOf(FuelType.GASOLINE to StationPrice(Money(it, "EUR"))) } ?: emptyMap(),
            ),
            // What the use case measured: from where it was ASKED, not from the user.
            distanceMeters = askedFrom,
            stationKey = "test|$id",
            source = "test",
            price = null,
        )

    private fun parking(id: String, northMeters: Double, askedFrom: Double = 0.0) = NearbyPublicParking(
        parking = PublicParking(
            id = id, name = id, operator = null, kind = PublicParkingKind.UNDERGROUND,
            latitude = latNorth(northMeters), longitude = user.longitude,
        ),
        distanceMeters = askedFrom,
    )

    @Test
    fun should_listPricedStationsFirst_thenNearestFirst_when_theCarHasAFuel() {
        val shown = NearbyOrdering.fuelStations(
            stations = listOf(
                station("near-unpriced", northMeters = 100.0),
                station("far-priced", northMeters = 3_000.0, gasoline = 1.5),
                station("mid-priced", northMeters = 1_000.0, gasoline = 1.6),
                station("mid-unpriced", northMeters = 900.0),
            ),
            pricedFor = FuelType.GASOLINE,
            user = user,
        )

        // ⛔ `far-priced` encabeza aunque esté a 3 km: es la MÁS BARATA, y desde la review de #37 esa
        // sube al principio — un badge que hay que buscar con scroll no es un badge. Lo demás no se
        // mueve: con precio primero, y dentro de cada grupo la distancia.
        // [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001]
        assertEquals(listOf("far-priced", "mid-priced", "near-unpriced", "mid-unpriced"), shown.map { it.station.id })
        assertEquals(1.5, shown.first().price?.perUnit?.amount, "priced for the car being driven")
    }

    @Test
    fun should_priceAndRankAHybridLikeAPetrolCar_when_theForecourtQuotesOnlyProducts() {
        // «Priced for the car being driven» includes the car that buys petrol without burning only
        // petrol. Asking with HYBRID priced nothing, so a list of priced forecourts collapsed into
        // one flat nearest-first run with no figures at all.
        // [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
        val shown = NearbyOrdering.fuelStations(
            stations = listOf(
                station("near-unpriced", northMeters = 100.0),
                station("far-priced", northMeters = 3_000.0, gasoline = 1.5),
            ),
            pricedFor = FuelType.HYBRID,
            user = user,
        )

        assertEquals(listOf("far-priced", "near-unpriced"), shown.map { it.station.id })
        assertEquals(1.5, shown.first().price?.perUnit?.amount)
    }

    @Test
    fun should_orderByDistanceAlone_andPriceNothing_when_nobodyDeclaredTheFuel() {
        val shown = NearbyOrdering.fuelStations(
            stations = listOf(station("far", 3_000.0, gasoline = 1.5), station("near", 100.0)),
            pricedFor = null,
            user = user,
        )

        assertEquals(listOf("near", "far"), shown.map { it.station.id })
        assertTrue(shown.all { it.price == null })
    }

    @Test
    fun should_measureFromTheUser_notFromWhereTheAskWasMade() {
        // Asked around a camera 5 km away: the use case said 5.000 m for both. The rows must speak
        // from the user, like a free-spot row.
        val shown = NearbyOrdering.publicParkings(
            parkings = listOf(parking("b", 800.0, askedFrom = 5_000.0), parking("a", 200.0, askedFrom = 5_000.0)),
            user = user,
        )

        assertEquals(listOf("a", "b"), shown.map { it.parking.id })
        assertEquals(200.0, shown.first().distanceMeters, DISTANCE_TOLERANCE_M)
        assertEquals(800.0, shown.last().distanceMeters, DISTANCE_TOLERANCE_M)
    }

    @Test
    fun should_keepTheAskedDistance_when_thereIsNoFixYet() {
        val shown = NearbyOrdering.publicParkings(
            parkings = listOf(parking("b", 0.0, askedFrom = 900.0), parking("a", 0.0, askedFrom = 300.0)),
            user = null,
        )

        assertEquals(listOf("a", "b"), shown.map { it.parking.id })
        assertEquals(300.0, shown.first().distanceMeters)
        assertNull(NearbyOrdering.fuelStations(emptyList(), FuelType.DIESEL, null).firstOrNull())
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_195.0
        const val DISTANCE_TOLERANCE_M = 2.0
    }

    // ── El orden que elige el usuario [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001] ─────────

    @Test
    fun should_listTheCheapestFirst_when_theUserSortsByPrice() {
        val shown = NearbyOrdering.fuelStations(
            stations = listOf(
                station("near-pricey", northMeters = 100.0, gasoline = 1.9),
                station("far-cheap", northMeters = 3_000.0, gasoline = 1.5),
                station("mid", northMeters = 1_000.0, gasoline = 1.7),
                station("unpriced", northMeters = 200.0),
            ),
            pricedFor = FuelType.GASOLINE,
            user = user,
            sort = FuelLaneSort.CHEAPEST,
        )

        assertEquals(
            listOf("far-cheap", "mid", "near-pricey", "unpriced"),
            shown.map { it.station.id },
            "por precio manda el precio; las que no tienen se van al final, ordenadas por distancia",
        )
    }

    // ── Plazas [HOME-A-LANE-FRAMES-ITS-SETTLED-FIRST-ROW-001] ──────────────────────────────────

    private fun spot(id: String, northMeters: Double, status: SpotStatus = SpotStatus.CONFIRMED) = Spot(
        id = id,
        location = GpsPoint(latNorth(northMeters), user.longitude, 10f, 0L, 0f),
        reportedBy = "u",
        status = status,
    )

    @Test
    fun should_listTheNearestSpotFirst_when_thereIsAFix() {
        // ⛔ EL TESTIGO de la issue #124: el carril del PRODUCTO era el único sin criterio de
        // lectura, así que su primera fila —y lo que encuadra la cámara al entrar— era la plaza que
        // el feed hubiera contestado primero.
        val shown = NearbyOrdering.spots(
            spots = listOf(spot("far", 2_000.0), spot("near", 80.0), spot("mid", 600.0)),
            user = user,
        )

        assertEquals(listOf("near", "mid", "far"), shown.map { it.id })
    }

    @Test
    fun should_keepAnUnconfirmedSpotLast_when_itIsTheNearestOne() {
        // La distancia ordena DENTRO del grupo; de qué nos fiamos sigue mandando por encima.
        val shown = NearbyOrdering.spots(
            spots = listOf(
                spot("unconfirmed-next-door", 10.0, SpotStatus.PROVISIONAL),
                spot("confirmed-far", 3_000.0),
            ),
            user = user,
        )

        assertEquals(listOf("confirmed-far", "unconfirmed-next-door"), shown.map { it.id })
    }

    @Test
    fun should_keepAReleasingSpotBehindEveryFreeOne_when_itIsTheNearestOne() {
        // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] The one row on this lane where the car is still
        // in the space. A lane that framed it first — and the lane DOES frame its first row — would
        // send somebody to an occupied kerb, which costs the one thing the app cannot give back.
        val shown = NearbyOrdering.spots(
            spots = listOf(
                spot("releasing-next-door", 10.0, SpotStatus.RELEASING),
                spot("unconfirmed-mid", 900.0, SpotStatus.PROVISIONAL),
                spot("confirmed-far", 3_000.0),
            ),
            user = user,
        )

        assertEquals(
            listOf("confirmed-far", "unconfirmed-mid", "releasing-next-door"),
            shown.map { it.id },
        )
    }

    @Test
    fun should_keepTheSourceOrder_when_thereIsNoFixToMeasureFrom() {
        // Sin fix no hay «más cercana»: no se inventa un orden, se conserva el de la fuente — y el
        // agrupado por estado, que no necesita medir nada, se aplica igual.
        val shown = NearbyOrdering.spots(
            spots = listOf(
                spot("b", 2_000.0),
                spot("provisional", 10.0, SpotStatus.PROVISIONAL),
                spot("a", 80.0),
            ),
            user = null,
        )

        assertEquals(listOf("b", "a", "provisional"), shown.map { it.id })
    }

    /** Los surtidores se copian el precio a la milésima: el empate lo rompe la distancia. */
    @Test
    fun should_breakAPriceTie_byDistance_when_sortingByPrice() {
        val shown = NearbyOrdering.fuelStations(
            stations = listOf(
                station("far-same", northMeters = 3_000.0, gasoline = 1.5),
                station("near-same", northMeters = 300.0, gasoline = 1.5),
            ),
            pricedFor = FuelType.GASOLINE,
            user = user,
            sort = FuelLaneSort.CHEAPEST,
        )

        assertEquals(listOf("near-same", "far-same"), shown.map { it.station.id })
    }
}
