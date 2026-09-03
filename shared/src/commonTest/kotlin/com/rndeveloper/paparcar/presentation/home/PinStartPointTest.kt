package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where a pin mode is allowed to start. [SPOT-A-REPORT-WITHOUT-A-PLACE-MUST-NOT-HAPPEN-001]
 *
 * The rule this file exists for is the LAST case: with neither camera nor fix the answer is null,
 * not a coordinate. Report mode used to default to `0.0, 0.0` there — the Gulf of Guinea — and a
 * spot published at sea is permanent garbage on every other user's map.
 */
class PinStartPointTest {

    private val gps = GpsPoint(36.6119, -6.2805, accuracy = 9f, timestamp = 1_000L, speed = 0f)

    @Test
    fun should_startFromTheCamera_when_theMapHasDrawnAFrame() {
        val controller = HomeUiController().apply { onCameraMoved(40.4168, -3.7038) }

        assertEquals(40.4168 to -3.7038, pinStartPoint(controller, HomeState(userGpsPoint = gps)))
    }

    @Test
    fun should_fallBackToTheFix_when_theMapHasNotReportedYet() {
        assertEquals(
            gps.latitude to gps.longitude,
            pinStartPoint(HomeUiController(), HomeState(userGpsPoint = gps)),
        )
    }

    @Test
    fun should_refuseToStart_when_thereIsNeitherCameraNorFix() {
        assertNull(
            pinStartPoint(HomeUiController(), HomeState()),
            "no place is an ANSWER: inventing 0.0, 0.0 publishes a spot in the middle of the ocean",
        )
    }
}
