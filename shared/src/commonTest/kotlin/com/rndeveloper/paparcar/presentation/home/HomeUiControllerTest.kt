package com.rndeveloper.paparcar.presentation.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The camera's rank rules. [UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001]
 *
 * Following the driving car is a DEFAULT, not a lock: a place the user asked for revokes it, and it
 * only comes back by an explicit act (the resume FAB, or the next trip). Automatic framing and the
 * follow's own tracking are neutral — if they revoked, opening the app mid-trip (or simply the next
 * GPS fix) would un-follow the car all by itself.
 */
class HomeUiControllerTest {

    private companion object {
        const val SPOT_LAT = 40.4168
        const val SPOT_LON = -3.7038
        const val CAR_LAT = 40.4200
        const val CAR_LON = -3.7100
        const val USER_LAT = 40.4300
        const val USER_LON = -3.7200
    }

    private fun drivingController(): HomeUiController =
        HomeUiController().apply { setDriverFollowActive(true) }

    @Test
    fun should_stopFollowingTheDriver_when_theUserGoesToATappedPlace() {
        val controller = drivingController()

        controller.goToPlace(SPOT_LAT, SPOT_LON)

        assertFalse(controller.followingDriver, "a place the user asked for outranks the followed car")
        val target = assertNotNull(controller.cameraTarget)
        assertEquals(SPOT_LAT, target.lat)
        assertEquals(SPOT_LON, target.lon)
    }

    @Test
    fun should_keepTheTappedPlace_when_theNextGpsFixArrives() {
        // The guarantee the user actually feels: the camera stays where they sent it. Before this,
        // follow stayed engaged and the very next fix pulled the map back onto the car.
        val controller = drivingController()
        controller.goToPlace(SPOT_LAT, SPOT_LON)

        controller.followDriver(CAR_LAT, CAR_LON)

        val target = assertNotNull(controller.cameraTarget)
        assertEquals(SPOT_LAT, target.lat)
        assertEquals(SPOT_LON, target.lon)
    }

    @Test
    fun should_stopFollowingTheDriver_when_theUserFramesCarAndSelf() {
        val controller = drivingController()

        controller.framePlaces(CAR_LAT, CAR_LON, USER_LAT, USER_LON)

        assertFalse(controller.followingDriver)
        val target = assertNotNull(controller.cameraTarget)
        assertEquals(USER_LAT, target.boundsLat2)
        assertEquals(USER_LON, target.boundsLon2)
    }

    @Test
    fun should_keepFollowingTheDriver_when_theCameraTracksThePuck() {
        val controller = drivingController()

        controller.followDriver(CAR_LAT, CAR_LON)
        controller.followDriver(CAR_LAT + 0.001, CAR_LON + 0.001)

        assertTrue(controller.followingDriver, "the follow must not revoke itself one fix in")
        val target = assertNotNull(controller.cameraTarget)
        assertEquals(CAR_LAT + 0.001, target.lat)
    }

    @Test
    fun should_keepFollowingTheDriver_when_theInitialFocusCentresOnTheUser() {
        // Opening the app mid-trip: centring on the user IS centring on the car being driven
        // ([DET-READY-TRIP-OVER-PARKED-001]). Automatic framing is not a request.
        val controller = drivingController()

        controller.centerInitialFocus(
            parking = null,
            selectedSpot = null,
            user = USER_LAT to USER_LON,
        )

        assertTrue(controller.followingDriver)
        assertEquals(USER_LAT, assertNotNull(controller.cameraTarget).lat)
    }

    @Test
    fun should_keepFollowingTheDriver_when_aParkedCarIsFramedOnArrival() {
        val controller = HomeUiController()
        controller.centerInitialFocus(parking = null, selectedSpot = null, user = USER_LAT to USER_LON)
        controller.setDriverFollowActive(true)

        controller.refocusOnParkingArrival(
            parking = CAR_LAT to CAR_LON,
            user = USER_LAT to USER_LON,
        )

        assertTrue(controller.followingDriver)
    }

    @Test
    fun should_followTheDriverAgain_when_theUserResumesAfterTappingAPlace() {
        val controller = drivingController()
        controller.goToPlace(SPOT_LAT, SPOT_LON)

        controller.resumeDriverFollow(CAR_LAT, CAR_LON)

        assertTrue(controller.followingDriver)
        assertEquals(CAR_LAT, assertNotNull(controller.cameraTarget).lat)
    }

    @Test
    fun should_moveTheCameraTwice_when_theSamePlaceIsTappedAgain() {
        // The token is what makes a repeated tap re-fire the map's one-shot animation.
        val controller = HomeUiController()

        controller.goToPlace(SPOT_LAT, SPOT_LON)
        val first = assertNotNull(controller.cameraTarget).token
        controller.goToPlace(SPOT_LAT, SPOT_LON)

        assertEquals(first + 1, assertNotNull(controller.cameraTarget).token)
    }

    @Test
    fun should_stopFollowingTheDriver_when_aFingerLandsOnTheMap() {
        // Unchanged guard: a real touch still pauses follow with zero latency. [DRIVE-PUCK-NATIVE-001]
        val controller = drivingController()

        controller.onUserMapGesture()

        assertFalse(controller.followingDriver)
    }
}
