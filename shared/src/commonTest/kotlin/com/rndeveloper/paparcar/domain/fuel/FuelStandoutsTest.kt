package com.rndeveloper.paparcar.domain.fuel

import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001] What a forecourt is allowed to claim.
 *
 * Every test here is about a claim the app must NOT make: the superlatives are cheap to write and
 * expensive to be wrong about, because the user checks them at the pump.
 */
class FuelStandoutsTest {

    private fun station(
        id: String,
        distance: Double,
        price: Double? = null,
        currency: String = "EUR",
    ) = NearbyFuelStation(
        station = FuelStation(
            id = id, name = id, address = null, town = null,
            latitude = 0.0, longitude = 0.0, prices = emptyMap(),
        ),
        distanceMeters = distance,
        stationKey = "test|$id",
        source = "test",
        price = price?.let { StationPrice(Money(it, currency)) },
    )

    @Test
    fun should_badge_the_cheapest_and_the_nearest_when_they_are_different_stations() {
        val standouts = listOf(
            station("near", distance = 100.0, price = 1.80),
            station("cheap", distance = 900.0, price = 1.55),
        ).fuelStandouts()

        assertEquals("test|cheap", standouts.cheapestKey)
        assertEquals("test|near", standouts.nearestKey)
        assertEquals(FuelStandoutBadge.CHEAPEST, standouts.badgeFor("test|cheap"))
        assertEquals(FuelStandoutBadge.NEAREST, standouts.badgeFor("test|near"))
    }

    @Test
    fun should_wear_one_badge_when_the_same_station_is_both() {
        val standouts = listOf(
            station("both", distance = 100.0, price = 1.55),
            station("other", distance = 900.0, price = 1.80),
        ).fuelStandouts()

        assertTrue(standouts.bothAreTheSame)
        assertEquals(FuelStandoutBadge.CHEAPEST_AND_NEAREST, standouts.badgeFor("test|both"))
        assertNull(standouts.badgeFor("test|other"), "the other one claims nothing")
    }

    @Test
    fun should_claim_nothing_when_there_is_a_single_station() {
        val standouts = listOf(station("only", distance = 100.0, price = 1.55)).fuelStandouts()

        assertNull(standouts.cheapestKey, "with one station there is no cheapest, just a station")
        assertNull(standouts.nearestKey)
    }

    @Test
    fun should_not_call_one_cheapest_when_two_charge_the_same() {
        val standouts = listOf(
            station("a", distance = 100.0, price = 1.55),
            station("b", distance = 900.0, price = 1.55),
        ).fuelStandouts()

        assertNull(standouts.cheapestKey, "a tie has no winner, and the user can check it at the pump")
        assertEquals("test|a", standouts.nearestKey, "distance still has one")
    }

    @Test
    fun should_not_compare_prices_across_currencies() {
        // Near a border the same answer can hold both feeds. 1.42 £/l is not cheaper than
        // 1.65 €/l because the number is smaller.
        val standouts = listOf(
            station("uk", distance = 900.0, price = 1.42, currency = "GBP"),
            station("es", distance = 100.0, price = 1.65, currency = "EUR"),
        ).fuelStandouts()

        assertNull(standouts.cheapestKey, "mixed currencies cannot be ranked")
        assertEquals("test|es", standouts.nearestKey, "metres are metres")
    }

    @Test
    fun should_still_find_the_cheapest_when_some_stations_have_no_price() {
        // The case the owner opened the issue for: most countries have no price feed at all.
        val standouts = listOf(
            station("unpriced", distance = 50.0),
            station("priced", distance = 400.0, price = 1.60),
            station("pricier", distance = 800.0, price = 1.90),
        ).fuelStandouts()

        assertEquals("test|priced", standouts.cheapestKey)
        assertEquals(
            "test|unpriced", standouts.nearestKey,
            "the nearest forecourt is the nearest one, priced or not — that is what the ordering " +
                "cannot say, since it puts priced stations first",
        )
    }
}
