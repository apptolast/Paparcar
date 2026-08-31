@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.detection.ParkingDetectionSource
import com.rndeveloper.paparcar.domain.detection.detectionSource
import com.rndeveloper.paparcar.domain.model.UserParking
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
            HistoryFilter.ThisMonth -> LocalDate(today.year, today.month, 1)
            // The 1st of the month (MONTHLY_BUCKETS - 1) months back: three calendar months
            // INCLUDING the current one, which is exactly the span the three bars cover.
            HistoryFilter.Last3Months ->
                LocalDate(today.year, today.month, 1).minus(MONTHLY_BUCKETS - 1, DateTimeUnit.MONTH)
        }
        return startDate.atStartOfDayIn(tz).toEpochMilliseconds()
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

    /** Aggregate insights over the ENDED sessions, or null when there is no history to speak of.
     *  Everything here reads persisted fields — no polyline decoding in this hot path (it runs on
     *  every Room emission, per vehicle). [VEH-STATS-SAY-SOMETHING-USEFUL-001] */
    fun computeStats(sessions: List<UserParking>): HistoryStatsData? {
        if (sessions.isEmpty()) return null
        val ended = sessions.filter { !it.isActive }

        val peakDay: Int? = run {
            if (ended.size < MIN_SESSIONS_FOR_PEAK) return@run null
            val tz = TimeZone.currentSystemDefault()
            ended
                .groupBy<UserParking, Int> {
                    Instant.fromEpochMilliseconds(it.location.timestamp)
                        .toLocalDateTime(tz).date.dayOfWeek.isoDayNumber
                }
                .maxByOrNull { it.value.size }
                ?.key
        }

        // A street is "usual" when it repeats, not when it merely wins a 1-1 tie.
        val topStreet: String? = ended
            .mapNotNull { it.address?.street?.takeIf { s -> s.isNotBlank() } }
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.takeIf { it.value.size >= MIN_SESSIONS_FOR_STREET }
            ?.key

        // One resolver for "who placed this pin" — never a second set of path strings here.
        // [UI-HISTORY-IDENTITY-AND-SOURCE-001]
        val known = ended.filter { it.detectionSource() != ParkingDetectionSource.Unknown }
        val autoDetected: AutoDetectedShare? = known
            .takeIf { it.size >= MIN_SESSIONS_FOR_AUTO_SHARE }
            ?.let { k ->
                AutoDetectedShare(
                    auto = k.count { it.detectionSource() != ParkingDetectionSource.Manual },
                    known = k.size,
                )
            }

        return HistoryStatsData(
            mostActiveDayOfWeek = peakDay,
            favoriteStreet = topStreet,
            spotsReleasedCount = ended.count { it.publishedSpot },
            autoDetected = autoDetected,
        )
    }

    /** Sum (meters) of the persisted route lengths of [sessions], or null when none carries one —
     *  "no distance data" must render as nothing, never as 0 km. */
    fun sumDistanceMeters(sessions: List<UserParking>): Float? = sessions
        .mapNotNull { it.routeDistanceMeters }
        .takeIf { it.isNotEmpty() }
        ?.sum()

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
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): List<WeekDayStats> = when (filter) {
        HistoryFilter.ThisWeek -> weekDayBuckets(sessions, dayLabels, nowMs)
        HistoryFilter.ThisMonth -> monthWeekBuckets(sessions, nowMs)
        HistoryFilter.Last3Months -> monthlyBuckets(sessions, monthNamesShort, MONTHLY_BUCKETS, nowMs)
        HistoryFilter.All -> monthlyBuckets(sessions, monthNamesShort, ALL_MONTHS_CAP, nowMs)
    }

    /** One bar per day of the CURRENT week, Monday → Sunday — the same week the filter cut. */
    private fun weekDayBuckets(
        sessions: List<UserParking>,
        dayLabels: List<String>,
        nowMs: Long,
    ): List<WeekDayStats> {
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
        val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        val counts = sessions
            .groupingBy { Instant.fromEpochMilliseconds(it.location.timestamp).toLocalDateTime(tz).date }
            .eachCount()
        return (0 until DAYS_PER_WEEK).map { offset ->
            val date = monday.plus(offset, DateTimeUnit.DAY)
            WeekDayStats(
                label = dayLabels[date.dayOfWeek.isoDayNumber - 1],
                sessions = counts[date] ?: 0,
                isCurrent = date == today,
            )
        }
    }

    /** One bar per week of the current month, labelled by the week's starting day-of-month. */
    private fun monthWeekBuckets(sessions: List<UserParking>, nowMs: Long): List<WeekDayStats> {
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
        val currentWeek = (today.day - 1) / DAYS_PER_WEEK
        val counts = IntArray(WEEKS_IN_MONTH)
        sessions.forEach { s ->
            val d = Instant.fromEpochMilliseconds(s.location.timestamp).toLocalDateTime(tz).date
            if (d.year == today.year && d.month == today.month) {
                counts[((d.day - 1) / DAYS_PER_WEEK).coerceAtMost(WEEKS_IN_MONTH - 1)]++
            }
        }
        return (0 until WEEKS_IN_MONTH).map { i ->
            WeekDayStats(
                label = "${i * DAYS_PER_WEEK + 1}",
                sessions = counts[i],
                isCurrent = i == currentWeek,
            )
        }
    }

    /** One bar per calendar month for the last [months] months, oldest → newest. */
    private fun monthlyBuckets(
        sessions: List<UserParking>,
        monthNamesShort: List<String>,
        months: Int,
        nowMs: Long,
    ): List<WeekDayStats> {
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
        val grouped = sessions.groupingBy { s ->
            val d = Instant.fromEpochMilliseconds(s.location.timestamp).toLocalDateTime(tz)
            d.year to d.month.number
        }.eachCount()
        val firstOfThisMonth = LocalDate(today.year, today.month, 1)
        return (months - 1 downTo 0).map { back ->
            val month = firstOfThisMonth.minus(back, DateTimeUnit.MONTH)
            WeekDayStats(
                label = monthNamesShort[month.month.number - 1],
                sessions = grouped[month.year to month.month.number] ?: 0,
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
    private const val MIN_SESSIONS_FOR_PEAK = 5
    private const val MIN_SESSIONS_FOR_STREET = 3
    private const val MIN_SESSIONS_FOR_AUTO_SHARE = 5
}
