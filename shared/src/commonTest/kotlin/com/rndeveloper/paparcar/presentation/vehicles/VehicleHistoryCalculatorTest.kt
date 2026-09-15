package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.usecase.stats.ComputePeriodStatsUseCase
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.rndeveloper.paparcar.domain.detection.DetectionPath

/** [AUDIT-M11-001] History filtering + stat aggregation, now testable outside the ViewModel.
 *  Stats semantics: user-facing metrics with significance thresholds.
 *  [VEH-STATS-SAY-SOMETHING-USEFUL-001] */
class VehicleHistoryCalculatorTest {

    private val nowMs = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    private fun session(
        atMs: Long,
        active: Boolean = false,
        street: String? = null,
        publishedSpot: Boolean = false,
        spotType: SpotType = SpotType.AUTO_DETECTED,
        detectionPath: String? = null,
        distanceMeters: Float? = null,
    ) = UserParking(
        id = "s-$atMs-${street ?: ""}-$detectionPath",
        location = GpsPoint(0.0, 0.0, accuracy = 5f, timestamp = atMs, speed = 0f),
        isActive = active,
        address = street?.let { AddressInfo(street = it, city = null, region = null, country = null) },
        publishedSpot = publishedSpot,
        spotType = spotType,
        detectionPath = detectionPath,
        routeDistanceMeters = distanceMeters,
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

    // ── previousWindow [STATS-CORE-001] ──────────────────────────────────────

    @Test
    fun should_haveNoPreviousWindow_forAllFilter() {
        // "Everything ever" has no previous everything — the comparison simply does not exist.
        assertNull(VehicleHistoryCalculator.previousWindow(HistoryFilter.All, nowMs))
    }

    @Test
    fun should_endThePreviousWindow_exactlyWhereTheCurrentOneStarts() {
        // ▲/▼ compares ADJACENT periods: a gap or an overlap would count a session twice or never.
        listOf(HistoryFilter.ThisWeek, HistoryFilter.ThisMonth, HistoryFilter.Last3Months).forEach { f ->
            val currentStart = VehicleHistoryCalculator.windowStartMs(f, nowMs)!!
            val previous = VehicleHistoryCalculator.previousWindow(f, nowMs)!!
            assertEquals(currentStart - 1, previous.last, "$f must touch the current window")
            assertTrue(previous.first < previous.last, "$f previous window must not be empty")
        }
    }

    @Test
    fun should_spanExactlySevenDays_forThePreviousWeek() {
        val currentStart = VehicleHistoryCalculator.windowStartMs(HistoryFilter.ThisWeek, nowMs)!!
        val previous = VehicleHistoryCalculator.previousWindow(HistoryFilter.ThisWeek, nowMs)!!
        assertEquals(7 * dayMs, currentStart - previous.first)
    }

    // ── Threshold cases inherited from the old presentation-side computeStats [STATS-CORE-001].
    // They asserted through HistoryStatsData while that mapper existed; STATS-UI-001 deleted it
    // (the figures card reads PeriodStats directly), so they now assert on the aggregate itself —
    // same inputs, same expectations, one less indirection to drift.

    private val computePeriodStats = ComputePeriodStatsUseCase()


    @Test
    fun should_returnNull_whenNoSessions() {
        assertNull(computePeriodStats(emptyList()))
    }

    @Test
    fun should_suppressFavoriteStreet_whenNoStreetRepeatsThreeTimes() {
        // Winning a 2-1 tie is not a habit — below the threshold nothing is claimed.
        val stats = computePeriodStats(
            listOf(
                session(nowMs - 20 * dayMs, street = "Calle A"),
                session(nowMs - 19 * dayMs, street = "Calle A"),
                session(nowMs - 18 * dayMs, street = "Calle B"),
            ),
        )
        assertNull(stats?.favoriteStreet)
    }

    @Test
    fun should_pickFavoriteStreet_whenAStreetReachesThreeSessions() {
        val stats = computePeriodStats(
            listOf(
                session(nowMs - 21 * dayMs, street = "Calle A"),
                session(nowMs - 20 * dayMs, street = "Calle A"),
                session(nowMs - 19 * dayMs, street = "Calle A"),
                session(nowMs - 18 * dayMs, street = "Calle B"),
            ),
        )
        assertEquals("Calle A", stats?.favoriteStreet)
    }

    @Test
    fun should_countSpotsReleased_overEndedSessionsOnly() {
        val stats = computePeriodStats(
            listOf(
                session(nowMs - 3 * dayMs, publishedSpot = true),
                session(nowMs - 2 * dayMs, publishedSpot = false),
                session(nowMs - dayMs, publishedSpot = true),
                // Active sessions are not completed parks — never counted.
                session(nowMs, active = true, publishedSpot = true),
            ),
        )
        assertEquals(2, stats?.spotsReleased)
    }

    @Test
    fun should_suppressAutoShare_whenFewerThanFiveKnownProvenanceSessions() {
        val stats = computePeriodStats(
            List(4) { i -> session(nowMs - i * dayMs, detectionPath = DetectionPath.StepsEgress.label) } +
                // Legacy rows (null path, AUTO_DETECTED) have UNKNOWN provenance — they neither
                // count toward the threshold nor drag the ratio down.
                List(10) { i -> session(nowMs - (20 + i) * dayMs) },
        )
        assertEquals(0, stats?.detectionKnownCount, "below the threshold nothing is claimed")
    }

    @Test
    fun should_computeAutoShare_overKnownProvenanceSessions() {
        val stats = computePeriodStats(
            listOf(
                session(nowMs - 6 * dayMs, detectionPath = DetectionPath.StepsEgress.label),
                session(nowMs - 5 * dayMs, detectionPath = DetectionPath.KinematicEgress.label),
                session(nowMs - 4 * dayMs, detectionPath = DetectionPath.Bt.label),
                session(nowMs - 3 * dayMs, detectionPath = DetectionPath.VehicleExitWindow.label),
                // Placed by the user's hand — known provenance, not auto.
                session(nowMs - 2 * dayMs, spotType = SpotType.MANUAL_REPORT, detectionPath = DetectionPath.ManualPin.label),
                // Legacy row: unknown provenance, excluded from both counts.
                session(nowMs - dayMs),
            ),
        )
        assertEquals(4, stats?.autoDetectedCount)
        assertEquals(5, stats?.detectionKnownCount)
    }

    @Test
    fun should_suppressPeakDay_belowFiveEndedSessions() {
        val stats = computePeriodStats(
            List(4) { i -> session(nowMs - i * 7 * dayMs) },
        )
        assertNull(stats?.mostActiveDayOfWeek)
    }

    // ── sumDistanceMeters ───────────────────────────────────────────────────

    @Test
    fun should_returnNullDistance_whenNoSessionCarriesOne() {
        // No data must render as nothing — never as "0 km".
        assertNull(VehicleHistoryCalculator.sumDistanceMeters(listOf(session(nowMs))))
    }

    @Test
    fun should_sumPersistedDistances_ignoringRoutelessSessions() {
        val total = VehicleHistoryCalculator.sumDistanceMeters(
            listOf(
                session(nowMs - 2 * dayMs, distanceMeters = 1200f),
                session(nowMs - dayMs),
                session(nowMs, distanceMeters = 800f),
            ),
        )
        assertEquals(2000f, total?.value)
    }

    // [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001] The sum above is over TWO of three parkings.
    // Summing routeless sessions as zero is right; presenting the result as the scope's total is
    // not, and the only thing that can stop a call site doing it is the coverage travelling with
    // the number.

    @Test
    fun should_reportPartialCoverage_when_someSessionsCarryNoRoute() {
        val distance = VehicleHistoryCalculator.sumDistanceMeters(
            listOf(
                session(nowMs - 2 * dayMs, distanceMeters = 1200f),
                session(nowMs - dayMs),
                session(nowMs, distanceMeters = 800f),
            ),
        )
        assertEquals(2, distance?.fromParkings)
        assertEquals(3, distance?.ofParkings)
        assertEquals(false, distance?.isComplete, "2 of 3 parkings is not a total")
    }

    @Test
    fun should_reportCompleteCoverage_when_everySessionCarriesARoute() {
        val distance = VehicleHistoryCalculator.sumDistanceMeters(
            listOf(
                session(nowMs - dayMs, distanceMeters = 1200f),
                session(nowMs, distanceMeters = 800f),
            ),
        )
        assertEquals(true, distance?.isComplete, "the common case must stay a plain figure")
    }
}
