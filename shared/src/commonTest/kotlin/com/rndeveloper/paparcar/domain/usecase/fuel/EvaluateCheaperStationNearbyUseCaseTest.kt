package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
class EvaluateCheaperStationNearbyUseCaseTest {

    private val useCase = EvaluateCheaperStationNearbyUseCase()

    // ~40.4168 N, -3.7038 W (Madrid). 0,001° of latitude ≈ 111 m.
    private val hereLat = 40.4168
    private val hereLon = -3.7038

    private fun station(
        id: String,
        price: Double?,
        latOffset: Double = 0.0,
        reportedAtMs: Long? = 1_000L,
        name: String = "Station $id",
        currency: String = "EUR",
    ) = FuelStation(
        id = id,
        name = name,
        address = null,
        town = null,
        latitude = hereLat + latOffset,
        longitude = hereLon,
        prices = price?.let {
            mapOf(FuelType.DIESEL to StationPrice(Money(it, currency), reportedAtMs))
        }.orEmpty(),
    )

    private fun candidates(vararg stations: FuelStation): Map<String, FuelStation> =
        stations.associateBy { "minetur|${it.id}" }

    @Test
    fun should_measureAgainstTheFollowedStation_when_thereIsOne() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("mine", price = 1.699, name = "Mi gasolinera"),
                station("cheap", price = 1.629, latOffset = 0.005),
            ),
            referenceStationKeys = setOf("minetur|mine"),
        )

        assertEquals("cheap", verdict?.station?.id)
        assertEquals(CheaperReference.FOLLOWED, verdict?.reference)
        assertEquals("Mi gasolinera", verdict?.referenceName)
        assertEquals(0.070, verdict?.savingPerUnit ?: 0.0, absoluteTolerance = 1e-9)
    }

    /**
     * ⛔ The reference with nothing followed is the MEDIAN, never the dearest pump around — that
     * version fires every single day, because next to the worst forecourt in town everything is a
     * bargain.
     */
    @Test
    fun should_measureAgainstTheAreaMedian_when_nothingIsFollowed() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("a", price = 1.900),
                station("b", price = 1.700),
                station("c", price = 1.660),
            ),
            referenceStationKeys = emptySet(),
        )

        assertEquals(CheaperReference.AREA_MEDIAN, verdict?.reference)
        // Median of {1.660, 1.700, 1.900} = 1.700, so the saving is measured from there — not from
        // the 1.900 that would have made this a 24-cent "bargain".
        assertEquals(1.700, verdict?.referencePrice?.amount ?: 0.0, absoluteTolerance = 1e-9)
        assertEquals(0.040, verdict?.savingPerUnit ?: 0.0, absoluteTolerance = 1e-9)
    }

    @Test
    fun should_sayNothing_when_theSavingIsBelowTheThreshold() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("mine", price = 1.659),
                station("cheap", price = 1.649, latOffset = 0.005),
            ),
            referenceStationKeys = setOf("minetur|mine"),
        )

        assertNull(verdict, "1 c/l does not pay for the detour it would ask for")
    }

    @Test
    fun should_sayNothing_when_theOnlyCheaperPumpIsBeyondTheDetour() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("mine", price = 1.799),
                // ~5,5 km north.
                station("far", price = 1.599, latOffset = 0.05),
            ),
            referenceStationKeys = setOf("minetur|mine"),
        )

        assertNull(verdict)
    }

    /** A candidate quoted from an older reading than the reference proves nothing. */
    @Test
    fun should_sayNothing_when_theCandidatePriceIsStalerThanTheReference() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("mine", price = 1.799, reportedAtMs = 5_000L),
                station("stale", price = 1.599, latOffset = 0.005, reportedAtMs = 1_000L),
            ),
            referenceStationKeys = setOf("minetur|mine"),
        )

        assertNull(verdict)
    }

    @Test
    fun should_sayNothing_when_nothingAroundHasAPriceForThisFuel() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(station("a", price = null), station("b", price = null)),
            referenceStationKeys = emptySet(),
        )

        assertNull(verdict)
    }

    @Test
    fun should_neverProposeAFollowedStation_when_itIsItselfTheCheapest() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("mine", price = 1.599),
                station("other", price = 1.799, latOffset = 0.005),
            ),
            referenceStationKeys = setOf("minetur|mine"),
        )

        assertNull(verdict, "the cheapest pump around is already the one you use")
    }

    /** A border area can answer in two currencies, and the smaller number is not the lower price. */
    @Test
    fun should_compareWithinOneCurrency_when_theAnswerMixesTwo() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("eur1", price = 1.899, currency = "EUR"),
                station("eur2", price = 1.800, currency = "EUR"),
                station("eur3", price = 1.750, currency = "EUR", latOffset = 0.005),
                // Would be "cheapest" by raw number, and is a different money entirely.
                station("pln", price = 0.400, currency = "PLN", latOffset = 0.005),
            ),
            referenceStationKeys = emptySet(),
        )

        assertTrue(verdict != null && verdict.price.currency == "EUR")
        assertEquals("eur3", verdict.station.id)
    }
}
