package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The figures card's product rules: WHICH figures exist and in what order. [STATS-UI-001]
 *
 * These are the rules the ficha promises — a figure below its threshold is absent, never a "0" or
 * a dash — so they live in a pure function instead of inside the composable that renders them.
 */
class VehicleStatsBlockTest {

    private fun stats(
        cost: Money? = null,
        fuelLiters: Double? = null,
        energyKwh: Double? = null,
        co2: Double? = null,
        avgDurationMs: Long? = null,
        peakHour: Int? = null,
        usualDay: Int? = null,
        street: String? = null,
        knownCount: Int = 0,
        autoCount: Int = 0,
        spotsReleased: Int = 0,
    ) = PeriodStats(
        parkings = 10,
        endedParkings = 10,
        distance = null,
        fuelLiters = fuelLiters,
        energyKwh = energyKwh,
        costEstimate = cost,
        co2Kg = co2,
        avgParkedDurationMs = avgDurationMs,
        peakHourBucket = peakHour,
        mostActiveDayOfWeek = usualDay,
        favoriteStreet = street,
        spotsReleased = spotsReleased,
        autoDetectedCount = autoCount,
        detectionKnownCount = knownCount,
    )

    @Test
    fun `should_showOnlySpotsShared_when_everyFigureIsBelowItsThreshold`() {
        // A brand-new car: nothing is significant yet. The block does not fill the gaps with
        // zeroes — it shows the one figure where zero is itself an answer.
        val keys = statTileKeys(stats())
        assertEquals(listOf(StatTileKey.SPOTS_SHARED), keys)
    }

    @Test
    fun `should_omitTheCostTile_until_aPriceExists`() {
        // FUEL-PRICES-001 has not landed, so the aggregate reports no cost. A "~0 €" would be a
        // figure nobody can back.
        val keys = statTileKeys(stats(co2 = 14.2, avgDurationMs = 3_600_000L))
        assertFalse(StatTileKey.COST in keys)
        assertTrue(StatTileKey.CO2 in keys)
    }

    @Test
    fun `should_leadWithSpend_when_itExists_becauseThatIsWhatFreeSees`() {
        // The first tile is the one a free user reads whole — so the order is a product decision,
        // not a layout accident.
        val keys = statTileKeys(
            stats(cost = Money(9.4, "EUR"), co2 = 14.2, avgDurationMs = 3_600_000L),
        )
        assertEquals(StatTileKey.COST, keys.first())
    }

    @Test
    fun `should_dropTheAutoDetectedTile_when_noSessionHasKnownProvenance`() {
        // detectionKnownCount == 0 is how the aggregate says "below the significance threshold";
        // a share of zero known sessions would be noise wearing a percentage.
        assertFalse(StatTileKey.AUTO_DETECTED in statTileKeys(stats(knownCount = 0)))
        assertTrue(StatTileKey.AUTO_DETECTED in statTileKeys(stats(knownCount = 5, autoCount = 4)))
    }

    @Test
    fun `should_keepSpotsShared_when_itIsZero_becauseZeroIsAnAnswer`() {
        assertTrue(StatTileKey.SPOTS_SHARED in statTileKeys(stats(spotsReleased = 0)))
    }

    @Test
    fun `should_listEveryFigure_inReadingOrder_when_allExist`() {
        val keys = statTileKeys(
            stats(
                cost = Money(9.4, "EUR"),
                co2 = 14.2,
                avgDurationMs = 24_000_000L,
                peakHour = 18,
                usualDay = 2,
                street = "Calle de Alcalá",
                knownCount = 12,
                autoCount = 9,
                spotsReleased = 5,
            ),
        )
        assertEquals(
            listOf(
                StatTileKey.COST,
                StatTileKey.CO2,
                StatTileKey.AVG_DURATION,
                StatTileKey.PEAK_HOUR,
                StatTileKey.USUAL_DAY,
                StatTileKey.USUAL_STREET,
                StatTileKey.AUTO_DETECTED,
                StatTileKey.SPOTS_SHARED,
            ),
            keys,
        )
    }
    /**
     * ⛔ El combustible del periodo aparece — y aparece como CANTIDAD, no como tasa.
     * [STATS-A-COMPUTED-LITRE-WITH-NO-READER-001]
     *
     * `fuelLiters` y `energyKwh` se calculaban en cada resolución del historial y NADIE los leía:
     * un grep sobre `presentation/` no devolvía una sola línea. No fallaba ningún cálculo, es que
     * no existía la superficie.
     */
    @Test
    fun `should_showTheFuelBurned_when_thePeriodHasMeasuredLitres`() {
        val keys = statTileKeys(stats(fuelLiters = 6.5))
        assertTrue(StatTileKey.FUEL in keys)
        assertFalse(StatTileKey.ENERGY in keys, "a petrol car draws no kWh")
    }

    @Test
    fun `should_showTheEnergyDrawn_when_theCarIsElectric`() {
        val keys = statTileKeys(stats(energyKwh = 12.4))
        assertTrue(StatTileKey.ENERGY in keys)
        assertFalse(StatTileKey.FUEL in keys, "an EV burns no litres")
    }

    /**
     * Un híbrido enchufable consume las DOS energías, y por eso son dos claves y no una: fundirlas
     * obligaría a inventar una unidad común para litros y kilovatios-hora.
     */
    @Test
    fun `should_showBothEnergies_forAPlugInHybrid`() {
        val keys = statTileKeys(stats(fuelLiters = 3.1, energyKwh = 8.0))
        assertTrue(StatTileKey.FUEL in keys)
        assertTrue(StatTileKey.ENERGY in keys)
    }

    @Test
    fun `should_showNoConsumption_when_noSessionCarriesAMeasuredRoute`() {
        val keys = statTileKeys(stats())
        assertFalse(StatTileKey.FUEL in keys)
        assertFalse(StatTileKey.ENERGY in keys)
    }

    /**
     * ⛔ El coste SIGUE encabezando. El tier gratuito ve UNA cifra, y es la primera de esta lista:
     * meter el combustible por delante le habría cambiado en silencio la única cifra accionable que
     * tiene. El combustible va detrás del coste y delante de las emisiones.
     */
    @Test
    fun `should_keepSpendLeading_when_theConsumptionTilesAppear`() {
        val keys = statTileKeys(
            stats(cost = Money(12.07, "EUR"), fuelLiters = 6.5, energyKwh = 8.0, co2 = 17.2),
        )
        assertEquals(
            listOf(StatTileKey.COST, StatTileKey.FUEL, StatTileKey.ENERGY, StatTileKey.CO2),
            keys.take(4),
        )
    }

}
