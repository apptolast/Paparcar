@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.entitlement.FREE_HISTORY_WINDOW_DAYS
import com.rndeveloper.paparcar.domain.entitlement.periodIsFullyVisible
import com.rndeveloper.paparcar.domain.stats.closedMonthIsFullyVisible
import com.rndeveloper.paparcar.domain.entitlement.historyVisibleFromMs
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which chips a plan can honour, and what the free window really reaches.
 * [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
 *
 * ⛔ The dates are BUILT, never `Clock.System.now()`: half of these answers depend on the day of the
 * month, so a test reading the wall clock would pass or fail by the calendar.
 */
class PeriodIsMeasurableTest {

    private fun at(iso: String): Long =
        LocalDateTime.parse(iso).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    /** The 16th of a 30-day month: a Tuesday, mid-month, nothing on a boundary. */
    private val midMonth = at("2026-09-16T15:00:00")

    @Test
    fun `should_honourEveryPeriod_when_thePlanIsPlus`() {
        val everyFilter = listOf(
            StatsPeriod.Last7Days, StatsPeriod.ThisWeek, StatsPeriod.ThisMonth,
            StatsPeriod.LastMonth, StatsPeriod.Last3Months, StatsPeriod.ThisYear,
            StatsPeriod.Year(2025), StatsPeriod.All,
        )
        everyFilter.forEach { filter ->
            assertTrue(filter.isMeasurableBy(isPlus = true, nowMs = midMonth), "$filter")
        }
    }

    @Test
    fun `should_honourTheRollingWeekAndTheCalendarWeek_when_thePlanIsFree`() {
        // The two windows a seven-day history always covers whole, whatever day it is.
        listOf(midMonth, at("2026-09-14T00:30:00"), at("2026-09-20T23:30:00")).forEach { now ->
            assertTrue(StatsPeriod.Last7Days.isMeasurableBy(isPlus = false, nowMs = now), "7d @$now")
            assertTrue(StatsPeriod.ThisWeek.isMeasurableBy(isPlus = false, nowMs = now), "week @$now")
        }
    }

    @Test
    fun `should_refuseEveryPeriodLongerThanItsWindow_when_thePlanIsFree`() {
        listOf(
            StatsPeriod.LastMonth, StatsPeriod.Last3Months,
            StatsPeriod.ThisYear, StatsPeriod.Year(2025), StatsPeriod.All,
        ).forEach { filter ->
            assertFalse(filter.isMeasurableBy(isPlus = false, nowMs = midMonth), "$filter")
        }
    }

    /**
     * The one period whose answer depends on the DATE — and the reason the substitution inside
     * `HistoryTimeline.resolve` has to stay: a chip honoured at 23:59 on the 7th is not honoured a
     * minute later.
     */
    @Test
    fun `should_honourThisMonth_when_freeAndTheMonthIsYoungerThanTheWindow`() {
        assertTrue(StatsPeriod.ThisMonth.isMeasurableBy(isPlus = false, nowMs = at("2026-10-03T12:00:00")))
        assertTrue(StatsPeriod.ThisMonth.isMeasurableBy(isPlus = false, nowMs = at("2026-10-07T23:59:00")))
        assertFalse(StatsPeriod.ThisMonth.isMeasurableBy(isPlus = false, nowMs = at("2026-10-08T00:01:00")))
        assertFalse(StatsPeriod.ThisMonth.isMeasurableBy(isPlus = false, nowMs = midMonth))
    }

    /** "Since forever" is a period nobody with a window can cover — not even an enormous one. */
    @Test
    fun `should_refuseAll_when_thereIsAnyWindowAtAll`() {
        assertFalse(periodIsFullyVisible(periodStartMs = null, visibleFromMs = 0L))
        assertTrue(periodIsFullyVisible(periodStartMs = null, visibleFromMs = null))
    }

    /**
     * ⛔ The closed-month recap asks the SAME question through the same predicate now. If this ever
     * disagrees with what `closedMonthIsFullyVisible` used to answer by hand, the recap and the
     * chips have drifted apart again.
     */
    @Test
    fun `should_agreeWithTheRecapGate_when_askedAboutTheClosedMonth`() {
        listOf(at("2026-10-01T09:00:00"), at("2026-10-07T09:00:00"), midMonth).forEach { now ->
            listOf(true, false).forEach { isPlus ->
                assertEquals(
                    StatsPeriod.LastMonth.isMeasurableBy(isPlus, now),
                    closedMonthIsFullyVisible(historyVisibleFromMs(isPlus, now), now),
                    "isPlus=$isPlus @$now",
                )
            }
        }
    }

    /** The free window is what it says it is — if the constant moves, this test is where it shows. */
    @Test
    fun `should_reachExactlyTheFreeWindow_when_thePlanIsFree`() {
        val dayMs = 86_400_000L
        val from = historyVisibleFromMs(isPlus = false, nowMs = midMonth)!!
        assertEquals(midMonth - FREE_HISTORY_WINDOW_DAYS * dayMs, from)
    }
}
