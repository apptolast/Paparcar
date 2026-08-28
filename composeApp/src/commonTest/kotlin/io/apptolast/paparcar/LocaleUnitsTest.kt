package io.apptolast.paparcar

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocaleUnitsTest {

    @Test
    fun should_preferImperial_when_countrySignsRoadDistancesInMiles() {
        assertTrue(countryPrefersImperialUnits("US"))
        assertTrue(countryPrefersImperialUnits("GB"))
        assertTrue(countryPrefersImperialUnits("LR"))
        assertTrue(countryPrefersImperialUnits("MM"))
    }

    @Test
    fun should_preferImperial_when_countryCodeIsLowercase() {
        assertTrue(countryPrefersImperialUnits("us"))
    }

    @Test
    fun should_preferMetric_when_countryIsAnywhereElse() {
        assertFalse(countryPrefersImperialUnits("ES"))
        assertFalse(countryPrefersImperialUnits("FR"))
        assertFalse(countryPrefersImperialUnits("DE"))
    }

    @Test
    fun should_preferMetric_when_countryIsUnknownOrBlank() {
        assertFalse(countryPrefersImperialUnits(""))
        assertFalse(countryPrefersImperialUnits("ZZ"))
    }
}
