package com.rndeveloper.paparcar.domain.detection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BtTripShutoffTest {

    private val start = 1_000_000L
    private val afterWindow = start + BtTripShutoff.NULL_TRIP_WINDOW_MS + 1

    @Test
    fun `should keep tracking while the observation window is still open`() {
        assertFalse(
            BtTripShutoff.isNullTrip(
                startedAtMs = start,
                nowMs = start + BtTripShutoff.NULL_TRIP_WINDOW_MS - 1,
                sawDrivingSpeed = false,
                maxDisplacementMeters = null,
            ),
        )
    }

    @Test
    fun `should shut off when the window elapsed with no driving and no displacement`() {
        assertTrue(
            BtTripShutoff.isNullTrip(start, afterWindow, sawDrivingSpeed = false, maxDisplacementMeters = 12.0),
        )
    }

    @Test
    fun `should shut off when no fix at all arrived - no fixes is not an alibi`() {
        assertTrue(
            BtTripShutoff.isNullTrip(start, afterWindow, sawDrivingSpeed = false, maxDisplacementMeters = null),
        )
    }

    @Test
    fun `should keep tracking when a driving-speed fix was seen`() {
        assertFalse(
            BtTripShutoff.isNullTrip(start, afterWindow, sawDrivingSpeed = true, maxDisplacementMeters = 10.0),
        )
    }

    @Test
    fun `should keep tracking when displacement cleared the radius even at crawling speed`() {
        assertFalse(
            BtTripShutoff.isNullTrip(
                start, afterWindow,
                sawDrivingSpeed = false,
                maxDisplacementMeters = BtTripShutoff.NULL_TRIP_RADIUS_M,
            ),
        )
    }
}
