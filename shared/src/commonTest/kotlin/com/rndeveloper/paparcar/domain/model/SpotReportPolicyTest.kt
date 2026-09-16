package com.rndeveloper.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [SPOT-A-REPORT-YOU-CANNOT-SEE-IS-NOT-A-REPORT-001] The gate that decides whether the pin under the
 * camera may become a spot the whole neighbourhood drives towards.
 */
class SpotReportPolicyTest {

    @Test
    fun should_allowReport_when_standingAtTheSpot() {
        assertEquals(SpotReportGate.ALLOWED, SpotReportPolicy.gateFor(0.0))
    }

    @Test
    fun should_allowReport_when_exactlyAtTheDistanceLimit() {
        // The limit is INSIDE the gate: a driver standing at the edge of it is not a liar.
        assertEquals(
            SpotReportGate.ALLOWED,
            SpotReportPolicy.gateFor(SpotReportPolicy.MAX_REPORT_DISTANCE_METERS),
        )
    }

    @Test
    fun should_refuseReport_when_justPastTheDistanceLimit() {
        assertEquals(
            SpotReportGate.TOO_FAR,
            SpotReportPolicy.gateFor(SpotReportPolicy.MAX_REPORT_DISTANCE_METERS + 0.1),
        )
    }

    @Test
    fun should_refuseReport_when_panningToAnotherCity() {
        // The bug that opened the ticket: the report pin is the camera centre, so panning the map
        // used to publish a spot 40 km away under your own UID.
        assertEquals(SpotReportGate.TOO_FAR, SpotReportPolicy.gateFor(40_000.0))
    }

    @Test
    fun should_sayNoFix_when_distanceIsUnknown() {
        // NOT too-far: no fix is a different sentence, and the peek owes the user the right one.
        assertEquals(SpotReportGate.NO_FIX, SpotReportPolicy.gateFor(null))
    }

    @Test
    fun should_stayWiderThanTheVoteGate() {
        // Voting certifies what you have in front of you; reporting also covers the spot you saw
        // driving past. Two questions, two constants, neither derived from the other.
        assertTrue(
            SpotReportPolicy.MAX_REPORT_DISTANCE_METERS > SpotVotePolicy.MAX_VOTE_DISTANCE_METERS,
        )
    }
}
