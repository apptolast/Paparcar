package com.rndeveloper.paparcar.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParkingReviewMilestoneTest {
    @Test
    fun should_waitForAnotherProcess_when_theFirstProcessReachesTheMilestone() {
        val persisted = mutableListOf<Int>()
        val milestone = ParkingReviewMilestone(0, persisted::add)

        repeat(4) { assertFalse(milestone.recordSuccessfulParking()) }
        assertEquals(listOf(1, 2, 3), persisted)
    }

    @Test
    fun should_requestReview_when_thirdParkingIsSavedInALaterProcess() {
        val milestone = ParkingReviewMilestone(1) {}

        assertFalse(milestone.recordSuccessfulParking())
        assertTrue(milestone.recordSuccessfulParking())
    }

    @Test
    fun should_allowPlayToDecide_when_subsequentParkingsSucceed() {
        val persisted = mutableListOf<Int>()
        val milestone = ParkingReviewMilestone(3, persisted::add)

        assertTrue(milestone.recordSuccessfulParking())
        assertTrue(milestone.recordSuccessfulParking())
        assertTrue(persisted.isEmpty())
    }
}
