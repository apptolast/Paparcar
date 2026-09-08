package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelPriceOrigin
import com.rndeveloper.paparcar.domain.fuel.StaticAverageFuelPrices
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        // Outside the table the app does not know what fuel costs, and a drive in Tokyo priced in
        // Madrid euros would be worse than no figure at all.
        assertNull(resolve.litre(vehicle(), countryCode = "JP"))
        assertNull(resolve.litre(vehicle(), countryCode = null))
    }

    @Test
    fun `should_readTheEuroAreaRow_forACountryTheTableDoesNotPriceOneByOne`() {
        // Ireland is not a row of its own, but a euro is a euro: an Irish user gets a figure
        // labelled "average" instead of a blank.
        val price = resolve.litre(vehicle(), countryCode = "IE")
        assertNotNull(price)
        assertEquals(FuelPriceOrigin.NationalAverage, price.source)
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
                StaticAverageFuelPrices.averageLitre(country, FuelType.GASOLINE),
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
        assertEquals("EUR", StaticAverageFuelPrices.averageLitre("ES", FuelType.GASOLINE)?.currency)
        assertEquals("PLN", StaticAverageFuelPrices.averageLitre("PL", FuelType.GASOLINE)?.currency)
        assertEquals("RON", StaticAverageFuelPrices.averageLitre("RO", FuelType.GASOLINE)?.currency)
    }
}
