@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The period selector grows with the history behind it.
 * [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
 *
 * The rule under test is the one `availableChartMetrics` already lives by: a chip that draws what
 * its neighbour draws is not a view of the data. "Last 3 months" over an account three weeks old IS
 * "this month"; "2024" to someone who installed in 2026 is an empty chart with a lit chip.
 */
class AvailableFiltersTest {

    private val tz = TimeZone.currentSystemDefault()

    /** A Tuesday in the middle of a month, so no fixture lands on a boundary by accident. */
    private val today = LocalDate(2026, 9, 15)
    private val nowMs = today.atStartOfDayIn(tz).toEpochMilliseconds() + 12 * 60 * 60 * 1000L

    private fun at(date: LocalDate): Long =
        date.atStartOfDayIn(tz).toEpochMilliseconds() + 12 * 60 * 60 * 1000L

    private fun session(date: LocalDate) = UserParking(
        id = "s-$date",
        location = GpsPoint(0.0, 0.0, accuracy = 5f, timestamp = at(date), speed = 0f),
        isActive = false,
    )

    @Test
    fun should_offerOnlyTheAlwaysTruePeriods_when_theHistoryIsDaysOld() {
        val sessions = listOf(session(today), session(today.minus(3, DateTimeUnit.DAY)))

        val filters = availableFilters(sessions, nowMs)

        assertEquals(
            listOf(
                HistoryFilter.All,
                HistoryFilter.Last7Days,
                HistoryFilter.ThisWeek,
                HistoryFilter.ThisMonth,
            ),
            filters,
            "A three-day-old history was offered a period it cannot answer differently",
        )
    }

    @Test
    fun should_offerLastMonth_when_somethingHappenedInTheClosedMonth() {
        val sessions = listOf(session(today), session(LocalDate(2026, 8, 20)))

        val filters = availableFilters(sessions, nowMs)

        assertTrue(HistoryFilter.LastMonth in filters)
        // And a quarter, because there is now history older than this month.
        assertTrue(HistoryFilter.Last3Months in filters)
    }

    @Test
    fun should_notOfferLastMonth_when_theClosedMonthIsEmpty() {
        // Old history, but NOTHING in August: "last month" would draw five empty bars.
        val sessions = listOf(session(today), session(LocalDate(2026, 5, 4)))

        val filters = availableFilters(sessions, nowMs)

        assertFalse(HistoryFilter.LastMonth in filters)
        assertTrue(HistoryFilter.Last3Months in filters)
    }

    @Test
    fun should_offerThisYear_when_theHistoryReachesBeyondTheQuarter() {
        val justInsideTheQuarter = listOf(session(today), session(LocalDate(2026, 7, 2)))
        val beyondIt = listOf(session(today), session(LocalDate(2026, 2, 2)))

        assertFalse(
            HistoryFilter.ThisYear in availableFilters(justInsideTheQuarter, nowMs),
            "Inside the quarter, a year draws what the quarter already drew",
        )
        assertTrue(HistoryFilter.ThisYear in availableFilters(beyondIt, nowMs))
    }

    @Test
    fun should_offerOnlyTheClosedYearsThatHaveData_when_theHistorySpansYears() {
        // 2024 and 2026 have parkings; 2025 does not.
        val sessions = listOf(
            session(today),
            session(LocalDate(2024, 6, 1)),
            session(LocalDate(2024, 11, 3)),
        )

        val years = availableFilters(sessions, nowMs).filterIsInstance<HistoryFilter.Year>()

        assertEquals(listOf(HistoryFilter.Year(2024)), years)
    }

    @Test
    fun should_neverOfferTheRunningYearAsAClosedOne_when_thisYearHasData() {
        val sessions = listOf(session(today), session(LocalDate(2026, 1, 9)))

        val years = availableFilters(sessions, nowMs).filterIsInstance<HistoryFilter.Year>()

        assertTrue(
            years.isEmpty(),
            "2026 is still running: it is `ThisYear`, and offering it twice would be two chips " +
                "for one period",
        )
    }
}
