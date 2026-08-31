@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001]
 *
 * The filter and the chart used to compute their windows separately, and disagree. Two symptoms,
 * one cause:
 *
 *  - "This week" cut from Monday but drew 7 ROLLING days, so on a Wednesday four bars belonged to
 *    last week and could only ever read 0 — days the filter had excluded, drawn as days without
 *    activity.
 *  - "Last 3 months" cut 90 ROLLING days but drew 3 CALENDAR months, so on the 1st of a month
 *    almost a month of sessions counted in the title and in the km with no bar to appear in.
 *
 * The invariant that kills both, and the reason these tests assert a sum rather than a layout:
 * **every session the filter keeps has a bar to be counted in.** Only "All" is exempt, and that
 * exemption is deliberate and documented (the chart caps at 6 months, the total does not).
 *
 * These are also the first tests that can reach the bucket builders at all: they used to be private
 * functions of `HistoryContent.kt` calling `Clock.System.now()` internally.
 */
class HistoryChartWindowTest {

    private val tz = TimeZone.currentSystemDefault()
    private val dayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
    private val monthNames = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic",
    )

    /** Local midday, so a timezone shift can never move a fixture across a day boundary. */
    private fun at(date: LocalDate): Long =
        date.atStartOfDayIn(tz).toEpochMilliseconds() + 12 * 60 * 60 * 1000L

    private fun session(date: LocalDate) = UserParking(
        id = "s-$date",
        location = GpsPoint(0.0, 0.0, accuracy = 5f, timestamp = at(date), speed = 0f),
        isActive = false,
    )

    private fun bucketsFor(sessions: List<UserParking>, filter: HistoryFilter, nowMs: Long) =
        VehicleHistoryCalculator.buildActivityBuckets(
            sessions = VehicleHistoryCalculator.filter(sessions, filter, nowMs),
            filter = filter,
            dayLabels = dayLabels,
            monthNamesShort = monthNames,
            nowMs = nowMs,
        )

    private fun assertEveryCountedSessionHasABar(
        sessions: List<UserParking>,
        filter: HistoryFilter,
        nowMs: Long,
    ) {
        val kept = VehicleHistoryCalculator.filter(sessions, filter, nowMs).size
        val drawn = bucketsFor(sessions, filter, nowMs).sumOf { it.sessions }
        assertEquals(
            kept,
            drawn,
            "$filter counts $kept parkings but its bars only account for $drawn — " +
                "the difference is history the user is told about and cannot find",
        )
    }

    // ── This week ─────────────────────────────────────────────────────────────

    @Test
    fun should_drawOnlyDaysInsideTheWindow_when_theWeekHasJustStarted() {
        // A Monday: the worst case for the old rolling chart — six of its seven bars belonged to
        // last week, so the user saw a week that looked almost empty.
        val monday = LocalDate(2026, 8, 31)
        assertEquals(1, monday.dayOfWeek.isoDayNumber, "fixture must be a Monday")
        val nowMs = at(monday)

        val sessions = listOf(
            session(monday),
            session(monday.minus(3, DateTimeUnit.DAY)), // last Friday — outside the window
        )

        val bars = bucketsFor(sessions, HistoryFilter.ThisWeek, nowMs)

        assertEquals(7, bars.size)
        assertEquals(dayLabels, bars.map { it.label }, "the week reads Monday → Sunday")
        assertEquals(1, bars.first().sessions, "today's parking lands on Monday")
        assertEveryCountedSessionHasABar(sessions, HistoryFilter.ThisWeek, nowMs)
    }

    @Test
    fun should_leaveOnlyFutureDaysEmpty_when_midWeek() {
        val wednesday = LocalDate(2026, 9, 2)
        assertEquals(3, wednesday.dayOfWeek.isoDayNumber, "fixture must be a Wednesday")
        val nowMs = at(wednesday)
        val monday = wednesday.minus(2, DateTimeUnit.DAY)

        val sessions = (0..2).map { session(monday.plus(it, DateTimeUnit.DAY)) }
        val bars = bucketsFor(sessions, HistoryFilter.ThisWeek, nowMs)

        // Mon–Wed happened and are filled; Thu–Sun have not arrived yet. An empty FUTURE day claims
        // nothing; an empty PAST day outside the window was the lie.
        assertTrue(bars.take(3).all { it.sessions == 1 })
        assertTrue(bars.drop(3).all { it.sessions == 0 })
        assertTrue(bars[2].isCurrent, "today is the highlighted bar")
        assertEveryCountedSessionHasABar(sessions, HistoryFilter.ThisWeek, nowMs)
    }

    // ── Last 3 months ─────────────────────────────────────────────────────────

    @Test
    fun should_giveEveryCountedSessionABar_when_theMonthHasJustTurned() {
        // The 1st of a month: 90 rolling days reached back into a month the 3 bars do not cover, so
        // the title counted parkings that had nowhere to appear.
        val firstOfSeptember = LocalDate(2026, 9, 1)
        val nowMs = at(firstOfSeptember)

        val sessions = listOf(
            session(LocalDate(2026, 6, 15)), // inside 90 rolling days, outside the 3 calendar months
            session(LocalDate(2026, 7, 10)),
            session(LocalDate(2026, 8, 20)),
            session(firstOfSeptember),
        )

        val bars = bucketsFor(sessions, HistoryFilter.Last3Months, nowMs)

        assertEquals(listOf("jul", "ago", "sep"), bars.map { it.label })
        assertEveryCountedSessionHasABar(sessions, HistoryFilter.Last3Months, nowMs)
    }

    @Test
    fun should_dropWhatItCannotDraw_when_theWindowIsThreeCalendarMonths() {
        val firstOfSeptember = LocalDate(2026, 9, 1)
        val june = session(LocalDate(2026, 6, 15))

        // June is no longer counted either — the honest half of the trade: the filter now excludes
        // exactly what the chart cannot show, instead of counting it invisibly.
        assertEquals(
            emptyList(),
            VehicleHistoryCalculator.filter(
                listOf(june),
                HistoryFilter.Last3Months,
                at(firstOfSeptember),
            ),
        )
    }

    // ── Every window ──────────────────────────────────────────────────────────

    @Test
    fun should_accountForEveryKeptSession_across_everyBoundedFilter() {
        // A spread that crosses a week, a month and a quarter boundary at once.
        val nowMs = at(LocalDate(2026, 9, 1))
        val sessions = listOf(
            LocalDate(2026, 5, 2), LocalDate(2026, 6, 15), LocalDate(2026, 7, 10),
            LocalDate(2026, 8, 20), LocalDate(2026, 8, 31), LocalDate(2026, 9, 1),
        ).map { session(it) }

        // "All" is exempt on purpose: its chart caps at 6 months while the total counts everything.
        listOf(HistoryFilter.ThisWeek, HistoryFilter.ThisMonth, HistoryFilter.Last3Months)
            .forEach { assertEveryCountedSessionHasABar(sessions, it, nowMs) }
    }
}
