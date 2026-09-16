package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Who owns the collapsed browse header — the lane on show or the parked car below it — and WHEN that
 * is decided: on the two gestures that bracket browsing, never in between.
 * [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
 * [HOME-AN-EMPTY-LANE-HANDS-THE-HEADER-BACK-TO-THE-CAR-001]
 * [HOME-THE-SUBJECT-IS-CHOSEN-WHEN-THE-SHEET-CLOSES-001]
 */
class BrowseHeaderSubjectTest {

    // ── Does the lane deserve to keep it, asked at the closing ────────────────────────────────────

    @Test
    fun should_keepTheHeader_when_theLaneHasResults() {
        assertTrue(laneKeepsHeaderOnCollapse(laneCount = 4, laneLoading = false, hasCarToSpeakFor = true))
    }

    /** The ticket: nothing in the selected section, so the car takes the header. */
    @Test
    fun should_yieldTheHeader_when_theLaneAnsweredZeroAndThereIsACar() {
        assertFalse(laneKeepsHeaderOnCollapse(laneCount = 0, laneLoading = false, hasCarToSpeakFor = true))
    }

    /** A lane nobody asked has nothing to show either — and it prints no `0` doing it. */
    @Test
    fun should_yieldTheHeader_when_theLaneWasNeverAsked() {
        assertFalse(laneKeepsHeaderOnCollapse(laneCount = null, laneLoading = false, hasCarToSpeakFor = true))
    }

    /** Asking is not empty: a lane mid-question has not answered zero yet. */
    @Test
    fun should_keepTheHeader_when_theLaneIsStillAsking() {
        assertTrue(laneKeepsHeaderOnCollapse(laneCount = null, laneLoading = true, hasCarToSpeakFor = true))
        assertTrue(laneKeepsHeaderOnCollapse(laneCount = 0, laneLoading = true, hasCarToSpeakFor = true))
    }

    /**
     * NO vehicle at all → nothing to hand it to; the lane keeps it even at zero.
     *
     * ⛔ Unparked is NOT this case: a car with no session still says whose it is, that it is not
     * marked, and to go mark it, so it takes the header like any other.
     * [HOME-AN-UNPARKED-CAR-STILL-HAS-SOMETHING-TO-SAY-001]
     */
    @Test
    fun should_keepTheHeader_when_theLaneIsEmptyAndThereIsNoVehicleAtAll() {
        assertTrue(laneKeepsHeaderOnCollapse(laneCount = 0, laneLoading = false, hasCarToSpeakFor = false))
    }

    // ── WHEN the subject is decided ───────────────────────────────────────────────────────────────

    @Test
    fun should_speakForTheCar_when_browseWasNeverOpened() {
        assertFalse(nextLaneHeaderLatch(latched = false, browseOpen = false, laneKeepsHeader = true))
    }

    @Test
    fun should_speakForTheLane_when_browseIsOpened() {
        assertTrue(nextLaneHeaderLatch(latched = false, browseOpen = true, laneKeepsHeader = false))
    }

    @Test
    fun should_speakForTheLane_when_closedOverALaneWithResults() {
        assertTrue(nextLaneHeaderLatch(latched = true, browseOpen = false, laneKeepsHeader = true))
    }

    @Test
    fun should_speakForTheCar_when_closedOverAnEmptyLane() {
        assertFalse(nextLaneHeaderLatch(latched = true, browseOpen = false, laneKeepsHeader = false))
    }

    // ⛔ Sin test para «paneo y el carril se vacía»: que eso NO mueva el sujeto no es una rama de esta
    // función, es que el `snapshotFlow` de HomeScreen solo mira el canto abrir/cerrar y la cifra del
    // carril jamás llega hasta aquí. Un test que lo "comprobara" llamando a esta función con el mismo
    // valor dos veces sería una tautología con nombre de testigo.

    /** Abrir y cerrar sobre ese mismo bloque vacío SÍ devuelve el coche. */
    @Test
    fun should_bringTheCarBack_when_theUserOpensAndClosesOverAnEmptyLane() {
        val reopened = nextLaneHeaderLatch(latched = true, browseOpen = true, laneKeepsHeader = false)
        assertTrue(reopened, "opening always gives the header to the lane you are reading")
        assertFalse(
            nextLaneHeaderLatch(latched = reopened, browseOpen = false, laneKeepsHeader = false),
            "closing over a lane with nothing hands it back to the car",
        )
    }

    /**
     * ⛔ A car that already has the header keeps it through a closing: a lane that refilled on its
     * own must not steal it without the user opening the sheet.
     */
    @Test
    fun should_stayOnTheCar_when_closingWhileTheLaneRefilledByItself() {
        assertFalse(nextLaneHeaderLatch(latched = false, browseOpen = false, laneKeepsHeader = true))
    }
}
