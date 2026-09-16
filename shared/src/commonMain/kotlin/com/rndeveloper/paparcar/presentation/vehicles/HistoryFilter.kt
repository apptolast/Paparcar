@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.model.UserParking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

sealed class HistoryFilter {
    data object All : HistoryFilter()
    data object ThisWeek : HistoryFilter()

    /** The last seven days, today included — rolling, unlike [ThisWeek]'s calendar week. It is also
     *  the free plan's whole stats window. [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] */
    data object Last7Days : HistoryFilter()
    data object ThisMonth : HistoryFilter()

    /**
     * The calendar month that just closed. [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
     *
     * Until now a closed month could only be looked at through the recap card, which retires after
     * the first week of the new one [STATS-PLUS-THE-RECAP-KNOCKS-001] — so from the 8th onwards
     * "last month" was a thing the app knew and had no way to be asked about.
     */
    data object LastMonth : HistoryFilter()
    data object Last3Months : HistoryFilter()

    /**
     * The current calendar year. The ONLY period whose end is far enough away to make a yearly
     * projection mean something — `projectYear` was written in
     * [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001] and until this filter existed nothing could reach it.
     */
    data object ThisYear : HistoryFilter()

    /** A CLOSED calendar year — 2025, 2024… Derived from the data, never from a list. */
    data class Year(val value: Int) : HistoryFilter()
}

/**
 * The periods this history can actually answer, in reading order.
 * [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
 *
 * 🔑 **Same rule as [availableChartMetrics], and for the same reason**: a chip that shows the same
 * figures as its neighbour is not a view of the data, it is furniture. Offering "Last 3 months" to
 * an account three weeks old draws exactly what "This month" draws; offering "2024" to someone who
 * installed in 2026 draws an empty chart with a lit chip, which reads as a bug rather than as an
 * answer.
 *
 * The consequence is the point of the ticket: **the selector grows with the user.** A new account
 * sees four chips and every one of them says something different; an account with years behind it
 * gains "last month", "this year" and its closed years as they become true.
 *
 * ⛔ The closed years come from the SESSIONS, never from a constant. A `listOf(2024, 2025)` is a
 * bomb with a known date on it.
 */
fun availableFilters(
    sessions: List<UserParking>,
    nowMs: Long = Clock.System.now().toEpochMilliseconds(),
): List<HistoryFilter> {
    val tz = TimeZone.currentSystemDefault()
    val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
    val firstOfThisMonth = LocalDate(today.year, today.month, 1)
    val oldest = sessions.minOfOrNull { it.location.timestamp }

    fun hasDataBefore(date: LocalDate): Boolean =
        oldest != null && oldest < date.atStartOfDayIn(tz).toEpochMilliseconds()

    return buildList {
        // "All" leads, as it always has: it is the one that answers before anything has happened.
        add(HistoryFilter.All)
        add(HistoryFilter.Last7Days)
        add(HistoryFilter.ThisWeek)
        add(HistoryFilter.ThisMonth)
        // A closed month only exists as a question once something happened in it.
        val firstOfLastMonth = firstOfThisMonth.minus(1, DateTimeUnit.MONTH)
        val lastMonthRange = firstOfLastMonth.atStartOfDayIn(tz).toEpochMilliseconds() until
            firstOfThisMonth.atStartOfDayIn(tz).toEpochMilliseconds()
        if (sessions.any { it.location.timestamp in lastMonthRange }) add(HistoryFilter.LastMonth)
        // Anything older than this month makes a quarter say something this month does not.
        if (hasDataBefore(firstOfThisMonth)) add(HistoryFilter.Last3Months)
        // And anything older than the quarter is what makes a YEAR a different question.
        if (hasDataBefore(firstOfThisMonth.minus(QUARTER_MONTHS - 1, DateTimeUnit.MONTH))) {
            add(HistoryFilter.ThisYear)
        }
        addAll(closedYearsWithData(sessions, today.year, tz).map(HistoryFilter::Year))
    }
}

/**
 * The closed calendar years this history has something in, newest first.
 *
 * Separate from [availableFilters] because the UI groups them behind ONE chip rather than spending
 * a chip per year: four years of driving would otherwise push the periods that answer "what is
 * happening now" off the edge of a scrolling row.
 * [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
 */
fun closedYearsWithData(
    sessions: List<UserParking>,
    currentYear: Int,
    tz: TimeZone = TimeZone.currentSystemDefault(),
): List<Int> = sessions
    .map { Instant.fromEpochMilliseconds(it.location.timestamp).toLocalDateTime(tz).year }
    .filter { it < currentYear }
    .distinct()
    .sortedDescending()

/** Calendar months spanned by "Last 3 months" — mirrored from the calculator's own window. */
private const val QUARTER_MONTHS = 3
