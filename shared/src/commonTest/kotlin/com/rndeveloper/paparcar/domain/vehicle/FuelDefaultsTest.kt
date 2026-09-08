package com.rndeveloper.paparcar.domain.vehicle

import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.VehicleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuelDefaultsTest {

    @Test
    fun `should_giveEveryFuelAPositiveDefault_when_askedForACar`() {
        FuelType.entries.forEach { fuel ->
            val default = FuelDefaults.defaultConsumptionPer100(fuel, VehicleType.CAR)
            assertTrue(default > 0f, "$fuel must have a usable default")
        }
    }

    @Test
    fun `should_useTheMotorcycleScale_when_theTypeSaysSo`() {
        assertEquals(
            FuelDefaults.MOTORCYCLE_LITERS_PER_100,
            FuelDefaults.defaultConsumptionPer100(FuelType.GASOLINE, VehicleType.MOTORCYCLE),
        )
    }

    @Test
    fun `should_giveASecondaryElectricFigure_onlyToThePlugInHybrid`() {
        FuelType.entries.forEach { fuel ->
            val secondary = FuelDefaults.defaultElectricConsumptionPer100(fuel)
            if (fuel.hasSecondaryElectric) {
                assertEquals(FuelDefaults.PHEV_KWH_PER_100, secondary)
            } else {
                assertNull(secondary, "$fuel carries one figure, not two")
            }
        }
    }

    @Test
    fun `should_agreeWithTheEnergyQuestions_when_theEnumAnswersThem`() {
        // The PHEV is the only both-energies shape; the EV is the only pure-electric one.
        assertEquals(listOf(FuelType.PLUG_IN_HYBRID), FuelType.entries.filter { it.hasSecondaryElectric })
        assertEquals(listOf(FuelType.ELECTRIC), FuelType.entries.filter { !it.usesLiters })
    }
}
