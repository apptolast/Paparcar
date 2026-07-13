@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.vehicles

import io.apptolast.paparcar.domain.model.UserParking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [AUDIT-M11-001] Pure history filtering + stat aggregation, extracted out of
 * [VehiclesViewModel] so the (business) computation is unit-testable in isolation instead of being
 * reachable only by driving the whole ViewModel + its flows. No side effects, no state.
 */
object VehicleHistoryCalculator {

    /** Sessions within the time window of [filter]. `nowMs` injectable for deterministic tests. */
    fun filter(
        sessions: List<UserParking>,
        filter: HistoryFilter,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): List<UserParking> = when (filter) {
        HistoryFilter.All -> sessions
        HistoryFilter.ThisWeek -> {
            val tz = TimeZone.currentSystemDefault()
            val nowLocal = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz)
            val daysFromMonday = nowLocal.date.dayOfWeek.isoDayNumber - 1
            val weekStartMs = nowLocal.date
                .minus(daysFromMonday, DateTimeUnit.DAY)
                .atStartOfDayIn(tz)
                .toEpochMilliseconds()
            sessions.filter { it.location.timestamp >= weekStartMs }
        }
        HistoryFilter.ThisMonth -> {
            val tz = TimeZone.currentSystemDefault()
            val nowLocal = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz)
            sessions.filter {
                val dt = Instant.fromEpochMilliseconds(it.location.timestamp).toLocalDateTime(tz)
                dt.year == nowLocal.year && dt.month == nowLocal.month
            }
        }
        HistoryFilter.Last3Months -> sessions.filter {
            it.location.timestamp >= nowMs - MONTHS_3_MS
        }
    }

    /** Aggregate insights over the ENDED sessions, or null when there is no history to speak of. */
    fun computeStats(
        sessions: List<UserParking>,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): HistoryStatsData? {
        if (sessions.isEmpty()) return null
        val ended = sessions.filter { !it.isActive }

        val avgPerWeek: Float? = run {
            val oldest = ended.minOfOrNull { it.location.timestamp } ?: return@run null
            val weeks = (nowMs - oldest).toFloat() / WEEK_MS
            if (weeks < MIN_WEEKS_FOR_AVG) null else ended.size / weeks
        }

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

        val topStreet: String? = ended
            .mapNotNull { it.address?.street?.takeIf { s -> s.isNotBlank() } }
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key

        val avgReliabilityPct: Int? = ended
            .mapNotNull { it.detectionReliability }
            .takeIf { it.isNotEmpty() }
            ?.let { (it.sum() / it.size * PERCENT).toInt() }

        return HistoryStatsData(
            avgSessionsPerWeek = avgPerWeek,
            mostActiveDayOfWeek = peakDay,
            favoriteStreet = topStreet,
            avgReliabilityPct = avgReliabilityPct,
        )
    }

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private const val MONTHS_3_MS = 90L * 24 * 60 * 60 * 1000
    private const val MIN_WEEKS_FOR_AVG = 2f
    private const val MIN_SESSIONS_FOR_PEAK = 5
    private const val PERCENT = 100f
}
