@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.stats

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
 * axis the product sells as Plus: *free asks and the app answers; Plus remembers for you.* And it
 * needs no gate to be Plus — the free window is seven days, so a closed month cannot exist for a
 * free account at all.
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
