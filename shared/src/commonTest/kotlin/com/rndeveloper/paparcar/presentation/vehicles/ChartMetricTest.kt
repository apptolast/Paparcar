package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the activity chart can draw, and what each bar is worth.
 * [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001]
 */
class ChartMetricTest {

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
    fun should_offerOnlyParkings_when_nothingWasMeasured() {
        val metrics = availableChartMetrics(listOf(session(1), session(2)))
        assertEquals(listOf(ChartMetric.PARKINGS), metrics)
        // One metric means no chips at all — the card draws what it always drew.
        assertFalse(metrics.size > 1)
    }

    @Test
    fun should_offerEachMeasure_when_thePeriodHasIt() {
        assertEquals(
            listOf(ChartMetric.PARKINGS, ChartMetric.DISTANCE),
            availableChartMetrics(listOf(session(1, distanceMeters = 4_200f), session(2))),
        )
        assertEquals(
            listOf(ChartMetric.PARKINGS, ChartMetric.SEARCH_TIME),
            availableChartMetrics(listOf(session(1, searchTimeMs = 120_000L), session(2))),
        )
        assertEquals(
            listOf(ChartMetric.PARKINGS, ChartMetric.DISTANCE, ChartMetric.SEARCH_TIME),
            availableChartMetrics(
                listOf(session(1, distanceMeters = 4_200f, searchTimeMs = 120_000L)),
            ),
        )
    }

    /**
     * Changing the period can take the data out from under a choice — a week with no measured route
     * after a month full of them. A lit chip over an empty chart reads as a bug, so the choice falls
     * back to the one metric that always exists.
     */
    @Test
    fun should_fallBackToParkings_when_theChosenMetricLeftThePeriod() {
        val measured = listOf(session(1, distanceMeters = 4_200f))
        val unmeasured = listOf(session(2))

        assertEquals(ChartMetric.DISTANCE, resolveChartMetric(ChartMetric.DISTANCE, measured))
        assertEquals(ChartMetric.PARKINGS, resolveChartMetric(ChartMetric.DISTANCE, unmeasured))
    }

    @Test
    fun should_measureEachBucket_inItsOwnUnit() {
        val bucket = listOf(
            session(1, distanceMeters = 1_200f, searchTimeMs = 120_000L),
            session(2, distanceMeters = 800f),
        )
        assertEquals(2f, ChartMetric.PARKINGS.valueOf(bucket))
        assertEquals(2_000f, ChartMetric.DISTANCE.valueOf(bucket))
        assertEquals(120_000f, ChartMetric.SEARCH_TIME.valueOf(bucket))
    }

    /**
     * A bucket where nothing was measured is worth zero on the BAR — a bar has one dimension and
     * the value already spent it, so it cannot say "unmeasured". The period's coverage is confessed
     * by the card's qualifier instead. [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
     */
    @Test
    fun should_drawZero_when_aBucketMeasuredNothing() {
        assertEquals(0f, ChartMetric.DISTANCE.valueOf(listOf(session(1))))
        assertEquals(0f, ChartMetric.SEARCH_TIME.valueOf(listOf(session(1))))
        assertEquals(0f, ChartMetric.DISTANCE.valueOf(emptyList()))
    }

    /** A floor travels with its unit: three parkings, ten kilometres, ten minutes. */
    @Test
    fun should_scaleEachMetricAgainstItsOwnFloor() {
        assertTrue(ChartMetric.PARKINGS.scaleFloor < ChartMetric.DISTANCE.scaleFloor)
        assertEquals(600_000f, ChartMetric.SEARCH_TIME.scaleFloor)
    }
}
