package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [DET-WATCHDOG-DEPARTURE-KNOWS-NO-HOUR-001] */
class FreedSpotIsStillThereTest {

    private val config = ParkingDetectionConfig()
    private val now = 1_787_000_000_000L

    @Test
    fun should_publish_when_the_departure_just_happened() {
        assertTrue(freedSpotIsStillThere(exitAtMs = now - 60_000L, nowMs = now, config = config))
    }

    @Test
    fun should_publish_when_the_departure_is_exactly_at_the_age_limit() {
        val exitAt = now - config.spotPublishMaxAgeMs
        assertTrue(freedSpotIsStillThere(exitAtMs = exitAt, nowMs = now, config = config))
    }

    @Test
    fun should_not_publish_when_the_departure_is_older_than_the_limit() {
        val exitAt = now - config.spotPublishMaxAgeMs - 1L
        assertFalse(freedSpotIsStillThere(exitAtMs = exitAt, nowMs = now, config = config))
    }

    @Test
    fun should_not_publish_when_the_departure_was_recovered_hours_late() {
        // Field 2026-07-06, Redmi: a departure processed 5 h late. [DET-RECONCILE-001]
        assertFalse(freedSpotIsStillThere(exitAtMs = now - 5 * 3_600_000L, nowMs = now, config = config))
    }

    /**
     * The watchdog's "still parked? → I've left" tap. The user witnesses the FACT of having gone,
     * never the HOUR — and an unknown hour must never be read as a recent one.
     */
    @Test
    fun should_not_publish_when_the_hour_of_the_departure_is_unknown() {
        assertFalse(freedSpotIsStillThere(exitAtMs = null, nowMs = now, config = config))
    }
}
