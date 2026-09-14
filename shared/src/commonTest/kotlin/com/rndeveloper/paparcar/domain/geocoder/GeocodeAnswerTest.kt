package com.rndeveloper.paparcar.domain.geocoder

import com.rndeveloper.paparcar.domain.model.AddressInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What counts as an answer from a reverse geocode.
 * [FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001]
 *
 * The three cases sit together on purpose: the bug was that the first two were served as the third.
 */
class GeocodeAnswerTest {

    @Test
    fun `should_fail_whenTheLookupReturnedNothing`() {
        val answer = geocodeAnswer(null)

        assertTrue(answer.isFailure)
        assertIs<GeocodeUnanswered>(answer.exceptionOrNull())
    }

    @Test
    fun `should_fail_whenTheAddressNamesNothing`() {
        // ⛔ The shape a mute platform geocoder took: every field null, handed back as a success.
        // Downstream that is a place with no country, and the fuel lane routed past Spain's feed
        // and told the user Spain does not publish prices.
        val answer = geocodeAnswer(
            AddressInfo(street = null, city = null, region = null, country = null),
        )

        assertTrue(answer.isFailure)
        assertIs<GeocodeUnanswered>(answer.exceptionOrNull())
    }

    @Test
    fun `should_answer_whenTheAddressNamesAnything`() {
        // A partial address IS an answer: the caller that needs a province says so itself, and a
        // street with nothing else is exactly what most of the world geocodes to.
        val partial = AddressInfo(street = "Calle Autillo", city = null, region = null, country = null)

        assertEquals(partial, geocodeAnswer(partial).getOrNull())
    }

    @Test
    fun `should_answer_whenOnlyTheCountryCodeIsKnown`() {
        // The field the fuel registry routes on, and the one an enumeration of "the fields that
        // matter" would have left out. [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001]
        val onlyCountry = AddressInfo(
            street = null, city = null, region = null, country = null, countryCode = "es",
        )

        assertEquals(onlyCountry, geocodeAnswer(onlyCountry).getOrNull())
    }
}
