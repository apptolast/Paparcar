package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FollowedFuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelPriceSample
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * The test that matters here is the NEGATIVE one: a feed that has not refreshed must not produce a
 * movement. Italy carries 69% of its prices two to seven days old, so "yesterday's row differs from
 * today's" is a statement about our reading schedule, not about the pump.
 */
class EvaluateDailyPriceMoveUseCaseTest {

    private val useCase = EvaluateDailyPriceMoveUseCase()
    private val station = FollowedFuelStation(
        stationKey = "minetur|1234",
        source = "minetur",
        id = "1234",
        name = "Repsol Alcalá",
        latitude = 40.48,
        longitude = -3.36,
        followedAtMs = 1_000L,
    )

    private fun sample(
        day: Long,
        amount: Double,
        reportedAtMs: Long?,
        currency: String = "EUR",
        isSelf: Boolean? = null,
    ) = FuelPriceSample(
        stationKey = station.stationKey,
        fuelType = FuelType.DIESEL,
        perUnit = Money(amount, currency),
        reportedAtMs = reportedAtMs,
        observedAtMs = day * 86_400_000L,
        observedDay = day,
        isSelf = isSelf,
    )

    @Test
    fun should_reportADrop_when_theFeedStampMovedAndThePriceFell() {
        val moves = useCase(
            followed = listOf(station),
            fuelType = FuelType.DIESEL,
            samplesByStation = mapOf(
                station.stationKey to listOf(
                    sample(day = 100, amount = 1.659, reportedAtMs = 100_000L),
                    sample(day = 101, amount = 1.629, reportedAtMs = 200_000L),
                ),
            ),
        )

        assertEquals(1, moves.size)
        assertEquals(PriceMoveDirection.DOWN, moves.first().direction)
        assertEquals(-0.030, moves.first().deltaPerUnit, absoluteTolerance = 1e-9)
    }

    @Test
    fun should_reportARise_when_theFeedStampMovedAndThePriceRose() {
        val moves = useCase(
            followed = listOf(station),
            fuelType = FuelType.DIESEL,
            samplesByStation = mapOf(
                station.stationKey to listOf(
                    sample(day = 100, amount = 1.629, reportedAtMs = 100_000L),
                    sample(day = 101, amount = 1.679, reportedAtMs = 200_000L),
                ),
            ),
        )

        assertEquals(PriceMoveDirection.UP, moves.single().direction)
    }

    /** ⛔ THE rule of this verdict: an unchanged feed stamp is "no news", never "no change". */
    @Test
    fun should_reportNothing_when_theFeedStampDidNotMove() {
        val moves = useCase(
            followed = listOf(station),
            fuelType = FuelType.DIESEL,
            samplesByStation = mapOf(
                station.stationKey to listOf(
                    sample(day = 100, amount = 1.659, reportedAtMs = 100_000L),
                    // Same stamp, different number — a rounding or a re-publish, not a price move.
                    sample(day = 101, amount = 1.629, reportedAtMs = 100_000L),
                ),
            ),
        )

        assertTrue(moves.isEmpty())
    }

    /** A source that publishes no stamp at all cannot testify either way. */
    @Test
    fun should_reportNothing_when_eitherSampleCarriesNoStamp() {
        val moves = useCase(
            followed = listOf(station),
            fuelType = FuelType.DIESEL,
            samplesByStation = mapOf(
                station.stationKey to listOf(
                    sample(day = 100, amount = 1.659, reportedAtMs = null),
                    sample(day = 101, amount = 1.629, reportedAtMs = 200_000L),
                ),
            ),
        )

        assertTrue(moves.isEmpty())
    }

    @Test
    fun should_reportNothing_when_bothReadingsBelongToTheSameDay() {
        val moves = useCase(
            followed = listOf(station),
            fuelType = FuelType.DIESEL,
            samplesByStation = mapOf(
                station.stationKey to listOf(
                    sample(day = 101, amount = 1.659, reportedAtMs = 100_000L),
                    sample(day = 101, amount = 1.629, reportedAtMs = 200_000L),
                ),
            ),
        )

        assertTrue(moves.isEmpty())
    }

    @Test
    fun should_reportNothing_when_theMoveIsBelowTheThreshold() {
        val moves = useCase(
            followed = listOf(station),
            fuelType = FuelType.DIESEL,
            samplesByStation = mapOf(
                station.stationKey to listOf(
                    sample(day = 100, amount = 1.6590, reportedAtMs = 100_000L),
                    sample(day = 101, amount = 1.6570, reportedAtMs = 200_000L),
                ),
            ),
        )

        assertTrue(moves.isEmpty(), "0,2 c/l is arithmetic, not news")
    }

    /** ⛔ 1,70 PLN is not cheaper than 1,60 EUR, and no rate belongs in a verdict. */
    @Test
    fun should_reportNothing_when_theCurrencyChanged() {
        val moves = useCase(
            followed = listOf(station),
            fuelType = FuelType.DIESEL,
            samplesByStation = mapOf(
                station.stationKey to listOf(
                    sample(day = 100, amount = 1.659, reportedAtMs = 100_000L, currency = "EUR"),
                    sample(day = 101, amount = 1.200, reportedAtMs = 200_000L, currency = "PLN"),
                ),
            ),
        )

        assertTrue(moves.isEmpty())
    }

    /**
     * ⛔ The same argument as the currency one, one unit down.
     * [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
     *
     * An Italian forecourt that stops quoting `self` still quotes `servito`, and the gap is
     * measured up to 0,35 €/L — more than TEN times the threshold this verdict is tuned for. So
     * this is not a near-miss: unguarded it is the loudest notification the feature can send, and
     * it is about a price that never moved.
     */
    @Test
    fun should_reportNothing_when_thePumpChangedButThePriceDidNot() {
        val moves = useCase(
            followed = listOf(station),
            fuelType = FuelType.DIESEL,
            samplesByStation = mapOf(
                station.stationKey to listOf(
                    sample(day = 100, amount = 2.084, reportedAtMs = 100_000L, isSelf = true),
                    sample(day = 101, amount = 2.434, reportedAtMs = 200_000L, isSelf = false),
                ),
            ),
        )

        assertTrue(moves.isEmpty(), "a self reading and an attended one are two different questions")
    }

    /** And the mode staying put must not cost a real move its notification. */
    @Test
    fun should_reportADrop_when_thePumpStayedSelfAndThePriceFell() {
        val moves = useCase(
            followed = listOf(station),
            fuelType = FuelType.DIESEL,
            samplesByStation = mapOf(
                station.stationKey to listOf(
                    sample(day = 100, amount = 2.084, reportedAtMs = 100_000L, isSelf = true),
                    sample(day = 101, amount = 2.034, reportedAtMs = 200_000L, isSelf = true),
                ),
            ),
        )

        assertEquals(1, moves.size)
        assertEquals(PriceMoveDirection.DOWN, moves.single().direction)
    }

    @Test
    fun should_reportNothing_when_onlyOneReadingExists() {
        val moves = useCase(
            followed = listOf(station),
            fuelType = FuelType.DIESEL,
            samplesByStation = mapOf(
                station.stationKey to listOf(sample(day = 101, amount = 1.629, reportedAtMs = 200_000L)),
            ),
        )

        assertTrue(moves.isEmpty(), "the first day a station is followed has nothing to compare")
    }

    /** A move in a fuel this car cannot burn is not news for this driver. */
    @Test
    fun should_ignoreOtherFuels_when_theCarBurnsDiesel() {
        val petrol = sample(day = 100, amount = 1.500, reportedAtMs = 100_000L)
            .copy(fuelType = FuelType.GASOLINE)
        val petrolLater = sample(day = 101, amount = 1.400, reportedAtMs = 200_000L)
            .copy(fuelType = FuelType.GASOLINE)

        val moves = useCase(
            followed = listOf(station),
            fuelType = FuelType.DIESEL,
            samplesByStation = mapOf(station.stationKey to listOf(petrol, petrolLater)),
        )

        assertTrue(moves.isEmpty())
    }
}
