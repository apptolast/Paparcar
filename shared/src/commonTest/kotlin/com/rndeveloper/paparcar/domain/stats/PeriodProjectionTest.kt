package com.rndeveloper.paparcar.domain.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where a period is heading, and when saying so would be dishonest.
 *  [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001] */
class PeriodProjectionTest {

    private val day = 24L * 60 * 60 * 1000
    private val month = 30 * day

    /**
     * ⛔ Two days of a month multiplied by fifteen is not a forecast — it is a multiplication with a
     * forecast's face on it. The gate is what separates the two.
     */
    @Test
    fun should_refuseToProject_when_almostNothingHasHappenedYet() {
        assertNull(projectPeriod(value = 4.0, elapsedMs = 2 * day, windowMs = month))
    }

    /** And past nine tenths the projection IS the figure, so saying it again only adds a number. */
    @Test
    fun should_refuseToProject_when_thePeriodIsAlmostOver() {
        assertNull(projectPeriod(value = 40.0, elapsedMs = 29 * day, windowMs = month))
    }

    @Test
    fun should_projectTheRest_when_halfThePeriodHasHappened() {
        val projection = projectPeriod(value = 20.0, elapsedMs = 15 * day, windowMs = month)
        assertNotNull(projection)
        assertEquals(40.0, projection.value, 0.01)
        assertEquals(0.5, projection.elapsedFraction, 0.01)
    }

    @Test
    fun should_returnNothing_when_thereIsNoPeriod() {
        assertNull(projectPeriod(value = 10.0, elapsedMs = 0L, windowMs = month))
        assertNull(projectPeriod(value = 10.0, elapsedMs = day, windowMs = 0L))
    }

    /**
     * ⛔ A year is never extrapolated from a week: seven days sample somebody's WEEK, not their year,
     * and one holiday moves the figure by a third.
     */
    @Test
    fun should_refuseTheYear_when_theBasisIsAWeek() {
        assertNull(projectYear(value = 20.0, elapsedMs = 7 * day))
        assertNotNull(projectYear(value = 20.0, elapsedMs = 25 * day))
    }

    @Test
    fun should_scaleTheYear_byTheDailyPace() {
        val yearly = projectYear(value = 30.0, elapsedMs = 30 * day)
        assertNotNull(yearly)
        // 1 €/day over a year.
        assertTrue(yearly > 360.0 && yearly < 370.0, "365 días a un euro: $yearly")
    }

    @Test
    fun should_refuseTheYear_when_thereIsNothingToProject() {
        assertNull(projectYear(value = 0.0, elapsedMs = 30 * day))
    }
}
