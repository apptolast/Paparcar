package com.rndeveloper.paparcar.domain.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule under test: **a step completes on measured state, never on a tap**, and once banked it
 * never walks backwards. [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
 */
class FirstStepsTest {

    private fun resolve(
        done: Set<FirstStep> = emptySet(),
        dismissed: Boolean = false,
        hasActiveSession: Boolean = false,
        isWatching: Boolean = false,
        hasTouchedSpots: Boolean = false,
    ) = resolveFirstSteps(done, dismissed, hasActiveSession, isWatching, hasTouchedSpots)

    @Test
    fun should_askForTheFirstStep_when_nothingHasHappenedYet() {
        val progress = resolve()
        assertEquals(FirstStep.MARK_PARKING, progress.current)
        assertTrue(progress.done.isEmpty())
        assertTrue(progress.isVisible)
        assertFalse(progress.isComplete)
    }

    @Test
    fun should_completeMarkParking_when_aSessionExists_withoutAnyPersistedLatch() {
        // The whole point: no tap was recorded anywhere, the car is simply marked.
        val progress = resolve(hasActiveSession = true)
        assertTrue(FirstStep.MARK_PARKING in progress.done)
        assertEquals(FirstStep.UNDERSTAND_WATCH, progress.current)
    }

    @Test
    fun should_keepMarkParkingDone_when_theSessionIsReleasedAfterBeingBanked() {
        // Releasing the parking must not walk the tutorial backwards — the latch is why it doesn't.
        val progress = resolve(done = setOf(FirstStep.MARK_PARKING), hasActiveSession = false)
        assertTrue(FirstStep.MARK_PARKING in progress.done)
        assertEquals(FirstStep.UNDERSTAND_WATCH, progress.current)
    }

    @Test
    fun should_notCompleteUnderstandWatch_when_theWatchIsNotLive() {
        // isWatching is the HONEST badge, not "a session exists": a killed foreground service must
        // not tick the step that claims the car is being watched. [DET-WATCH-HONEST-001]
        val progress = resolve(hasActiveSession = true, isWatching = false)
        assertFalse(FirstStep.UNDERSTAND_WATCH in progress.done)
        assertEquals(FirstStep.UNDERSTAND_WATCH, progress.current)
    }

    @Test
    fun should_stillAskToMarkParking_when_aSpotWasTouchedFirst() {
        // Out-of-order progress banks step 3 but does NOT skip step 1 — nothing is watching the car.
        val progress = resolve(hasTouchedSpots = true)
        assertEquals(FirstStep.MARK_PARKING, progress.current)
        assertEquals(setOf(FirstStep.FIND_SPOT), progress.done)
    }

    @Test
    fun should_reportComplete_when_allThreeSignalsAreSatisfied() {
        val progress = resolve(hasActiveSession = true, isWatching = true, hasTouchedSpots = true)
        assertTrue(progress.isComplete)
        assertEquals(null, progress.current)
        // Still visible: the closing card is what the user dismisses, so the checklist does not
        // vanish under the finger that finished it.
        assertTrue(progress.isVisible)
    }

    @Test
    fun should_hideEverything_when_theUserDismissed() {
        assertFalse(resolve(dismissed = true).isVisible)
        // Dismissing hides; it does not fake progress. Restarting from Settings must find the real
        // state, not a set someone filled in on the way out.
        assertEquals(FirstStep.MARK_PARKING, resolve(dismissed = true).current)
    }

    @Test
    fun should_beIndependentOfSetOrder_when_theLatchArrivesShuffled() {
        val a = resolve(done = setOf(FirstStep.FIND_SPOT, FirstStep.MARK_PARKING))
        val b = resolve(done = setOf(FirstStep.MARK_PARKING, FirstStep.FIND_SPOT))
        assertEquals(a, b)
        assertEquals(FirstStep.UNDERSTAND_WATCH, a.current)
    }
}
