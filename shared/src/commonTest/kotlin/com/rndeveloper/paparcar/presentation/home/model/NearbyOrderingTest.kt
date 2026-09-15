package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

        assertEquals(listOf("mid-priced", "far-priced", "near-unpriced", "mid-unpriced"), shown.map { it.station.id })
        assertEquals(1.6, shown.first().price?.perUnit?.amount, "priced for the car being driven")
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
}
