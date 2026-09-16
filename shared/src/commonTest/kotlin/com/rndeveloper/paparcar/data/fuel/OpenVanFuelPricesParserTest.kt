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

    // ── La unidad la declara el COMBUSTIBLE; la de la fila es el respaldo ────
    // [FUEL-A-FEED-QUOTES-EACH-FUEL-IN-ITS-OWN-UNIT-001]

    /**
     * ⛔ El fallo sin síntoma, un piso por debajo del galón: Brasil cotiza el GNC por **metro
     * cúbico** con la fila en litros, y no tiene GLP — así que el fallback `lpg ?: cng` se tragaba
     * `4,8098 BRL/m³` como si fuera el precio del litro. No es un error de escala, es de categoría:
     * ningún factor convierte un m³ de gas en un litro de líquido sin saber la presión.
     *
     * Y lo que NO puede pasar es que Brasil se caiga entera: su gasolina está perfectamente cotizada
     * en litros, y un gas sin precio no invalida el surtidor de al lado.
     */
    @Test
    fun `should_leaveTheGasUnpriced_when_theFeedQuotesItPerCubicMetre`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(
                """"BR": ${country(
                    "BRL", "liter", gasoline = 6.5664, diesel = 6.8884,
                    lpg = null, cng = 4.8098, units = """"cng": "m3"""",
                )}""",
            ),
        ).getOrThrow()

        assertNull(table.averageLitre("BR", FuelType.LPG_CNG), "un m³ de gas no es un litro")
        val gasoline = assertNotNull(table.averageLitre("BR", FuelType.GASOLINE))
        assertEquals(6.5664, gasoline.amount, ABSOLUTE_TOLERANCE, "la gasolina no se cae con el gas")
    }

    /** Tailandia vende el GLP por KILO. Mismo veredicto y por la misma razón: el kilo no es litro. */
    @Test
    fun `should_leaveTheGasUnpriced_when_theFeedQuotesItPerKilo`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(
                """"TH": ${country(
                    "THB", "liter", gasoline = 39.09, diesel = 39.84,
                    lpg = 25.87, units = """"lpg": "kg"""",
                )}""",
            ),
        ).getOrThrow()

        assertNull(table.averageLitre("TH", FuelType.LPG_CNG))
        assertNotNull(table.averageLitre("TH", FuelType.DIESEL))
    }

    /**
     * El caso de España, que es el que explica por qué el mapa existe y por qué hoy no muerde ahí:
     * declara `cng` en kilos **y** tiene GLP en litros, así que el fallback nunca llega al gas. Una
     * unidad rara en un combustible que no se lee no puede estropear nada.
     */
    @Test
    fun `should_priceTheLpg_when_onlyTheFuelItNeverReadsCarriesAnOddUnit`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(
                """"ES": ${country(
                    "EUR", "liter", gasoline = 1.8927, diesel = 1.8584,
                    lpg = 1.131, cng = 1.804, units = """"cng": "kg"""",
                )}""",
            ),
        ).getOrThrow()

        val lpg = assertNotNull(table.averageLitre("ES", FuelType.LPG_CNG))
        assertEquals(1.131, lpg.amount, ABSOLUTE_TOLERANCE)
    }

    /**
     * ⛔ La dirección importa: manda el COMBUSTIBLE, no la fila. Con la fila en litros y el diésel
     * declarado en galones, leer la fila dejaría el diésel ×3,79 alto — el fallo del ticket del
     * galón, escrito por el otro lado.
     */
    @Test
    fun `should_obeyTheFuelUnit_when_itContradictsTheRowUnit`() {
        val table = OpenVanFuelPricesParser.parse(
            payload(
                """"XX": ${country(
                    "USD", "liter", gasoline = 1.0, diesel = 3.785411784,
                    units = """"diesel": "gallon"""",
                )}""",
            ),
        ).getOrThrow()

        val diesel = assertNotNull(table.averageLitre("XX", FuelType.DIESEL))
        assertEquals(1.0, diesel.amount, ABSOLUTE_TOLERANCE, "el galón del diésel manda sobre el litro de la fila")
        assertEquals(1.0, assertNotNull(table.averageLitre("XX", FuelType.GASOLINE)).amount, ABSOLUTE_TOLERANCE)
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
        cng: Double? = null,
        /**
         * The feed's per-fuel `units` object, written as the JSON fragment it really is — e.g.
         * `"\"cng\": \"m3\""`. Blank ⇒ the key is absent, which is the shape of the 150 countries
         * that quote everything in the row's own unit.
         * [FUEL-A-FEED-QUOTES-EACH-FUEL-IN-ITS-OWN-UNIT-001]
         */
        units: String = "",
        excluded: Boolean = false,
        fetchedAt: String = FETCHED_AT,
    ): String = """
        {
          "currency": "$currency",
          "unit": "$unit",
          ${if (units.isBlank()) "" else """"units": { $units },"""}
          "prices": { "gasoline": $gasoline, "diesel": $diesel, "lpg": ${lpg ?: "null"}, "cng": ${cng ?: "null"} },
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
