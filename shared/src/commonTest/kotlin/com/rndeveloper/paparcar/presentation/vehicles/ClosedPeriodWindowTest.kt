@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.stats.closedMonthWindow
import com.rndeveloper.paparcar.domain.stats.previousToClosedMonthWindow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The periods that CLOSED in the past, and the chart that has to grow a unit to draw them.
 * [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
 *
 * Until these existed every window ran up to "now", so filtering by its start alone was the same
 * thing as filtering by both ends. The moment a period finished in the past that stopped being
 * true: a start-only cut would have made "last month" grow all through this one.
 */
class ClosedPeriodWindowTest {

    private val tz = TimeZone.currentSystemDefault()
    private val today = LocalDate(2026, 9, 15)
    private val nowMs = today.atStartOfDayIn(tz).toEpochMilliseconds() + 12 * 60 * 60 * 1000L
    private val dayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
    private val monthNames = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic",
    )

    private fun at(date: LocalDate): Long =
        date.atStartOfDayIn(tz).toEpochMilliseconds() + 12 * 60 * 60 * 1000L

    private fun session(date: LocalDate) = UserParking(
        id = "s-$date",
        location = GpsPoint(0.0, 0.0, accuracy = 5f, timestamp = at(date), speed = 0f),
        isActive = false,
    )

    @Test
    fun should_keepOnlyTheClosedMonth_when_filteringByLastMonth() {
        val sessions = listOf(
            session(LocalDate(2026, 7, 31)), // before it
            session(LocalDate(2026, 8, 1)),  // first day of it
            session(LocalDate(2026, 8, 31)), // last day of it
            session(today),                  // this month
        )

        val kept = VehicleHistoryCalculator.filter(sessions, StatsPeriod.LastMonth, nowMs)

        assertEquals(
            listOf("s-2026-08-01", "s-2026-08-31"),
            kept.map { it.id },
            "Last month has two ends; without the second one it would swallow this month too",
        )
    }

    @Test
    fun should_keepOnlyThatYear_when_filteringByAClosedYear() {
        val sessions = listOf(
            session(LocalDate(2024, 12, 31)),
            session(LocalDate(2025, 1, 1)),
            session(LocalDate(2025, 12, 31)),
            session(LocalDate(2026, 1, 1)),
        )

        val kept = VehicleHistoryCalculator.filter(sessions, StatsPeriod.Year(2025), nowMs)

        assertEquals(listOf("s-2025-01-01", "s-2025-12-31"), kept.map { it.id })
    }

    @Test
    fun should_haveAnEnd_when_thePeriodIsCalendarAndNotRolling() {
        // The type IS the rule: only a period that can finish may be projected.
        // [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001]
        assertTrue(VehicleHistoryCalculator.windowEndMs(StatsPeriod.ThisYear, nowMs) != null)
        assertTrue(VehicleHistoryCalculator.windowEndMs(StatsPeriod.LastMonth, nowMs) != null)
        assertTrue(VehicleHistoryCalculator.windowEndMs(StatsPeriod.Year(2025), nowMs) != null)
        assertTrue(VehicleHistoryCalculator.windowEndMs(StatsPeriod.Last3Months, nowMs) == null)
        assertTrue(VehicleHistoryCalculator.windowEndMs(StatsPeriod.All, nowMs) == null)
    }

    @Test
    fun should_compareAYearAgainstThePreviousYear_when_thePeriodIsAYear() {
        val window = VehicleHistoryCalculator.previousWindow(StatsPeriod.Year(2025), nowMs)!!

        val startOf2024 = LocalDate(2024, 1, 1).atStartOfDayIn(tz).toEpochMilliseconds()
        val startOf2025 = LocalDate(2025, 1, 1).atStartOfDayIn(tz).toEpochMilliseconds()
        assertEquals(startOf2024, window.first)
        assertEquals(startOf2025 - 1, window.last)
    }

    @Test
    fun should_drawTwelveMonths_when_thePeriodIsACalendarYear() {
        val sessions = listOf(session(LocalDate(2025, 3, 4)), session(LocalDate(2025, 11, 20)))

        val bars = VehicleHistoryCalculator.buildActivityBuckets(
            sessions = VehicleHistoryCalculator.filter(sessions, StatsPeriod.Year(2025), nowMs),
            filter = StatsPeriod.Year(2025),
            dayLabels = dayLabels,
            monthNamesShort = monthNames,
            nowMs = nowMs,
        )

        assertEquals(12, bars.size)
        assertEquals(1f, bars[2].value, "March")
        assertEquals(1f, bars[10].value, "November")
        assertTrue(bars.none { it.isCurrent }, "A finished year has no current month")
    }

    @Test
    fun should_drawOneBarPerYear_when_allSpansMoreThanTwelveMonths() {
        // Three calendar years of history: the old cap drew the last twelve months and called it
        // "All", which is the lie this ticket exists to stop.
        val sessions = listOf(
            session(LocalDate(2024, 5, 5)),
            session(LocalDate(2025, 5, 5)),
            session(LocalDate(2026, 5, 5)),
            session(today),
        )

        val bars = VehicleHistoryCalculator.buildActivityBuckets(
            sessions = sessions,
            filter = StatsPeriod.All,
            dayLabels = dayLabels,
            monthNamesShort = monthNames,
            nowMs = nowMs,
        )

        assertEquals(listOf("2024", "2025", "2026"), bars.map { it.label })
        assertEquals(sessions.size, bars.sumOf { it.value.toInt() }, "Every session has a bar")
        assertTrue(bars.last().isCurrent)
    }

    @Test
    fun should_keepDrawingMonths_when_allFitsInsideTheYear() {
        val sessions = listOf(session(LocalDate(2026, 5, 5)), session(today))

        val bars = VehicleHistoryCalculator.buildActivityBuckets(
            sessions = sessions,
            filter = StatsPeriod.All,
            dayLabels = dayLabels,
            monthNamesShort = monthNames,
            nowMs = nowMs,
        )

        assertEquals(12, bars.size, "Inside a year, All is still twelve monthly bars")
    }

    /**
     * ⛔ **Dos autoridades, una sola ventana.** `closedMonthWindow` nació para el resumen del mes
     * cerrado y `LastMonth` para el chip, y calculan la MISMA fecha en dos sitios
     * [STATS-CORE-001: ninguna segunda aritmética de fechas]. Por eso el resumen pasa ya por
     * `HistoryTimeline.resolve(LastMonth)` —y así hereda el gate de plan, el precio del combustible
     * y la ventana anterior—: este test es lo que autoriza ese cambio a no mover ni una cifra.
     * [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
     */
    @Test
    fun should_spanTheSameWindow_whicheverAuthorityIsAsked() {
        val closed = closedMonthWindow(nowMs, tz)
        assertEquals(closed.startMs, VehicleHistoryCalculator.windowStartMs(StatsPeriod.LastMonth, nowMs))
        assertEquals(closed.endMs, VehicleHistoryCalculator.windowEndMs(StatsPeriod.LastMonth, nowMs))

        // Y la comparación ▲/▼ también: el mes anterior al cerrado es el mismo por los dos caminos.
        val before = previousToClosedMonthWindow(nowMs, tz)
        val previous = VehicleHistoryCalculator.previousWindow(StatsPeriod.LastMonth, nowMs)
        assertEquals(before.startMs until before.endMs, previous)
    }
}
