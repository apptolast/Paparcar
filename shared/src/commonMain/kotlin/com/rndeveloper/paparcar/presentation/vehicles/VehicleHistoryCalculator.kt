@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.stats.ScopedMeasure
import com.rndeveloper.paparcar.domain.stats.sumMeasuredDistance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [AUDIT-M11-001] Pure history filtering + stat aggregation, extracted out of
 * [VehiclesViewModel] so the (business) computation is unit-testable in isolation instead of being
 * reachable only by driving the whole ViewModel + its flows. No side effects, no state.
 */
object VehicleHistoryCalculator {

    /**
     * The instant [filter] opens at, or null for "everything ever".
     *
     * [UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001] This is the ONE place a time window is
     * decided. Both the session filter and the chart's buckets read it, so they cannot disagree —
     * they used to: the filter cut the week from Monday while the chart drew 7 rolling days, and
     * the filter cut 90 rolling days while the chart drew 3 calendar months. Bars appeared for days
     * the filter had already excluded, and (on the 1st of a month) sessions counted in the title
     * with no bar to appear in.
     *
     * Every window is CALENDAR-natural, which is what "this week" and "this month" already meant to
     * a reader. Deliberate consequence: early in a month "last 3 months" now shows less than the 90
     * rolling days did — correct, because what it showed could not be located on the chart.
     */
    fun windowStartMs(
        filter: HistoryFilter,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): Long? {
        if (filter == HistoryFilter.All) return null
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
        val startDate = when (filter) {
            HistoryFilter.All -> return null
            HistoryFilter.ThisWeek -> today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
            // Rolling: today and the six days before it. [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
            HistoryFilter.Last7Days -> today.minus(DAYS_PER_WEEK - 1, DateTimeUnit.DAY)
            HistoryFilter.ThisMonth -> LocalDate(today.year, today.month, 1)
            // The 1st of the month (MONTHLY_BUCKETS - 1) months back: three calendar months
            // INCLUDING the current one, which is exactly the span the three bars cover.
            HistoryFilter.Last3Months ->
                LocalDate(today.year, today.month, 1).minus(MONTHLY_BUCKETS - 1, DateTimeUnit.MONTH)
        }
        return startDate.atStartOfDayIn(tz).toEpochMilliseconds()
    }

    /**
     * When [filter]'s window CLOSES — and null for the ones that never do.
     * [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001]
     *
     * ⛔ Only a CALENDAR period has an end. "Last 7 days" and "Last 3 months" are rolling windows:
     * they always cover the same span and never close, so projecting their end means nothing —
     * tomorrow the window has moved, with the user still inside it. "All" has no end at all.
     * Returning null for those three is that rule expressed as a type, so no caller can project a
     * period that cannot finish.
     *
     * It lives next to [windowStartMs] because a window's two ends are one decision.
     */
    fun windowEndMs(
        filter: HistoryFilter,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): Long? {
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
        val endDate = when (filter) {
            HistoryFilter.ThisWeek ->
                today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
                    .plus(DAYS_PER_WEEK, DateTimeUnit.DAY)
            HistoryFilter.ThisMonth -> LocalDate(today.year, today.month, 1).plus(1, DateTimeUnit.MONTH)
            HistoryFilter.Last7Days, HistoryFilter.Last3Months, HistoryFilter.All -> return null
        }
        return endDate.atStartOfDayIn(tz).toEpochMilliseconds()
    }

    /** Sessions within the time window of [filter]. `nowMs` injectable for deterministic tests. */
    fun filter(
        sessions: List<UserParking>,
        filter: HistoryFilter,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): List<UserParking> {
        val startMs = windowStartMs(filter, nowMs) ?: return sessions
        return sessions.filter { it.location.timestamp >= startMs }
    }

    // computeStats moved to domain in [STATS-CORE-001]: ComputePeriodStatsUseCase is THE
    // aggregator (the F4 panel, the teaser and HistoryStatsData all derive from it). This object
    // keeps ONLY what is presentation-shaped: the window authority and the chart buckets.

    /**
     * Distance covered by [sessions], with coverage — delegates to the ONE implementation in
     * domain/stats [STATS-CORE-001]; see [sumMeasuredDistance] for the partial-sum doctrine
     * [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001].
     */
    fun sumDistanceMeters(sessions: List<UserParking>): ScopedMeasure<Float>? =
        sumMeasuredDistance(sessions)

    /**
     * The [startMs, endMs) window of the period IMMEDIATELY BEFORE [filter]'s, for the ▲/▼
     * comparison — or null for [HistoryFilter.All], which has no "previous everything".
     * Same calendar-natural rules as [windowStartMs], same single owner. [STATS-CORE-001]
     */
    fun previousWindow(
        filter: HistoryFilter,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): LongRange? {
        val currentStart = windowStartMs(filter, nowMs) ?: return null
        val tz = TimeZone.currentSystemDefault()
        val currentStartDate = Instant.fromEpochMilliseconds(currentStart).toLocalDateTime(tz).date
        val previousStartDate = when (filter) {
            HistoryFilter.All -> return null
            HistoryFilter.ThisWeek -> currentStartDate.minus(DAYS_PER_WEEK, DateTimeUnit.DAY)
            HistoryFilter.Last7Days -> currentStartDate.minus(DAYS_PER_WEEK, DateTimeUnit.DAY)
            HistoryFilter.ThisMonth -> currentStartDate.minus(1, DateTimeUnit.MONTH)
            HistoryFilter.Last3Months -> currentStartDate.minus(MONTHLY_BUCKETS, DateTimeUnit.MONTH)
        }
        return previousStartDate.atStartOfDayIn(tz).toEpochMilliseconds() until currentStart
    }

    /**
     * The chart's bars for [filter], built from the sessions the SAME filter kept.
     *
     * [UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001] Lives here, next to the window it
     * spans, and takes `nowMs` — the three builders used to be private functions of
     * `HistoryContent.kt` calling `Clock.System.now()` inside, so no test could reach them. That is
     * why a chart drawing days the filter had excluded went unnoticed while the filter itself was
     * covered.
     *
     * Bars a filter's window includes but the calendar has not reached yet (the rest of this week,
     * the rest of this month) read as empty track — the same choice the month view already made. A
     * future day being empty is not a claim; a PAST day outside the window reading as empty was.
     */
    fun buildActivityBuckets(
        sessions: List<UserParking>,
        filter: HistoryFilter,
        dayLabels: List<String>,
        monthNamesShort: List<String>,
        /** What each bar MEASURES. The default is the chart as it always was - counting parkings -
         *  so a caller with no metric to offer draws exactly what it drew before.
         *  [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001] */
        metric: ChartMetric = ChartMetric.PARKINGS,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): List<ActivityBucket> = when (filter) {
        HistoryFilter.ThisWeek -> weekDayBuckets(sessions, dayLabels, metric, nowMs)
        HistoryFilter.Last7Days -> rollingDayBuckets(sessions, dayLabels, metric, nowMs)
        HistoryFilter.ThisMonth -> monthWeekBuckets(sessions, metric, nowMs)
        HistoryFilter.Last3Months ->
            monthlyBuckets(sessions, monthNamesShort, MONTHLY_BUCKETS, metric, nowMs)
        HistoryFilter.All -> monthlyBuckets(sessions, monthNamesShort, ALL_MONTHS_CAP, metric, nowMs)
    }

    /** One bar per day of the CURRENT week, Monday → Sunday — the same week the filter cut. */
    private fun weekDayBuckets(
        sessions: List<UserParking>,
        dayLabels: List<String>,
        metric: ChartMetric,
        nowMs: Long,
    ): List<ActivityBucket> {
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
        val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        val byDate = sessions.groupBy {
            Instant.fromEpochMilliseconds(it.location.timestamp).toLocalDateTime(tz).date
        }
        return (0 until DAYS_PER_WEEK).map { offset ->
            val date = monday.plus(offset, DateTimeUnit.DAY)
            ActivityBucket(
                label = dayLabels[date.dayOfWeek.isoDayNumber - 1],
                value = metric.valueOf(byDate[date].orEmpty()),
                isCurrent = date == today,
            )
        }
    }

    /** One bar per day of the last seven, oldest → today — the same rolling window the filter cut.
     *  [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] */
    private fun rollingDayBuckets(
        sessions: List<UserParking>,
        dayLabels: List<String>,
        metric: ChartMetric,
        nowMs: Long,
    ): List<ActivityBucket> {
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
        val first = today.minus(DAYS_PER_WEEK - 1, DateTimeUnit.DAY)
        val byDate = sessions.groupBy {
            Instant.fromEpochMilliseconds(it.location.timestamp).toLocalDateTime(tz).date
        }
        return (0 until DAYS_PER_WEEK).map { offset ->
            val date = first.plus(offset, DateTimeUnit.DAY)
            ActivityBucket(
                label = dayLabels[date.dayOfWeek.isoDayNumber - 1],
                value = metric.valueOf(byDate[date].orEmpty()),
                isCurrent = date == today,
            )
        }
    }

    /** One bar per week of the current month, labelled by the week's starting day-of-month. */
    private fun monthWeekBuckets(
        sessions: List<UserParking>,
        metric: ChartMetric,
        nowMs: Long,
    ): List<ActivityBucket> {
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
        val currentWeek = (today.day - 1) / DAYS_PER_WEEK
        fun weekOf(session: UserParking): Int {
            val d = Instant.fromEpochMilliseconds(session.location.timestamp).toLocalDateTime(tz).date
            return ((d.day - 1) / DAYS_PER_WEEK).coerceAtMost(WEEKS_IN_MONTH - 1)
        }
        val byWeek = sessions
            .filter { s ->
                val d = Instant.fromEpochMilliseconds(s.location.timestamp).toLocalDateTime(tz).date
                d.year == today.year && d.month == today.month
            }
            .groupBy(::weekOf)
        return (0 until WEEKS_IN_MONTH).map { i ->
            ActivityBucket(
                label = "${i * DAYS_PER_WEEK + 1}",
                value = metric.valueOf(byWeek[i].orEmpty()),
                isCurrent = i == currentWeek,
            )
        }
    }

    /** One bar per calendar month for the last [months] months, oldest → newest. */
    private fun monthlyBuckets(
        sessions: List<UserParking>,
        monthNamesShort: List<String>,
        months: Int,
        metric: ChartMetric,
        nowMs: Long,
    ): List<ActivityBucket> {
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
        val grouped = sessions.groupBy { s ->
            val d = Instant.fromEpochMilliseconds(s.location.timestamp).toLocalDateTime(tz)
            d.year to d.month.number
        }
        val firstOfThisMonth = LocalDate(today.year, today.month, 1)
        return (months - 1 downTo 0).map { back ->
            val month = firstOfThisMonth.minus(back, DateTimeUnit.MONTH)
            ActivityBucket(
                label = monthNamesShort[month.month.number - 1],
                value = metric.valueOf(grouped[month.year to month.month.number].orEmpty()),
                isCurrent = back == 0,
            )
        }
    }

    private const val DAYS_PER_WEEK = 7
    private const val WEEKS_IN_MONTH = 5
    /** Calendar months spanned by "Last 3 months" — the filter window and the bars share it, which
     *  is the whole point of [UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001]. */
    private const val MONTHLY_BUCKETS = 3
    /** How many months the "All" chart shows. Older sessions still count in the total — a
     *  deliberate, documented cap, unlike the two mismatches this ticket fixed. [Task 4] */
    private const val ALL_MONTHS_CAP = 6
}
