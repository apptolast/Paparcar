package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.model.FuelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wire format of OpenVan.camp, pinned. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
 *
 * ⛔ Every fixture below is a **shape measured against the live feed on 09-09-2026**, not one
 * invented to make the parser look right. That distinction is the whole reason these tests are
 * worth anything: the gallon rows, the `is_excluded` flag and the four-month-old Micronesia row all
 * exist in the real payload, and none of them would have been guessed.
 */
class OpenVanFuelPricesParserTest {

    /**
     * ⛔ The trap with no symptom. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
     *
     * Fourteen countries quote per gallon. Reading the US row as litres would price every American
     * drive at 3.79× the truth and never throw, never log, never look wrong — just be wrong. These
     * are the feed's own numbers: 4.1053 USD/gallon is 1.0845 USD/litre.
     */
    @Test
    fun `should_convertGallonsToLitres_when_theCountryDoesNotQuotePerLitre`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(
                """"US": ${country("USD", "gallon", gasoline = 4.1053, diesel = 5.5807)}""",
            ),
        ).getOrThrow()

        val litre = assertNotNull(table.averageLitre("US", FuelType.GASOLINE))
        assertEquals(1.0845, litre.amount, ABSOLUTE_TOLERANCE)
        assertEquals("USD", litre.currency)
    }

    /** The imperial gallon is a THIRD unit, and it is 20% bigger than the US one. */
    @Test
    fun `should_convertImperialGallons_when_theCountryUsesThem`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(""""KY": ${country("KYD", "imperial_gallon", gasoline = 5.7, diesel = 6.24)}"""),
        ).getOrThrow()

        val litre = assertNotNull(table.averageLitre("KY", FuelType.GASOLINE))
        assertEquals(5.7 / 4.54609, litre.amount, ABSOLUTE_TOLERANCE)
    }

    /**
     * ⛔ A unit we do not know is DROPPED, never assumed to be litres.
     *
     * This is the difference between a feed that adds a unit one day and a US-sized error appearing
     * in a country nobody was watching. Silence is the safe answer; a guess is not.
     */
    @Test
    fun `should_dropTheCountry_when_theUnitIsOneWeDoNotKnow`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(
                """"XX": ${country("USD", "barrel", gasoline = 90.0, diesel = 95.0)}""",
                """"ES": ${country("EUR", "liter", gasoline = 1.8445, diesel = 1.8544)}""",
            ),
        ).getOrThrow()

        assertNull(table.averageLitre("XX", FuelType.GASOLINE))
        assertNotNull(table.averageLitre("ES", FuelType.GASOLINE))
    }

    /** `is_excluded` is the feed distrusting its own row. Six countries carried it on the day. */
    @Test
    fun `should_dropTheCountry_when_theFeedMarksItExcluded`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(
                """"SN": ${country("XOF", "liter", gasoline = 990.0, diesel = 900.0, excluded = true)}""",
                """"ES": ${country("EUR", "liter", gasoline = 1.8445, diesel = 1.8544)}""",
            ),
        ).getOrThrow()

        assertNull(table.averageLitre("SN", FuelType.GASOLINE))
        assertNotNull(table.averageLitre("ES", FuelType.GASOLINE))
    }

    /**
     * The real stale row: Micronesia, fetched 2026-05-02 while the other 167 were same-day.
     *
     * A price with a four-month-old date is not a current price, and quoting it dated "today"
     * would be the exact dishonesty the old hand-written table fell into.
     */
    @Test
    fun `should_dropTheCountry_when_itsOwnRowIsMonthsBehindTheRest`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(
                """"FM": ${country("USD", "liter", gasoline = 1.2, diesel = 1.3, fetchedAt = "2026-05-02T11:14:47+03:00")}""",
                """"ES": ${country("EUR", "liter", gasoline = 1.8445, diesel = 1.8544)}""",
            ),
        ).getOrThrow()

        assertNull(table.averageLitre("FM", FuelType.GASOLINE))
        assertNotNull(table.averageLitre("ES", FuelType.GASOLINE))
    }

    /** Prices come in the country's own money, and nothing here converts. Poland quotes złoty. */
    @Test
    fun `should_keepEachCountryInItsOwnCurrency`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(
                """"PL": ${country("PLN", "liter", gasoline = 7.0163, diesel = 7.7794)}""",
                """"TR": ${country("TRY", "liter", gasoline = 76.8351, diesel = 88.8265)}""",
            ),
        ).getOrThrow()

        assertEquals("PLN", table.averageLitre("PL", FuelType.GASOLINE)?.currency)
        assertEquals(7.0163, table.averageLitre("PL", FuelType.GASOLINE)?.amount)
        assertEquals("TRY", table.averageLitre("TR", FuelType.DIESEL)?.currency)
    }

    /** LPG and CNG are one FuelType here, and LPG is the one a car actually finds. */
    @Test
    fun `should_readLpgForTheCombinedGasType`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(
                """"ES": ${country("EUR", "liter", gasoline = 1.8445, diesel = 1.8544, lpg = 1.1172)}""",
            ),
        ).getOrThrow()

        assertEquals(1.1172, table.averageLitre("ES", FuelType.LPG_CNG)?.amount)
    }

    /** A country that sells no LPG has NO LPG price — not a zero, and not a neighbour's figure. */
    @Test
    fun `should_answerNothing_when_theCountryDoesNotSellThatFuel`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(""""IE": ${country("EUR", "liter", gasoline = 1.8362, diesel = 1.9197)}"""),
        ).getOrThrow()

        assertNotNull(table.averageLitre("IE", FuelType.GASOLINE))
        assertNull(table.averageLitre("IE", FuelType.LPG_CNG))
    }

    /**
     * ⛔ An empty result is a FAILURE, not a table. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
     *
     * Installing zero countries over a good embedded table would blank every cost tile in the app
     * at once — the loudest possible symptom from the quietest possible bug. The repository only
     * replaces its table on success, so this refusal is what protects it.
     */
    @Test
    fun `should_fail_when_noCountrySurvivesParsing`() {
        val result = OpenVanFuelPricesParser.parse(
            payload(""""XX": ${country("USD", "barrel", gasoline = 90.0, diesel = 95.0)}"""),
        )
        assertTrue(result.isFailure)
    }

    /** `success: false` is the feed telling us not to use this payload. */
    @Test
    fun `should_fail_when_theFeedReportsFailure`() {
        val body = """
            {"success":false,"meta":{"updated_at":"$UPDATED_AT"},"data":{}}
        """.trimIndent()
        assertTrue(OpenVanFuelPricesParser.parse(body).isFailure)
    }

    /** An undated payload cannot date its own figures, and an undated price is invented. */
    @Test
    fun `should_fail_when_thePayloadCarriesNoDate`() {
        val body = """{"success":true,"data":{}}"""
        assertTrue(OpenVanFuelPricesParser.parse(body).isFailure)
    }

    /** Garbage in, failure out — never a half-built table. */
    @Test
    fun `should_fail_when_theBodyIsNotJson`() {
        assertTrue(OpenVanFuelPricesParser.parse("<html>502 Bad Gateway</html>").isFailure)
    }

    /** The table dates itself from the feed's own `updated_at`, so the UI can say when. */
    @Test
    fun `should_carryTheDateTheFeedReports`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(""""ES": ${country("EUR", "liter", gasoline = 1.8445, diesel = 1.8544)}"""),
        ).getOrThrow()
        assertEquals("2026-09-05", table.asOf)
    }

    private fun payload(vararg countries: String): String = """
        {
          "success": true,
          "data": { ${countries.joinToString(",")} },
          "meta": { "total_countries": ${countries.size}, "updated_at": "$UPDATED_AT" }
        }
    """.trimIndent()

    private fun country(
        currency: String,
        unit: String,
        gasoline: Double,
        diesel: Double,
        lpg: Double? = null,
        excluded: Boolean = false,
        fetchedAt: String = FETCHED_AT,
    ): String = """
        {
          "currency": "$currency",
          "unit": "$unit",
          "prices": { "gasoline": $gasoline, "diesel": $diesel, "lpg": ${lpg ?: "null"}, "cng": null },
          "fetched_at": "$fetchedAt",
          "is_excluded": $excluded
        }
    """.trimIndent()

    private companion object {
        const val UPDATED_AT = "2026-09-05 11:14:47"
        const val FETCHED_AT = "2026-09-05T11:14:47+03:00"

        /** Four decimals is what the feed carries; this is looser than that and tighter than a cent. */
        const val ABSOLUTE_TOLERANCE = 0.0001
    }
}
