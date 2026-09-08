package com.rndeveloper.paparcar.domain.entitlement

import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlusOfferSavingsTest {

    private fun offer(plan: PlusPlan, micros: Long) =
        PlusOffer(plan = plan, price = "irrelevant", amountMicros = micros)

    private fun offers(monthlyMicros: Long?, yearlyMicros: Long?) = listOfNotNull(
        monthlyMicros?.let { offer(PlusPlan.MONTHLY, it) },
        yearlyMicros?.let { offer(PlusPlan.YEARLY, it) },
    )

    @Test
    fun `should_reportTheRoundedSaving_when_theYearlyPlanIsCheaperThanTwelveMonths`() {
        // The shipping prices: 1,99 €/month against 13,99 €/year. Paying monthly for a year is
        // 23,88 €, so the yearly plan saves 9,89 € — 41 %.
        val percent = annualSavingsPercent(offers(1_990_000L, 13_990_000L))

        assertEquals(41, percent)
    }

    @Test
    fun `should_reportNoSaving_when_theYearlyPlanCostsTwelveMonths`() {
        // Exactly break-even is not a saving, and a "0 %" badge on a paywall reads as a mistake.
        assertNull(annualSavingsPercent(offers(1_000_000L, 12_000_000L)))
    }

    @Test
    fun `should_reportNoSaving_when_theYearlyPlanIsDearerThanPayingMonthly`() {
        assertNull(annualSavingsPercent(offers(1_000_000L, 15_000_000L)))
    }

    @Test
    fun `should_reportNoSaving_when_theDiscountIsTooSmallToBeWorthABadge`() {
        // 2 % off invites the reader to do arithmetic instead of making a decision.
        assertNull(annualSavingsPercent(offers(1_000_000L, 11_760_000L)))
    }

    @Test
    fun `should_reportNoSaving_when_aPlanIsMissing`() {
        // A store that only registered one product must not produce a badge comparing it to
        // nothing. Both directions, because either package can be the one that failed to load.
        assertNull(annualSavingsPercent(offers(1_990_000L, null)))
        assertNull(annualSavingsPercent(offers(null, 13_990_000L)))
        assertNull(annualSavingsPercent(emptyList()))
    }

    @Test
    fun `should_reportNoSaving_when_aPriceIsNotPositive`() {
        // A fully discounted or malformed product would otherwise divide by zero or claim 100 %.
        assertNull(annualSavingsPercent(offers(0L, 13_990_000L)))
        assertNull(annualSavingsPercent(offers(1_990_000L, 0L)))
    }
}
