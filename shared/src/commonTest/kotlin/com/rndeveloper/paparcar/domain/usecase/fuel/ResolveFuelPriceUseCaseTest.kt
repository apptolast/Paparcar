package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelPriceOrigin
import com.rndeveloper.paparcar.domain.fuel.AverageFuelPriceTable
import com.rndeveloper.paparcar.domain.fuel.StaticAverageFuelPrices
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * The ORDER is the product rule, so the order is what these pin: what the user typed beats a pump,
 * a pump beats the national average, and the average beats nothing. [FUEL-PRICE-RESOLVER-001]
 */
class ResolveFuelPriceUseCaseTest {

    private val resolve = ResolveFuelPriceUseCase()

    private fun vehicle(
        fuel: FuelType? = FuelType.GASOLINE,
        litrePrice: Money? = null,
        kwhPrice: Money? = null,
    ) = Vehicle(
        id = "v-1",
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        fuelType = fuel,
        fuelPricePerLiter = litrePrice,
        energyPricePerKwh = kwhPrice,
    )

    @Test
    fun `should_preferWhatTheUserTyped_overEverythingElse`() {
        val price = resolve.litre(
            vehicle = vehicle(litrePrice = Money(1.30, "EUR")),
            countryCode = "ES",
            stationPrice = Money(1.49, "EUR"),
        )
        assertEquals(1.30, price?.perUnit?.amount)
        assertEquals(FuelPriceOrigin.UserOverride, price?.source)
    }

    @Test
    fun `should_preferAPump_overTheNationalAverage`() {
        val price = resolve.litre(vehicle(), countryCode = "ES", stationPrice = Money(1.49, "EUR"))
        assertEquals(1.49, price?.perUnit?.amount)
        assertEquals(FuelPriceOrigin.Station, price?.source)
    }

    @Test
    fun `should_fallBackToTheNationalAverage_when_nothingBetterExists`() {
        val price = resolve.litre(vehicle(), countryCode = "ES")
        assertNotNull(price)
        assertEquals(FuelPriceOrigin.NationalAverage, price.source)
        assertEquals("EUR", price.perUnit.currency)
    }

    @Test
    fun `should_answerNothing_where_theTableHasNoIdea`() {
        // Outside the table the app does not know what fuel costs, and a drive priced in Madrid
        // euros because nothing better was known would be worse than no figure at all.
        //
        // ⛔ The subject used to be Japan, which the hand-written table had never heard of. The
        // generated one prices 161 countries INCLUDING Japan, so the old assertion was passing for
        // a reason that had stopped being true — a witness that no longer witnessed anything.
        // "ZZ" is reserved by ISO 3166 and can never be a country, so it stays absent by
        // construction rather than by luck. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
        assertNull(resolve.litre(vehicle(), countryCode = "ZZ"))
        assertNull(resolve.litre(vehicle(), countryCode = null))
    }

    @Test
    fun `should_priceEachEuroAreaCountryWithItsOwnFigure`() {
        // Ireland used to read a shared "EU" row because the hand-written table could not afford
        // fourteen more. It has its own now — and so do the other thirteen, which matters because
        // the real spread between them reached 0.80 €/L: Malta at 1.34 and Finland at 2.16 were
        // both being quoted the same flattened average.
        // [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
        val ireland = resolve.litre(vehicle(), countryCode = "IE")
        assertNotNull(ireland)
        assertEquals(FuelPriceOrigin.NationalAverage, ireland.source)

        val malta = assertNotNull(resolve.litre(vehicle(), countryCode = "MT")).perUnit.amount
        val finland = assertNotNull(resolve.litre(vehicle(), countryCode = "FI")).perUnit.amount
        assertTrue(
            malta != finland,
            "Malta and Finland are quoting the same figure — the shared euro row is back",
        )
    }

    @Test
    fun `should_haveNoLitrePrice_forAnElectricCar`() {
        // A litre price for an EV is a category error, not a missing figure.
        assertNull(resolve.litre(vehicle(fuel = FuelType.ELECTRIC), countryCode = "ES"))
        assertNotNull(resolve.kwh(vehicle(fuel = FuelType.ELECTRIC), countryCode = "ES"))
    }

    @Test
    fun `should_haveNoKwhPrice_forACarThatNeverPlugsIn`() {
        assertNull(resolve.kwh(vehicle(fuel = FuelType.GASOLINE), countryCode = "ES"))
        assertNull(resolve.kwh(vehicle(fuel = FuelType.DIESEL), countryCode = "ES"))
    }

    @Test
    fun `should_priceBothEnergies_forAPlugInHybrid`() {
        // The aggregate refuses to cost a PHEV with only one of the two, so the resolver has to
        // answer twice or the tile disappears. [STATS-CORE-001]
        val phev = vehicle(fuel = FuelType.PLUG_IN_HYBRID)
        assertNotNull(resolve.litre(phev, countryCode = "ES"))
        assertNotNull(resolve.kwh(phev, countryCode = "ES"))
    }

    @Test
    fun `should_answerNothing_when_theCarDoesNotSayWhatItBurns`() {
        assertNull(resolve.litre(vehicle(fuel = null), countryCode = "ES"))
        assertNull(resolve.kwh(vehicle(fuel = null), countryCode = "ES"))
    }

    @Test
    fun `should_priceEveryLocaleTheAppSpeaks`() {
        // A user reading the app in their own language and getting no spend figure would be the
        // table quietly excluding them. Nine locales ship; nine locales price.
        listOf("ES", "PT", "FR", "IT", "DE", "NL", "PL", "RO").forEach { country ->
            assertNotNull(
                AverageFuelPriceTable.Embedded.averageLitre(country, FuelType.GASOLINE),
                "$country has no petrol average",
            )
            assertNotNull(
                StaticAverageFuelPrices.averageKwh(country),
                "$country has no kWh average",
            )
        }
    }

    @Test
    fun `should_quoteEachCountryInItsOwnMoney`() {
        val table = AverageFuelPriceTable.Embedded
        assertEquals("EUR", table.averageLitre("ES", FuelType.GASOLINE)?.currency)
        assertEquals("PLN", table.averageLitre("PL", FuelType.GASOLINE)?.currency)
        assertEquals("RON", table.averageLitre("RO", FuelType.GASOLINE)?.currency)
    }

    /**
     * ⛔ The country list may only ever GROW. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
     *
     * Swapping a hand-written table for a generated one is exactly the change that can shrink
     * coverage without anyone noticing — a user in Croatia losing their spend figure produces no
     * error, just a tile that stops appearing. These are every country the OLD table could answer:
     * the eight it priced by name, plus the fourteen euro-area members that read its shared `EU`
     * row. All twenty-two must still answer, and now they answer with their own figures rather
     * than a flattened euro average.
     */
    @Test
    fun `should_keepPricingEveryCountryTheOldTableCould`() {
        val table = AverageFuelPriceTable.Embedded
        val pricedByName = listOf("ES", "PT", "FR", "IT", "DE", "NL", "PL", "RO")
        val readTheSharedEuroRow = listOf(
            "AT", "BE", "CY", "EE", "FI", "GR", "HR", "IE", "LT", "LU", "LV", "MT", "SI", "SK",
        )
        (pricedByName + readTheSharedEuroRow).forEach { country ->
            assertNotNull(
                table.averageLitre(country, FuelType.GASOLINE),
                "$country lost its petrol average in the swap",
            )
            assertNotNull(
                table.averageLitre(country, FuelType.DIESEL),
                "$country lost its diesel average in the swap",
            )
        }
    }

    /**
     * The embedded table is what answers with no network, so it has to BE there.
     *
     * A generator that failed silently would leave a valid, empty Kotlin file and every cost tile
     * in the app would go blank at once — with nothing to read in a stack trace, because an empty
     * map is not an error. This is the witness that the generated file actually carries countries.
     */
    @Test
    fun `should_shipAnEmbeddedTableThatIsNotEmpty`() {
        assertTrue(
            AverageFuelPriceTable.Embedded.rows.size > MIN_EMBEDDED_COUNTRIES,
            "the generated table carries only ${AverageFuelPriceTable.Embedded.rows.size} countries",
        )
    }

    /**
     * ⛔ Una media DICE cuándo se midió. [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]
     *
     * `AverageFuelPriceTable` llevaba su `asOf` desde el primer día y NADIE lo leía, así que la
     * tarjeta decía «precio medio de tu país» sin fecha — mientras el snapshot del binario envejece
     * con cada release que no se publica. Es la trampa exacta que dejó la tabla anterior un 20 % por
     * debajo del Ministerio: el dato no era malo, es que nadie miraba cuándo.
     */
    @Test
    fun `should_carryTheDateItWasMeasured_when_thePriceIsTheNationalAverage`() {
        val price = assertNotNull(resolve.litre(vehicle(), countryCode = "ES"))
        assertEquals(FuelPriceOrigin.NationalAverage, price.source)
        assertEquals(
            AverageFuelPriceTable.Embedded.asOf,
            price.measuredOn,
            "the average reached the screen without saying when it was measured",
        )
    }

    /**
     * ⛔ Y el precio del USUARIO no lleva fecha, porque no la tiene. Inventarle una sería peor que
     * no decir nada: convertiría «lo que pagas» en «lo que pagabas el día X», que nadie ha medido.
     */
    @Test
    fun `should_carryNoDate_when_theUserTypedThePriceThemselves`() {
        val own = vehicle().copy(fuelPricePerLiter = Money(1.70, "EUR"))
        val price = assertNotNull(resolve.litre(own, countryCode = "ES"))
        assertEquals(FuelPriceOrigin.UserOverride, price.source)
        assertNull(price.measuredOn, "the user's own price was given a measurement date")
    }

    // ── Una media nacional CADUCA [STATS-AN-ESTIMATE-NAMES-ITSELF-AND-ITS-AGE-001] ─────────────
    //
    // Las fechas se derivan del propio `asOf` de la tabla, no se escriben a mano: estos tests
    // prueban la REGLA de edad, no un calendario. (Los tests de arriba usan el reloj real — si la
    // tabla embebida llega a envejecer >180 días sin regenerarse, se pondrán rojos a propósito:
    // ese rojo dice «regenera el snapshot antes de publicar».)

    private fun resolverOn(date: LocalDate) = ResolveFuelPriceUseCase(today = { date })

    private val tableAsOf = LocalDate.parse(AverageFuelPriceTable.Embedded.asOf)

    @Test
    fun `should_stillPrice_when_theAverageIsInsideItsCeiling`() {
        val lastHonestDay = tableAsOf.plus(
            ResolveFuelPriceUseCase.MAX_NATIONAL_AVERAGE_AGE_DAYS,
            DateTimeUnit.DAY,
        )
        val price = resolverOn(lastHonestDay).litre(vehicle(), countryCode = "ES")
        assertNotNull(price)
        assertEquals(FuelPriceOrigin.NationalAverage, price.source)
    }

    @Test
    fun `should_refuseToPrice_when_theAverageOutlivedItsCeiling`() {
        // Un precio de hace medio año describe otro mercado: mejor ningún tile de gasto que uno
        // calculado con él — la misma negativa que ya damos sin precio.
        val dayAfter = tableAsOf.plus(
            ResolveFuelPriceUseCase.MAX_NATIONAL_AVERAGE_AGE_DAYS + 1,
            DateTimeUnit.DAY,
        )
        assertNull(resolverOn(dayAfter).litre(vehicle(), countryCode = "ES"))
    }

    @Test
    fun `should_expireTheKwhAverageByTheSameRule`() {
        val kwhAsOf = LocalDate.parse("${StaticAverageFuelPrices.KWH_VALID_AS_OF}-01")
        val dayAfter = kwhAsOf.plus(
            ResolveFuelPriceUseCase.MAX_NATIONAL_AVERAGE_AGE_DAYS + 1,
            DateTimeUnit.DAY,
        )
        assertNull(resolverOn(dayAfter).kwh(vehicle(fuel = FuelType.ELECTRIC), countryCode = "ES"))
    }

    @Test
    fun `should_neverExpireWhatTheUserTypedOrAPumpQuoted`() {
        // Sólo caduca el peldaño de la media: el precio del usuario es su palabra, y el de
        // gasolinera llega medido en el momento (su frescura la traerá FUEL-STATIONS-001).
        val longAfter = tableAsOf.plus(999, DateTimeUnit.DAY)
        val own = resolverOn(longAfter).litre(
            vehicle(litrePrice = Money(1.70, "EUR")),
            countryCode = "ES",
        )
        assertEquals(FuelPriceOrigin.UserOverride, own?.source)

        val pump = resolverOn(longAfter).litre(
            vehicle(),
            countryCode = "ES",
            stationPrice = Money(1.49, "EUR"),
        )
        assertEquals(FuelPriceOrigin.Station, pump?.source)
    }

    private companion object {
        /**
         * Comfortably below the 161 the generator produced, and far above the 22 the hand-written
         * table reached — so this catches a truncated or failed generation without breaking every
         * time a country drops out of the feed for a day.
         */
        const val MIN_EMBEDDED_COUNTRIES = 100
    }
}
