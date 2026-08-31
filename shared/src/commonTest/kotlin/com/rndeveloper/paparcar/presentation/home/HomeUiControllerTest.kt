package com.rndeveloper.paparcar.presentation.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        const val ASK_LAT = 40.4250
        const val ASK_LON = -3.7150
        const val ASK_AT_MS = 1_700_000_000_000L
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

    // ── The asked place as a camera subject [UI-THE-ASK-IS-A-CAMERA-SUBJECT-001] ──────────────────

    @Test
    fun should_frameTheAskedPlace_when_aQuestionIsOpen() {
        val controller = HomeUiController()

        controller.frameTheAsk(ASK_AT_MS, ASK_LAT to ASK_LON)

        val target = assertNotNull(controller.cameraTarget)
        assertEquals(ASK_LAT, target.lat)
        assertEquals(ASK_LON, target.lon)
        // Tight on the block being asked about, not in bounds with the user: the question is
        // "is the car THERE?", not "how do I walk back to it".
        assertNull(target.boundsLat2, "the asked place is framed alone")
    }

    @Test
    fun should_keepTheAskedPlace_when_theFirstGpsFixArrives() {
        // THE regression test. On a cold open with a question pending there is no session — that is
        // what is being asked — so centerInitialFocus always took its "not parked" branch and pulled
        // the camera onto the user, while the sheet opened on the asked place. Park, walk off, open
        // the app: the two surfaces showed two different places.
        val controller = HomeUiController()
        controller.frameTheAsk(ASK_AT_MS, ASK_LAT to ASK_LON)

        controller.centerInitialFocus(parking = null, selectedSpot = null, user = USER_LAT to USER_LON)

        val target = assertNotNull(controller.cameraTarget)
        assertEquals(ASK_LAT, target.lat, "the question owns the opening frame, not the user")
        assertEquals(ASK_LON, target.lon)
    }

    @Test
    fun should_outrankAParkedCar_when_aQuestionIsOpenAtTheSameTime() {
        // A question and a genuinely parked car can coexist. The question is the one with a deadline
        // and the one the sheet is already open on, so it wins the opening frame.
        val controller = HomeUiController()
        controller.frameTheAsk(ASK_AT_MS, ASK_LAT to ASK_LON)

        controller.centerInitialFocus(
            parking = CAR_LAT to CAR_LON,
            selectedSpot = null,
            user = USER_LAT to USER_LON,
        )

        assertEquals(ASK_LAT, assertNotNull(controller.cameraTarget).lat)
    }

    @Test
    fun should_notMoveTheCameraAgain_when_theSameQuestionIsSeenTwice() {
        // Once per QUESTION, the same unit the sheet's auto-open uses. A re-launched effect (Home
        // recomposing, the window re-emitting) must not yank a user who moved on.
        val controller = HomeUiController()
        controller.frameTheAsk(ASK_AT_MS, ASK_LAT to ASK_LON)
        val first = assertNotNull(controller.cameraTarget).token

        controller.frameTheAsk(ASK_AT_MS, ASK_LAT to ASK_LON)

        assertEquals(first, assertNotNull(controller.cameraTarget).token)
    }

    @Test
    fun should_frameTheAskedPlaceAgain_when_aSecondQuestionIsPosted() {
        val controller = HomeUiController()
        controller.frameTheAsk(ASK_AT_MS, ASK_LAT to ASK_LON)

        controller.frameTheAsk(ASK_AT_MS + 1, CAR_LAT to CAR_LON)

        assertEquals(CAR_LAT, assertNotNull(controller.cameraTarget).lat)
    }

    @Test
    fun should_frameTheParkedCar_when_theAnswerTurnsTheQuestionIntoASession() {
        // Answering "Yes" plants a real pin, which may not be the asked point (the answer runs its own
        // anchor cascade). Framing a question is not framing a session, so [FOCUS-002] stays armed.
        val controller = HomeUiController()
        controller.frameTheAsk(ASK_AT_MS, ASK_LAT to ASK_LON)

        controller.refocusOnParkingArrival(parking = CAR_LAT to CAR_LON, user = null)

        assertEquals(CAR_LAT, assertNotNull(controller.cameraTarget).lat)
    }

    @Test
    fun should_keepFollowingTheDriver_when_anAskedPlaceIsFramed() {
        // Automatic framing is not a request: it must stay neutral on follow like the rest of the
        // focus machinery. [UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001]
        val controller = drivingController()

        controller.frameTheAsk(ASK_AT_MS, ASK_LAT to ASK_LON)

        assertTrue(controller.followingDriver)
    }

    @Test
    fun should_stopFollowingTheDriver_when_aFingerLandsOnTheMap() {
        // Unchanged guard: a real touch still pauses follow with zero latency. [DRIVE-PUCK-NATIVE-001]
        val controller = drivingController()

        controller.onUserMapGesture()

        assertFalse(controller.followingDriver)
    }
}
