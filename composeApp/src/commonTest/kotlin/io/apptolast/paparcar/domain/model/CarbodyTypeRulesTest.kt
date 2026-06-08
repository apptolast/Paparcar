package io.apptolast.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CarbodyTypeRulesTest {

    @Test
    fun should_useStandardWidth_when_smallCar() {
        val rules = CarbodyType.HATCHBACK_SMALL.getParkingRules()
        assertEquals(2.20, rules.minPlazaWidthMeters, "small hatchback should require standard width")
        assertFalse(rules.requiresHighCeiling)
        assertEquals(ParkingAlertKey.STANDARD, rules.alertKey)
    }

    @Test
    fun should_useSuvWidth_when_mediumSuv() {
        val rules = CarbodyType.SUV_MEDIUM.getParkingRules()
        assertEquals(2.40, rules.minPlazaWidthMeters)
        assertFalse(rules.requiresHighCeiling)
        assertEquals(ParkingAlertKey.WIDE_CAR, rules.alertKey)
    }

    @Test
    fun should_useCommercialWidth_when_pickup() {
        val rules = CarbodyType.PICKUP.getParkingRules()
        assertEquals(2.50, rules.minPlazaWidthMeters)
    }

    @Test
    fun should_flagHighCeiling_when_anyVanHighBody() {
        listOf(CarbodyType.VAN_LIGHT, CarbodyType.VAN_COMMERCIAL, CarbodyType.PICKUP).forEach { body ->
            val rules = body.getParkingRules()
            assertTrue(rules.requiresHighCeiling, "$body must flag high ceiling")
            assertEquals(ParkingAlertKey.HIGH_CEILING, rules.alertKey)
        }
    }

    @Test
    fun should_warnLong_when_sedanOrFamily() {
        listOf(CarbodyType.SEDAN, CarbodyType.FAMILY_LONG).forEach { body ->
            val rules = body.getParkingRules()
            assertEquals(ParkingAlertKey.LONG_CAR, rules.alertKey, "$body should surface long-car alert")
        }
    }
}
