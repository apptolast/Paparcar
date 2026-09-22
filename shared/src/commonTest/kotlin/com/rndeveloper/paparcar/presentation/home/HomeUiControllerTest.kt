package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.presentation.map.ZoomIntent
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

    // ── El zoom del seguimiento [HOME-A-LIVE-TRIP-DESERVES-THE-ZOOM-IT-NEEDS-001] ──

    @Test
    fun should_zoomIn_when_theFollowFirstEngages() {
        // ⛔ EL TESTIGO de la issue #66: «al abrir la app en driving, la cámara no va a la
        // localización ni se acerca». Iba al coche; lo que no hacía era acercarse, porque el
        // seguimiento recentraba SIN zoom desde el primer fix — y al abrir en mitad de un viaje el
        // zoom que hay es el de por defecto del mapa, no uno que el usuario haya elegido.
        val controller = drivingController()

        controller.followDriver(CAR_LAT, CAR_LON)

        val target = assertNotNull(controller.cameraTarget)
        assertEquals(CAR_LAT, target.lat)
        assertEquals(
            HomeUiController.FOCUS_DRIVING_ZOOM,
            target.zoom,
            "el primer seguimiento de un viaje es un encuadre, no una actualización de tracking",
        )
        assertEquals(
            ZoomIntent.AtLeast,
            target.zoomIntent,
            "y entra como SUELO: a quien ya miraba de cerca no se le aleja",
        )
    }

    @Test
    fun should_leaveTheZoomAlone_when_theFollowKeepsTracking() {
        // La mitad que ya era correcta y que no se puede perder: una vez siguiendo, la cámara no le
        // deshace el pellizco al usuario en cada fix.
        val controller = drivingController()
        controller.followDriver(CAR_LAT, CAR_LON)

        controller.followDriver(CAR_LAT + 0.001, CAR_LON + 0.001)

        assertNull(
            assertNotNull(controller.cameraTarget).zoom,
            "seguir NO es reencuadrar: el zoom del usuario manda a partir del primero",
        )
    }

    @Test
    fun should_zoomInAgain_when_aNewTripStarts() {
        // Un viaje nuevo es un encuadre nuevo: el anterior terminó y su zoom no dice nada del que
        // empieza.
        val controller = drivingController()
        controller.followDriver(CAR_LAT, CAR_LON)
        controller.followDriver(CAR_LAT + 0.001, CAR_LON + 0.001)

        controller.setDriverFollowActive(false)
        controller.setDriverFollowActive(true)
        controller.followDriver(CAR_LAT, CAR_LON)

        assertEquals(HomeUiController.FOCUS_DRIVING_ZOOM, assertNotNull(controller.cameraTarget).zoom)
    }

    // ── El zoom al REANUDAR [HOME-A-LOCATE-ME-TAP-IS-A-FRAMING-001] ──

    @Test
    fun should_zoomIn_when_theUserTapsLocateMeMidTrip() {
        // ⛔ EL TESTIGO de la issue #115 («el zoom de location en ruta sigue sin funcionar»): el
        // arreglo de la #66 sólo cubrió el encuadre AUTOMÁTICO del primer seguimiento. Este es el
        // camino del FAB, que ya está siguiendo y por tanto nunca vuelve a armar aquel encuadre:
        // recentraba y dejaba la cámara tan lejos como estuviera. Pedir tu localización es pedir un
        // encuadre, la siga la cámara o no.
        val controller = drivingController()
        controller.followDriver(CAR_LAT, CAR_LON)
        controller.followDriver(CAR_LAT + 0.001, CAR_LON + 0.001)

        controller.resumeDriverFollow(CAR_LAT, CAR_LON)

        val target = assertNotNull(controller.cameraTarget)
        assertEquals(CAR_LAT, target.lat)
        assertEquals(
            HomeUiController.FOCUS_DRIVING_ZOOM,
            target.zoom,
            "reanudar es encuadrar: el mismo suelo que el primer seguimiento, no uno nuevo",
        )
        assertEquals(
            ZoomIntent.AtLeast,
            target.zoomIntent,
            "y como SUELO: a quien ya miraba de cerca no se le aleja de golpe",
        )
    }

    @Test
    fun should_reEngageTheFollow_when_theUserTapsLocateMeAfterLeavingIt() {
        // La mitad que ya era correcta: el FAB es una de las DOS únicas puertas por las que el
        // seguimiento vuelve, y encuadrar no puede costarle esa función.
        val controller = drivingController()
        controller.goToPlace(SPOT_LAT, SPOT_LON)

        controller.resumeDriverFollow(CAR_LAT, CAR_LON)

        assertTrue(controller.followingDriver, "el FAB reengancha el seguimiento que el usuario soltó")
        assertEquals(CAR_LAT, assertNotNull(controller.cameraTarget).lat)
    }

    @Test
    fun should_leaveTheZoomAlone_when_aGpsFixArrivesAfterTheUserResumed() {
        // Reanudar no rearma el encuadre para los fixes siguientes: el zoom con el que se quede el
        // usuario tras pulsar sigue siendo suyo.
        val controller = drivingController()
        controller.resumeDriverFollow(CAR_LAT, CAR_LON)

        controller.followDriver(CAR_LAT + 0.001, CAR_LON + 0.001)

        assertNull(
            assertNotNull(controller.cameraTarget).zoom,
            "el encuadre lo pidió la pulsación, no los fixes que vienen detrás",
        )
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

    // ── A hand-panned camera is not up for grabs [PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001] ──
    // The pin the user places IS the camera centre, so an automatic re-frame does not just move the
    // map: it MOVES THE PIN. `refocusOnParkingArrival` has honoured the manual-pan guard since
    // [FOCUS-002]; the initial focus never did, and it is the one that fires on the first GPS fix —
    // i.e. seconds into a cold start, exactly while a new user is dragging the map onto their car.

    // ── Volver a Home vuelve al sitio que elegiste [HOME-WHAT-IS-REMEMBERED-IS-THE-PLACE-YOU-CHOSE-001] ──

    @Test
    fun should_returnToTheChosenView_andRefuseToBeReframed_when_homeIsEnteredAgain() {
        // El controlador nace NUEVO al volver de otra pantalla, así que restaurar el sitio sin
        // restaurar el «no me reencuadres» habría durado exactamente un fix: el primero manda la
        // cámara a tu ubicación mientras la lista sigue hablando de la otra ciudad.
        val controller = HomeUiController()

        controller.restoreChosenView(SPOT_LAT, SPOT_LON)

        assertEquals(SPOT_LAT, controller.cameraTarget?.lat)
        assertEquals(SPOT_LON, controller.cameraTarget?.lon)

        // El primer fix llega y NO se lleva la cámara: el destino sigue siendo el elegido.
        controller.centerInitialFocus(parking = null, selectedSpot = null, user = USER_LAT to USER_LON)
        assertEquals(
            SPOT_LAT,
            controller.cameraTarget?.lat,
            "el primer fix no puede llevarse la cámara del sitio que el usuario eligió",
        )
    }

    @Test
    fun should_notChangeTheZoom_when_restoringTheChosenView() {
        // ⚠️ Restaurar NO es ir: `goToPlace` sube el zoom si hace falta porque es una petición
        // nueva; devolver al usuario donde estaba no puede reencuadrarle de paso.
        val controller = HomeUiController()

        controller.restoreChosenView(SPOT_LAT, SPOT_LON)

        assertNull(controller.cameraTarget?.zoom, "una restauración no trae zoom propio")
    }

    @Test
    fun should_notCentreOnTheUser_when_theyAlreadyPannedTheMapByHand() {
        val controller = HomeUiController()

        controller.onUserMapGesture()
        controller.centerInitialFocus(parking = null, selectedSpot = null, user = USER_LAT to USER_LON)

        assertNull(
            controller.cameraTarget,
            "the first fix must not fly the camera — and with it the pending pin — onto the user",
        )
    }

    @Test
    fun should_notFrameAnythingAutomatically_when_aPinIsBeingPlaced() {
        // Not even without a pan: entering a pin mode pre-centred and waiting for the fix is the
        // very sequence the tutorial walks a new user through.
        //
        // «Pre-centrada» es literal y ahora se simula: el mapa reporta el centro, que es lo que
        // convierte a la cámara en un SITIO que defender. [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001]
        val controller = HomeUiController()
        controller.onCameraMoved(SPOT_LAT, SPOT_LON)
        controller.setPinPlacementActive(true)

        controller.centerInitialFocus(parking = null, selectedSpot = null, user = USER_LAT to USER_LON)
        controller.frameTheAsk(ASK_AT_MS, ASK_LAT to ASK_LON)
        controller.refocusOnParkingArrival(CAR_LAT to CAR_LON, USER_LAT to USER_LON)

        assertNull(controller.cameraTarget, "the finger owns the camera while a pin is in the air")
    }

    // ── Un pin que no está en ningún sitio no supera a nada ──
    // [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001]

    @Test
    fun should_frameTheFirstRealPlace_when_aPinIsBeingPlacedOnACameraNobodyEverSet() {
        // El caso de campo: el aviso abre Home DIRECTAMENTE en modo pin y el arranque en frío todavía
        // no tiene fix, así que el mapa nace en su semilla de emergencia y no reporta centro alguno.
        // El guard de «no muevas un pin colocado» dejaba la cámara clavada ahí para siempre, y el pin
        // se guardaba en el Golfo de Guinea. Sin centro reportado no hay pin que proteger: el primer
        // sitio real manda.
        val controller = HomeUiController()
        controller.setPinPlacementActive(true)

        controller.centerInitialFocus(parking = null, selectedSpot = null, user = USER_LAT to USER_LON)

        val target = assertNotNull(controller.cameraTarget, "nothing to protect: the fix wins")
        assertEquals(USER_LAT, target.lat)
    }

    @Test
    fun should_frameTheParkedCar_when_aPinIsBeingPlacedWithoutLocationAndNothingEverPlacedTheCamera() {
        // Misma ausencia por la otra puerta: sin permiso de ubicación el fix no llega NUNCA, así que
        // el coche aparcado es el único sitio que Home conoce. Con el guard viejo, entrar en modo pin
        // sin fix condenaba la sesión entera a (0,0).
        val controller = HomeUiController()
        controller.setPinPlacementActive(true)

        controller.centerOnParkingWithoutLocation(CAR_LAT to CAR_LON)

        assertEquals(CAR_LAT, assertNotNull(controller.cameraTarget).lat)
    }

    @Test
    fun should_notFrameAnythingAutomatically_when_theCameraWasPlacedAndThenThePinMovedAway() {
        // Y lo que el guard protege sigue protegido: una vez la cámara está posada, arrastrar el pin
        // lejos y que llegue el primer fix NO lo arrastra de vuelta.
        // [PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001]
        val controller = HomeUiController()
        controller.onCameraMoved(CAR_LAT, CAR_LON)
        controller.setPinPlacementActive(true)
        controller.onCameraMoved(SPOT_LAT, SPOT_LON)

        controller.centerInitialFocus(parking = null, selectedSpot = null, user = USER_LAT to USER_LON)

        assertNull(controller.cameraTarget, "a placed pin still outranks the first fix")
    }

    @Test
    fun should_stillFlyToAPlaceTheUserAsksFor_when_aPinIsBeingPlaced() {
        // The guard is about AUTOMATIC framing only. "My location" pressed mid-placement is the user
        // asking to go there, and it must still work.
        val controller = HomeUiController()
        controller.setPinPlacementActive(true)

        controller.goToPlace(SPOT_LAT, SPOT_LON)

        val target = assertNotNull(controller.cameraTarget)
        assertEquals(SPOT_LAT, target.lat)
    }

    @Test
    fun should_frameTheUserAgain_when_thePinModeIsOverAndNothingWasFramedYet() {
        // Standing down must not disarm the one-shot: leaving the mode without ever having framed
        // anything should still let Home open on something meaningful.
        //
        // La cámara se posa primero: si no, no habría stand-down que comprobar y el test pasaría por
        // la razón equivocada. [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001]
        val controller = HomeUiController()
        controller.onCameraMoved(SPOT_LAT, SPOT_LON)
        controller.setPinPlacementActive(true)
        controller.centerInitialFocus(parking = null, selectedSpot = null, user = USER_LAT to USER_LON)

        controller.setPinPlacementActive(false)
        controller.centerInitialFocus(parking = null, selectedSpot = null, user = USER_LAT to USER_LON)

        val target = assertNotNull(controller.cameraTarget)
        assertEquals(USER_LAT, target.lat)
    }

    // ── No location, parked car → the camera goes to the car ──
    // [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]

    @Test
    fun should_flyToTheParkedCar_when_thereIsNoLocationToWaitFor() {
        val controller = HomeUiController()

        controller.centerOnParkingWithoutLocation(CAR_LAT to CAR_LON)

        val target = assertNotNull(controller.cameraTarget)
        assertEquals(CAR_LAT, target.lat)
        assertEquals(CAR_LON, target.lon)
    }

    @Test
    fun should_stayOnTheCar_when_locationArrivesAfterwards() {
        val controller = HomeUiController()
        controller.centerOnParkingWithoutLocation(CAR_LAT to CAR_LON)

        controller.centerInitialFocus(parking = CAR_LAT to CAR_LON, selectedSpot = null, user = USER_LAT to USER_LON)
        controller.refocusOnParkingArrival(parking = CAR_LAT to CAR_LON, user = USER_LAT to USER_LON)

        val target = assertNotNull(controller.cameraTarget)
        assertEquals(CAR_LAT, target.lat)
        assertEquals(null, target.boundsLat2, "no second move onto car + user")
    }
}
