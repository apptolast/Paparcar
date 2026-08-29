@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.detection.ParkingDetectionSource
import com.rndeveloper.paparcar.domain.detection.detectionSource
import com.rndeveloper.paparcar.domain.model.UserParking
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

    private const val MONTHS_3_MS = 90L * 24 * 60 * 60 * 1000
    private const val MIN_SESSIONS_FOR_PEAK = 5
    private const val MIN_SESSIONS_FOR_STREET = 3
    private const val MIN_SESSIONS_FOR_AUTO_SHARE = 5
}
