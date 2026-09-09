package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.AverageFuelPriceTable
import com.rndeveloper.paparcar.domain.fuel.AverageFuelPricesSource
import com.rndeveloper.paparcar.domain.fuel.CountryFuelPrices
import com.rndeveloper.paparcar.domain.model.FuelType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The rung that must never need the network. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
 *
 * ⛔ The invariant under test is the one F5a refuses to trade: **no rung of the price ladder needs
 * the network at the moment it is asked.** Every test here is a way of asking "and what if there is
 * no network?" and getting a real figure back.
 */
class AverageFuelPricesRepositoryImplTest {

    /**
     * ⛔ The headline: a cold install, offline, still prices a drive.
     *
     * Nothing has been fetched and nothing can be. The answer is the snapshot compiled into the
     * binary, and it is a real number — not null, not zero, not a throw.
     */
    @Test
    fun `should_priceADrive_when_nothingHasEverBeenFetched`() {
        val repository = AverageFuelPricesRepositoryImpl(source = FailingSource())

        val price = assertNotNull(repository.current().averageLitre("ES", FuelType.GASOLINE))
        assertTrue(price.amount > 0.0)
        assertEquals("EUR", price.currency)
    }

    /**
     * ⛔ A failed refresh leaves the previous answer standing.
     *
     * A stale average is not an error state, it is the previous answer. Blanking the cost tile
     * because a fetch timed out would be strictly worse than the number it replaced — and that is
     * the failure mode this class exists to prevent.
     */
    @Test
    fun `should_keepQuotingTheEmbeddedTable_when_theRefreshFails`() = runTest {
        val repository = AverageFuelPricesRepositoryImpl(source = FailingSource())

        val afterRefresh = repository.refresh()

        assertEquals(AverageFuelPriceTable.Embedded.asOf, afterRefresh.asOf)
        assertNotNull(repository.current().averageLitre("ES", FuelType.GASOLINE))
    }

    /** A successful refresh is what replaces the table — and every reader sees it at once. */
    @Test
    fun `should_quoteTheFreshTable_when_theRefreshSucceeds`() = runTest {
        val repository = AverageFuelPricesRepositoryImpl(source = WorkingSource())

        repository.refresh()

        assertEquals(FRESH_AS_OF, repository.current().asOf)
        assertEquals(
            FRESH_GASOLINE,
            repository.current().averageLitre("ES", FuelType.GASOLINE)?.amount,
        )
    }

    /**
     * Asking again inside the TTL spends no request: the averages move in cents per week, and the
     * feed's own cache is six hours.
     */
    @Test
    fun `should_notAskAgain_when_theHeldTableIsStillFresh`() = runTest {
        val source = WorkingSource()
        var now = 0L
        val repository = AverageFuelPricesRepositoryImpl(source = source, clock = { now })

        repository.refresh()
        now += 60 * 60 * 1000L
        repository.refresh()

        assertEquals(1, source.calls)
    }

    /** Once the day is up, it asks again — otherwise the "refresh" would be a one-shot. */
    @Test
    fun `should_askAgain_when_theTtlHasPassed`() = runTest {
        val source = WorkingSource()
        var now = 0L
        val repository = AverageFuelPricesRepositoryImpl(source = source, clock = { now })

        repository.refresh()
        now += 25 * 60 * 60 * 1000L
        repository.refresh()

        assertEquals(2, source.calls)
    }

    /**
     * ⛔ A failure must NOT start the clock. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
     *
     * The obvious implementation stamps the time on every attempt, which means one failed fetch —
     * the radio off for a second — buys a whole day of not trying again. The stamp moves on success
     * only, so the next opportunity is taken.
     */
    @Test
    fun `should_retryImmediately_when_thePreviousRefreshFailed`() = runTest {
        val source = FailingSource()
        val repository = AverageFuelPricesRepositoryImpl(source = source, clock = { 0L })

        repository.refresh()
        repository.refresh()

        assertEquals(2, source.calls)
    }

    private class FailingSource : AverageFuelPricesSource {
        var calls = 0
        override suspend fun latest(): Result<AverageFuelPriceTable> {
            calls++
            return Result.failure(IllegalStateException("no network"))
        }
    }

    private class WorkingSource : AverageFuelPricesSource {
        var calls = 0
        override suspend fun latest(): Result<AverageFuelPriceTable> {
            calls++
            return Result.success(
                AverageFuelPriceTable(
                    asOf = FRESH_AS_OF,
                    rows = mapOf("ES" to CountryFuelPrices("EUR", FRESH_GASOLINE, 1.8544, 1.1172)),
                ),
            )
        }
    }

    private companion object {
        const val FRESH_AS_OF = "2026-09-09"
        const val FRESH_GASOLINE = 1.9101
    }
}
