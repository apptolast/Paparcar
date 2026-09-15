package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one rule every measured sum obeys: it knows what it covers.
 * [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001] [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001]
 */
class ScopedMeasureTest {

    private fun session(
        id: Int,
        distanceMeters: Float? = null,
        searchTimeMs: Long? = null,
    ) = UserParking(
        id = "p$id",
        location = GpsPoint(40.4, -3.7, 8f, id * 1_000L, 0f),
        routeDistanceMeters = distanceMeters,
        searchTimeMs = searchTimeMs,
    )

    @Test
    fun should_sumOnlyWhatWasMeasured_andCountItAgainstTheWholeScope() {
        val distance = sumMeasuredDistance(
            listOf(session(1, distanceMeters = 1_200f), session(2), session(3, distanceMeters = 800f)),
        )
        assertEquals(2_000f, distance?.value)
        assertEquals(2, distance?.fromParkings)
        assertEquals(3, distance?.ofParkings, "the denominator is the scope, not the measured ones")
        assertEquals(false, distance?.isComplete)
    }

    @Test
    fun should_beComplete_when_everyParkingInScopeContributed() {
        val distance = sumMeasuredDistance(
            listOf(session(1, distanceMeters = 1_200f), session(2, distanceMeters = 800f)),
        )
        assertTrue(distance!!.isComplete, "the common case stays a plain figure")
    }

    /** No measurement is not a zero: the figure does not exist, so nothing renders. */
    @Test
    fun should_returnNothing_when_nothingWasMeasured() {
        assertNull(sumMeasuredDistance(listOf(session(1), session(2))))
        assertNull(sumMeasuredSearchTime(listOf(session(1), session(2))))
        assertNull(sumMeasuredDistance(emptyList()))
    }

    /**
     * The search time is the reason this type stopped being about kilometres: it is stamped only
     * when a fresh measured route was attached at confirm, so a partial sum is its NORMAL state.
     * [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001]
     */
    @Test
    fun should_carryCoverage_onTheSearchTime_theSameWayTheDistanceDoes() {
        val search = sumMeasuredSearchTime(
            listOf(
                session(1, searchTimeMs = 120_000L),
                session(2, searchTimeMs = 60_000L),
                session(3),
            ),
        )
        assertEquals(180_000L, search?.value, "time LOST is a sum: it is what the period cost")
        assertEquals(2, search?.fromParkings)
        assertEquals(3, search?.ofParkings)
        assertEquals(false, search?.isComplete)
    }

    /**
     * The two measures are INDEPENDENT. A parking can carry a route distance and no search time
     * (the drive was too short to leave the search radius), so one coverage can never stand in for
     * the other — which is exactly what a single "measured parkings" count would have done.
     */
    @Test
    fun should_keepEachMeasuresCoverageSeparate() {
        val sessions = listOf(
            session(1, distanceMeters = 1_200f, searchTimeMs = 120_000L),
            session(2, distanceMeters = 800f),
        )
        assertEquals(true, sumMeasuredDistance(sessions)?.isComplete)
        assertEquals(false, sumMeasuredSearchTime(sessions)?.isComplete)
        assertEquals(1, sumMeasuredSearchTime(sessions)?.fromParkings)
    }
}
