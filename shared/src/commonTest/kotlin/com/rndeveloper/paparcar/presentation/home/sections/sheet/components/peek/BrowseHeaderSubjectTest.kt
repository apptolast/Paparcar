package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Who owns the browse header — the lane on show or the parked car below it.
 * [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
 * [HOME-AN-EMPTY-LANE-HANDS-THE-HEADER-BACK-TO-THE-CAR-001]
 */
class BrowseHeaderSubjectTest {

    @Test
    fun should_speakForTheCar_when_collapsedAndBrowseNeverOpened() {
        assertFalse(
            browseHeaderFollowsLane(
                expanded = false,
                laneHeaderLatched = false,
                laneCount = 7,
                laneLoading = false,
                hasParkedCar = true,
            ),
        )
    }

    @Test
    fun should_speakForTheLane_when_collapsedAfterBrowseWasOpenedAndTheLaneHasResults() {
        assertTrue(
            browseHeaderFollowsLane(
                expanded = false,
                laneHeaderLatched = true,
                laneCount = 4,
                laneLoading = false,
                hasParkedCar = true,
            ),
        )
    }

    /** The ticket: nothing in the selected section, so the car takes its header back. */
    @Test
    fun should_speakForTheCar_when_collapsedAndTheSelectedLaneIsEmpty() {
        assertFalse(
            browseHeaderFollowsLane(
                expanded = false,
                laneHeaderLatched = true,
                laneCount = 0,
                laneLoading = false,
                hasParkedCar = true,
            ),
        )
    }

    /** A lane nobody asked has nothing to show either — and it prints no `0` doing it. */
    @Test
    fun should_speakForTheCar_when_collapsedAndTheSelectedLaneWasNeverAsked() {
        assertFalse(
            browseHeaderFollowsLane(
                expanded = false,
                laneHeaderLatched = true,
                laneCount = null,
                laneLoading = false,
                hasParkedCar = true,
            ),
        )
    }

    /** Asking is not empty: the breathing capsule keeps the header until the answer lands. */
    @Test
    fun should_speakForTheLane_when_theLaneIsStillAsking() {
        assertTrue(
            browseHeaderFollowsLane(
                expanded = false,
                laneHeaderLatched = true,
                laneCount = null,
                laneLoading = true,
                hasParkedCar = true,
            ),
        )
        assertTrue(
            browseHeaderFollowsLane(
                expanded = false,
                laneHeaderLatched = true,
                laneCount = 0,
                laneLoading = true,
                hasParkedCar = true,
            ),
        )
    }

    /** No car parked → nothing to hand the header to; the lane keeps it. */
    @Test
    fun should_speakForTheLane_when_theLaneIsEmptyButNoCarIsParked() {
        assertTrue(
            browseHeaderFollowsLane(
                expanded = false,
                laneHeaderLatched = true,
                laneCount = 0,
                laneLoading = false,
                hasParkedCar = false,
            ),
        )
    }

    /** Expanded, the lane always heads its own list — empty or not, latched or not. */
    @Test
    fun should_speakForTheLane_when_expanded() {
        assertTrue(
            browseHeaderFollowsLane(
                expanded = true,
                laneHeaderLatched = false,
                laneCount = 0,
                laneLoading = false,
                hasParkedCar = true,
            ),
        )
    }
}
