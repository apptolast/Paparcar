package io.apptolast.paparcar.presentation.vehicles

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [AUDIT-M11-001] History filtering + stat aggregation, now testable outside the ViewModel. */
class VehicleHistoryCalculatorTest {

    private val nowMs = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    private fun session(
        atMs: Long,
        active: Boolean = false,
        street: String? = null,
        reliability: Float? = null,
    ) = UserParking(
        id = "s-$atMs",
        location = GpsPoint(0.0, 0.0, accuracy = 5f, timestamp = atMs, speed = 0f),
        isActive = active,
        address = street?.let { AddressInfo(street = it, city = null, region = null, country = null) },
        detectionReliability = reliability,
    )

    // ── filter ────────────────────────────────────────────────────────────────

    @Test
    fun should_returnAll_forAllFilter() {
        val sessions = listOf(session(nowMs - 100 * dayMs), session(nowMs))
        assertEquals(sessions, VehicleHistoryCalculator.filter(sessions, HistoryFilter.All, nowMs))
    }

    @Test
    fun should_dropOlderThan3Months_forLast3MonthsFilter() {
        val recent = session(nowMs - 10 * dayMs)
        val old = session(nowMs - 100 * dayMs)
        val result = VehicleHistoryCalculator.filter(listOf(recent, old), HistoryFilter.Last3Months, nowMs)
        assertEquals(listOf(recent), result)
    }

    // ── computeStats ────────────────────────────────────────────────────────

    @Test
    fun should_returnNull_whenNoSessions() {
        assertNull(VehicleHistoryCalculator.computeStats(emptyList(), nowMs))
    }

    @Test
    fun should_suppressAverage_whenLessThanTwoWeeksOfHistory() {
        // Only a few days of data → avg-per-week is not meaningful yet.
        val stats = VehicleHistoryCalculator.computeStats(
            listOf(session(nowMs - 3 * dayMs), session(nowMs - dayMs)),
            nowMs,
        )
        assertNull(stats?.avgSessionsPerWeek)
    }

    @Test
    fun should_pickFavoriteStreet_byFrequency() {
        val stats = VehicleHistoryCalculator.computeStats(
            listOf(
                session(nowMs - 20 * dayMs, street = "Calle A"),
                session(nowMs - 19 * dayMs, street = "Calle A"),
                session(nowMs - 18 * dayMs, street = "Calle B"),
            ),
            nowMs,
        )
        assertEquals("Calle A", stats?.favoriteStreet)
    }

    @Test
    fun should_averageReliability_asPercent() {
        val stats = VehicleHistoryCalculator.computeStats(
            listOf(
                session(nowMs - 20 * dayMs, reliability = 0.9f),
                session(nowMs - 19 * dayMs, reliability = 0.5f),
            ),
            nowMs,
        )
        assertEquals(70, stats?.avgReliabilityPct)
    }

    @Test
    fun should_excludeActiveSessions_fromStats() {
        // An active (ongoing) session isn't a completed park — it must not skew the averages.
        val stats = VehicleHistoryCalculator.computeStats(
            listOf(
                session(nowMs - 20 * dayMs, street = "Calle A", reliability = 0.9f),
                session(nowMs, active = true, street = "Calle Z", reliability = 0.1f),
            ),
            nowMs,
        )
        assertEquals("Calle A", stats?.favoriteStreet)
        assertEquals(90, stats?.avgReliabilityPct)
    }
}
