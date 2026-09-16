@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.entitlement.periodIsFullyVisible
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The month that just ended — the one period the app brings up on its own.
 * [STATS-PLUS-REMEMBERS-AND-REPORTS-001]
 *
 * Every other stats surface answers a chip the user pressed. This one speaks first, which is the
 * axis the product sells as Plus: *free asks and the app answers; Plus remembers for you.* What
 * makes it Plus is [closedMonthIsFullyVisible] — and it had to be written, because the reasoning
 * that replaced it did not hold. See that function.
 *
 * Pure, with the time zone injected, because "which month just closed" is a question about the
 * user's calendar and not about UTC: at 00:30 on the 1st, UTC is still in the old month.
 */
data class MonthWindow(val startMs: Long, val endMs: Long, val monthNumber: Int, val year: Int) {
    operator fun contains(timestampMs: Long): Boolean = timestampMs in startMs until endMs
}

/** The previous calendar month. In January this is December of the year before — the reason this is
 *  a function over `LocalDate` arithmetic and not a subtraction of milliseconds. */
fun closedMonthWindow(
    nowMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): MonthWindow {
    val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(timeZone).date
    val firstOfThisMonth = LocalDate(today.year, today.month, 1)
    val firstOfClosed = firstOfThisMonth.minus(1, DateTimeUnit.MONTH)
    return MonthWindow(
        startMs = firstOfClosed.atStartOfDayIn(timeZone).toEpochMilliseconds(),
        endMs = firstOfThisMonth.atStartOfDayIn(timeZone).toEpochMilliseconds(),
        monthNumber = firstOfClosed.month.number,
        year = firstOfClosed.year,
    )
}

/** The month before the closed one — what the recap's ▲/▼ compare against. */
fun previousToClosedMonthWindow(
    nowMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): MonthWindow {
    val closed = closedMonthWindow(nowMs, timeZone)
    val firstOfClosed = LocalDate(closed.year, closed.monthNumber, 1)
    val firstOfPrevious = firstOfClosed.minus(1, DateTimeUnit.MONTH)
    return MonthWindow(
        startMs = firstOfPrevious.atStartOfDayIn(timeZone).toEpochMilliseconds(),
        endMs = closed.startMs,
        monthNumber = firstOfPrevious.month.number,
        year = firstOfPrevious.year,
    )
}

/**
 * Whether the history this user may see COVERS the whole month being recapped — the only condition
 * under which a recap of it is a recap and not a fragment.
 * [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
 *
 * ⛔ **This is the Plus gate, and it was missing.** Two KDocs asserted that a free account returns
 * null "by construction", because its window is seven days and a closed month cannot survive it.
 * That is false on exactly the days the recap exists: it only composes on the 1st–7th of the new
 * month, and a rolling seven-day window on the 1st of October reaches back to the 24th of
 * September. A free account would have been shown four days of September under the words "last
 * month" — not a hidden figure, a WRONG one, which is worse.
 *
 * ⛔ And it is written as COVERAGE rather than as `if (isPlus)` on purpose: it says why the card is
 * absent, and it is the same rule the partial-sum doctrine already lives by
 * [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001].
 *
 * ⛔ **On its own it would not make raising the free window safe**, and saying it did would be the
 * same overclaim this function exists to correct. `HistoryTimeline.resolve` substitutes `Last7Days`
 * for a free account's figures whatever period it is handed
 * [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] — right for the panel, where a chip picks the
 * period; wrong for a recap, whose title is a fixed "last month" that cannot relabel itself. So the
 * caller does not rely on this gate alone: it also requires `Resolved.statsPeriod` to BE the closed
 * month, which is a structural answer rather than a promise about a constant.
 *
 * [visibleFromMs] is `historyVisibleFromMs(isPlus, nowMs)`: the oldest instant the user may see,
 * null when they may see everything.
 *
 * ⛔ The arithmetic itself is NOT here: this is `periodIsFullyVisible` with the closed month's
 * window put into it. It was written out by hand while the period chips asked the very same
 * question a second time and got no answer at all
 * [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001].
 */
fun closedMonthIsFullyVisible(
    visibleFromMs: Long?,
    nowMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean = periodIsFullyVisible(
    periodStartMs = closedMonthWindow(nowMs, timeZone).startMs,
    visibleFromMs = visibleFromMs,
)

/**
 * Whether the recap is still NEWS.
 *
 * ⛔ A summary of September read on the 27th of October is not a summary, it is a memory — and it
 * would be sitting on top of the screen, above everything that is actually happening, every single
 * day of the year. The card exists because something just happened; when it stops being just, it
 * goes away on its own.
 */
fun isRecapFresh(
    nowMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean {
    val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(timeZone).date
    return today.day <= RECAP_FRESH_DAYS
}

/** The first week of the new month: long enough that nobody misses it, short enough to be news. */
const val RECAP_FRESH_DAYS = 7
