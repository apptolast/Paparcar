package com.rndeveloper.paparcar.presentation.home.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** The reusable half of "a layer shows what you look at". [FUEL-LAYER-FOLLOWS-THE-CAMERA-001] */
class ViewportRefetchTest {

    @Test
    fun should_ask_when_thereIsNoAnchorYet() {
        assertEquals(true, ViewportRefetch.shouldRefetch(null, null, 40.4168, -3.7038, 5_000))
    }

    @Test
    fun should_stayQuiet_when_theCameraSettledUnderHalfTheRadiusAway() {
        // ~1.1 km due north of the anchor — well under 2.5 km.
        assertEquals(
            false,
            ViewportRefetch.shouldRefetch(40.4168, -3.7038, 40.4268, -3.7038, 5_000),
        )
    }

    @Test
    fun should_refuseToAsk_at_theGulfOfGuinea() {
        // (0,0) is what a map reports before it has a camera — measured on device as an actual
        // request ("0 stations around 0.0,0.0"). Same trap SPOT-A-REPORT-WITHOUT-A-PLACE closed.
        assertEquals(false, ViewportRefetch.isWorthAsking(0.0, 0.0, 40.4168, -3.7038))
        assertEquals(false, ViewportRefetch.isWorthAsking(0.0, 0.0, null, null))
    }

    @Test
    fun should_followTheCamera_onlyWithinTheUsersOwnRegion() {
        val madridLat = 40.4168
        val madridLon = -3.7038
        // Toledo (~65 km): past the limit — a refuel question nobody is asking.
        assertEquals(false, ViewportRefetch.isWorthAsking(39.8628, -4.0273, madridLat, madridLon))
        // Alcalá de Henares (~30 km): the real case, "I am driving there".
        assertEquals(true, ViewportRefetch.isWorthAsking(40.4820, -3.3635, madridLat, madridLon))
        // No fix yet: nothing to measure against, so the camera is trusted.
        assertEquals(true, ViewportRefetch.isWorthAsking(48.8566, 2.3522, null, null))
    }

    @Test
    fun should_askAgain_when_theCameraSettledPastHalfTheRadius() {
        // ~3.3 km due north — past the 2.5 km line, and the visible edge would be running dry.
        assertEquals(
            true,
            ViewportRefetch.shouldRefetch(40.4168, -3.7038, 40.4468, -3.7038, 5_000),
        )
    }
}
