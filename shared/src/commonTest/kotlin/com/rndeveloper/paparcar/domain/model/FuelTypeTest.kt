package com.rndeveloper.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a powertrain buys at the pump. [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
 *
 * The witness for a rule that had been true in two private copies and absent from the one surface
 * where it was read by a user: the station list and the map capsule went blank for every hybrid
 * driver, in every country, because `prices[HYBRID]` matches nothing any feed emits.
 */
class FuelTypeTest {

    @Test
    fun should_buyPetrol_when_theCarIsAnyKindOfHybrid() {
        assertEquals(FuelType.GASOLINE, FuelType.HYBRID.pumpProduct)
        assertEquals(FuelType.GASOLINE, FuelType.PLUG_IN_HYBRID.pumpProduct)
    }

    @Test
    fun should_buyItself_when_theCarBurnsAProductAPumpSells() {
        assertEquals(FuelType.GASOLINE, FuelType.GASOLINE.pumpProduct)
        assertEquals(FuelType.DIESEL, FuelType.DIESEL.pumpProduct)
        assertEquals(FuelType.LPG_CNG, FuelType.LPG_CNG.pumpProduct)
    }

    @Test
    fun should_buyNoLiquid_when_theCarIsPurelyElectric() {
        // ⛔ Not mapped onto petrol to make a lookup succeed: an EV has no tank, and a litre price
        // beside it would be a figure about a pump it never visits.
        assertEquals(FuelType.ELECTRIC, FuelType.ELECTRIC.pumpProduct)
    }

    @Test
    fun should_landOnAProductAFeedCanQuote_when_askedByAnyFuelThatBurnsLitres() {
        // The property the station lane actually depends on: whatever a litre-burning car is, the
        // key it ends up asking a forecourt with is one of the three products every feed indexes.
        val sold = setOf(FuelType.GASOLINE, FuelType.DIESEL, FuelType.LPG_CNG)
        FuelType.entries.filter { it.usesLiters }.forEach { fuel ->
            assertTrue(
                fuel.pumpProduct in sold,
                "$fuel burns litres but asks the pump for ${fuel.pumpProduct}, which no feed quotes",
            )
        }
    }

    @Test
    fun should_beIdempotent_when_appliedTwice() {
        // Why it is safe at every car→key boundary instead of exactly one: the use case, the
        // ordering and the priced-fuel seam can all map without any of them double-counting.
        FuelType.entries.forEach { fuel ->
            assertEquals(fuel.pumpProduct, fuel.pumpProduct.pumpProduct, "$fuel")
        }
    }
}
