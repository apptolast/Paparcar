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
        hasSpotsOnOffer: Boolean = false,
        isAutoDetectionStopped: Boolean = false,
        reinforcement: WatchReinforcement = WatchReinforcement.NONE,
        deferred: Set<FirstStep> = emptySet(),
    ) = resolveFirstSteps(
        done, dismissed, hasActiveSession, isWatching, hasTouchedSpots, hasSpotsOnOffer,
        isAutoDetectionStopped, reinforcement, deferred,
    )

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
        // Detection stopped so the SECOND step stays open and this test keeps asserting what it is
        // about: the first step completing on measured state alone.
        val progress = resolve(hasActiveSession = true, isAutoDetectionStopped = true)
        assertTrue(FirstStep.MARK_PARKING in progress.done)
        assertEquals(FirstStep.UNDERSTAND_WATCH, progress.current)
    }

    @Test
    fun should_keepMarkParkingDone_when_theSessionIsReleasedAfterBeingBanked() {
        // Releasing the parking must not walk the tutorial backwards — the latch is why it doesn't.
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING),
            hasActiveSession = false,
            isAutoDetectionStopped = true,
        )
        assertTrue(FirstStep.MARK_PARKING in progress.done)
        assertEquals(FirstStep.UNDERSTAND_WATCH, progress.current)
    }

    @Test
    fun should_notCompleteUnderstandWatch_when_detectionIsStopped() {
        // What ticks this step is a parking of yours being watched: parked AND detection running.
        // It used to be the strictest badge instead, which let a declined battery exemption wall the
        // user in — see the block below. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
        val progress = resolve(hasActiveSession = true, isAutoDetectionStopped = true)
        assertFalse(FirstStep.UNDERSTAND_WATCH in progress.done)
        assertEquals(FirstStep.UNDERSTAND_WATCH, progress.current)
    }

    @Test
    fun should_notCompleteUnderstandWatch_before_theCarIsEvenParked() {
        // The step teaches what happens when you leave. Ticking it for a user who has never parked
        // would teach nothing and skip straight past the lesson.
        val progress = resolve(hasActiveSession = false, isAutoDetectionStopped = false)
        assertFalse(FirstStep.UNDERSTAND_WATCH in progress.done)
        assertEquals(FirstStep.MARK_PARKING, progress.current)
    }

    @Test
    fun should_keepUnderstandWatchDone_when_theParkingIsReleasedAfterwards() {
        // Same latch as step 1: releasing the parking must not un-teach the lesson.
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH),
            hasActiveSession = false,
        )
        assertTrue(FirstStep.UNDERSTAND_WATCH in progress.done)
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

    // ── A step that asks for a permission never blocks ──
    // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]

    @Test
    fun should_completeUnderstandWatch_when_detectionIsOn_evenWithoutABatteryExemption() {
        // THE BUG, in one assertion. The step used to ride on the strictest badge there is, so a
        // user who declined the exemption could never finish it — and everything after it was
        // unreachable for good.
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING),
            isAutoDetectionStopped = false,
            reinforcement = WatchReinforcement.BATTERY,
        )
        assertTrue(FirstStep.UNDERSTAND_WATCH in progress.done)
        assertEquals(FirstStep.FORTIFY_WATCH, progress.current, "robustness is the NEXT step's job")
    }

    @Test
    fun should_notBlockOnAStepTheUserDeferred() {
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING),
            reinforcement = WatchReinforcement.BATTERY,
            deferred = setOf(FirstStep.FORTIFY_WATCH),
        )
        assertEquals(FirstStep.FIND_SPOT, progress.current, "the checklist moves on to what it can teach")
        assertFalse(FirstStep.FORTIFY_WATCH in progress.done, "deferring is not doing")
    }

    @Test
    fun should_notClaimTheUserIsAllSet_when_somethingWasDeferred() {
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING, FirstStep.FIND_SPOT),
            reinforcement = WatchReinforcement.BATTERY,
            deferred = setOf(FirstStep.FORTIFY_WATCH),
        )
        assertEquals(null, progress.current, "nothing left to ask")
        assertFalse(progress.isComplete, "…but the user did not finish: the closing copy must differ")
        assertTrue(progress.hasDeferrals)
    }

    @Test
    fun should_forgetTheDeferral_when_theUserGrantsItLater() {
        // Deferring is reversible by construction: once there is nothing to reinforce the step is
        // gone, and with it the deferral — the closing card must not keep mentioning a pending
        // thing the user has since fixed.
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING),
            reinforcement = WatchReinforcement.NONE,
            deferred = setOf(FirstStep.FORTIFY_WATCH),
        )
        assertFalse(progress.hasDeferrals, "a granted step is no longer a pending deferral")
    }

    // ── The fortify step only exists when there is something to reinforce ──

    @Test
    fun should_notOfferTheFortifyStep_when_thereIsNothingToReinforce() {
        val progress = resolve(reinforcement = WatchReinforcement.NONE)
        assertFalse(FirstStep.FORTIFY_WATCH in progress.applicable)
        assertEquals(3, progress.total, "a user with nothing to fix has three steps, not four")
    }

    @Test
    fun should_offerTheFortifyStep_when_theWatchCanBeMadeSolid() {
        val progress = resolve(reinforcement = WatchReinforcement.BATTERY)
        assertTrue(FirstStep.FORTIFY_WATCH in progress.applicable)
        assertEquals(4, progress.total)
    }

    @Test
    fun should_retireTheFortifyStep_when_theReinforcementIsGranted() {
        // Granting does not tick this step, it RETIRES it: there is nothing left to fix, so the
        // checklist gets shorter and the counter follows. Any other reading would leave a step whose
        // button has nothing to do.
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING),
            reinforcement = WatchReinforcement.NONE,
        )
        assertFalse(FirstStep.FORTIFY_WATCH in progress.applicable)
        assertEquals(3, progress.total)
    }

    @Test
    fun should_ownTheFragileRow_when_theFortifyStepIsTheAsk() {
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING),
            reinforcement = WatchReinforcement.BATTERY,
        )
        assertEquals(FirstStep.FORTIFY_WATCH, progress.current)
        assertEquals(FirstStepsOwnership.WATCH_FRAGILE, progress.owns)
    }

    // ── Which reinforcement applies ──

    @Test
    fun should_askForBluetoothFirst_when_theCarCouldBeLinked() {
        // Bluetooth makes the exemption unnecessary, so asking for the exemption first would be
        // asking for the wrong thing.
        assertEquals(
            WatchReinforcement.BLUETOOTH,
            resolveWatchReinforcement(
                vehicleHasBluetoothMac = false,
                hasPairedBluetoothDevices = true,
                isReliabilityReduced = true,
            ),
        )
    }

    @Test
    fun should_askForTheExemption_when_thereIsNoBluetoothToLink() {
        // No bonded device on the phone: offering to link would open an empty list.
        assertEquals(
            WatchReinforcement.BATTERY,
            resolveWatchReinforcement(
                vehicleHasBluetoothMac = false,
                hasPairedBluetoothDevices = false,
                isReliabilityReduced = true,
            ),
        )
    }

    @Test
    fun should_askForNothing_when_theCarIsAlreadyLinked() {
        // A Bluetooth-covered car is watched by the manifest receiver — no exemption needed at all.
        assertEquals(
            WatchReinforcement.NONE,
            resolveWatchReinforcement(
                vehicleHasBluetoothMac = true,
                hasPairedBluetoothDevices = true,
                isReliabilityReduced = true,
            ),
        )
    }

    // ── The second step's two faces [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001] ──

    @Test
    fun should_explainTheRelease_when_detectionIsRunning() {
        assertEquals(WatchAsk.EXPLAIN_RELEASE, resolve(isAutoDetectionStopped = false).watchAsk)
    }

    @Test
    fun should_askToTurnDetectionOn_when_itIsStopped() {
        // The step's own sentence ("when you drive off it frees your spot") is only true with
        // detection running. Stopped, the step stops describing and starts asking.
        assertEquals(WatchAsk.TURN_IT_ON, resolve(isAutoDetectionStopped = true).watchAsk)
    }

    @Test
    fun should_ownTheDetectionOffRow_when_theSecondStepIsAskingForIt() {
        // The surface below has the very same ask in its own row; the step takes it over so there is
        // one voice and one button.
        val progress = resolve(done = setOf(FirstStep.MARK_PARKING), isAutoDetectionStopped = true)
        assertEquals(FirstStep.UNDERSTAND_WATCH, progress.current)
        assertEquals(FirstStepsOwnership.DETECTION_OFF, progress.owns)
    }

    @Test
    fun should_ownNothing_when_theSecondStepIsOnlyDescribing() {
        // Detection healthy: the step has nothing to ask for, so it takes over no row.
        val progress = resolve(done = setOf(FirstStep.MARK_PARKING), isAutoDetectionStopped = false)
        assertEquals(FirstStepsOwnership.NOTHING, progress.owns)
    }

    @Test
    fun should_ownTheColdStartRow_when_theFirstStepIsStillTheAsk() {
        // Even with detection stopped: step 1 is the current ask, and a step can only own one row.
        val progress = resolve(isAutoDetectionStopped = true)
        assertEquals(FirstStep.MARK_PARKING, progress.current)
        assertEquals(FirstStepsOwnership.COLD_START, progress.owns)
    }

    @Test
    fun should_ownNothing_when_everyStepIsDone() {
        assertEquals(
            FirstStepsOwnership.NOTHING,
            resolve(done = FirstStep.entries.toSet(), isAutoDetectionStopped = true).owns,
        )
    }

    // ── The third step's two faces [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001] ──

    @Test
    fun should_askToReportASpot_when_theCommunityHasNoneOnOffer() {
        // Day one: no users, no spots. Offering to "see spots" would hand the user an empty map, so
        // the step asks for the half that does not depend on anyone else being there yet.
        assertEquals(FindSpotAsk.REPORT_ONE, resolve(hasSpotsOnOffer = false).findSpotAsk)
    }

    @Test
    fun should_offerToShowSpots_when_thereIsAtLeastOneOnOffer() {
        assertEquals(FindSpotAsk.SEE_NEARBY, resolve(hasSpotsOnOffer = true).findSpotAsk)
    }

    @Test
    fun should_notCompleteFindSpot_when_thereAreSpotsButNoneWasOpened() {
        // The face is about what to ASK for; the step still completes only on measured state.
        // Spots existing nearby is not the user having met the community.
        val progress = resolve(hasSpotsOnOffer = true)
        assertFalse(FirstStep.FIND_SPOT in progress.done)
    }

    @Test
    fun should_beIndependentOfSetOrder_when_theLatchArrivesShuffled() {
        val a = resolve(done = setOf(FirstStep.FIND_SPOT, FirstStep.MARK_PARKING), isAutoDetectionStopped = true)
        val b = resolve(done = setOf(FirstStep.MARK_PARKING, FirstStep.FIND_SPOT), isAutoDetectionStopped = true)
        assertEquals(a, b)
        assertEquals(FirstStep.UNDERSTAND_WATCH, a.current)
    }
}
