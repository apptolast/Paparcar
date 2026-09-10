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
        hasExploredNearby: Boolean = false,
        reinforcement: WatchReinforcement = WatchReinforcement.NONE,
        deferred: Set<FirstStep> = emptySet(),
    ) = resolveFirstSteps(
        // ⛔ NAMED, not positional. This helper passed nine arguments by position, so inserting a
        // parameter in the middle silently re-bound every one after it — here it failed to compile
        // only because the new one is a Boolean and the next is an enum.
        // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
        done = done,
        dismissed = dismissed,
        hasActiveSession = hasActiveSession,
        isWatching = isWatching,
        hasTouchedSpots = hasTouchedSpots,
        hasSpotsOnOffer = hasSpotsOnOffer,
        isAutoDetectionStopped = isAutoDetectionStopped,
        hasExploredNearby = hasExploredNearby,
        reinforcement = reinforcement,
        deferred = deferred,
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
    fun should_reportComplete_when_everySignalIsSatisfied() {
        // FOUR signals since [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]: the map answers three
        // questions and the checklist teaches all of them, so "all set" needs the fifth step too.
        val progress = resolve(
            hasActiveSession = true,
            isWatching = true,
            hasTouchedSpots = true,
            hasExploredNearby = true,
        )
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

    // ── Qué paso se completa con solo TOCARLO ──
    // [ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001]

    @Test
    fun should_completeTheCommunityStep_when_theUserEngagesWithIt() {
        // Reading its explanation or opening the report flow, published spot or not. It teaches that
        // the community half EXISTS; demanding a publication left a user with nothing to report
        // unable to ever close it — the only ways out being a false report or a checklist open
        // forever.
        assertTrue(FirstStep.FIND_SPOT.completesOnEngage)
    }

    @Test
    fun should_notCompleteAnyStepThatIsAnAct_when_itIsMerelyTapped() {
        // The guard that matters: a tap does not park the car, does not turn detection on and does
        // not grant an exemption. MARK_PARKING above all — it is what arms everything else and stays
        // REQUIRED, with a parking that really exists. This is the assertion someone would have to
        // delete on purpose when adding a fifth step, instead of quietly inheriting "a tap counts".
        // Deleted ON PURPOSE when the fifth step landed, exactly as this comment asked: it is a
        // second step of KNOWLEDGE ([ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]), declared as such on
        // the enum. Everything else stays an ACT.
        val knowledge = setOf(FirstStep.FIND_SPOT, FirstStep.EXPLORE_NEARBY)
        val acts = FirstStep.entries.filter { it !in knowledge }
        assertEquals(
            emptyList(),
            acts.filter { it.completesOnEngage },
            "only steps of KNOWLEDGE complete on engagement",
        )
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
            // The fifth step has to be closed too, or the checklist genuinely still has something to
            // ask and this test would be asserting the wrong screen.
            // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
            hasExploredNearby = true,
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
        assertEquals(4, progress.total, "a user with nothing to fix has four steps, not five")
    }

    @Test
    fun should_offerTheFortifyStep_when_theWatchCanBeMadeSolid() {
        val progress = resolve(reinforcement = WatchReinforcement.BATTERY)
        assertTrue(FirstStep.FORTIFY_WATCH in progress.applicable)
        assertEquals(5, progress.total)
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
        assertEquals(4, progress.total)
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

    // ── The fifth step: the other two questions the map answers ───────────────
    // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]

    @Test
    fun should_completeExploreNearby_when_aComplementLaneHasBeenEntered() {
        // Entering the lane IS the step: it is knowledge, and it is the only moment the app says
        // where that data comes from.
        val progress = resolve(hasExploredNearby = true)
        assertTrue(FirstStep.EXPLORE_NEARBY in progress.done)
    }

    @Test
    fun should_leaveExploreNearbyOpen_when_noComplementLaneWasEverEntered() {
        assertFalse(FirstStep.EXPLORE_NEARBY in resolve().done)
    }

    @Test
    fun should_askExploreNearbyLast_when_everythingElseIsDone() {
        // The order is the hierarchy: free spots are the product, the complements come after it.
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH, FirstStep.FIND_SPOT),
        )
        assertEquals(FirstStep.EXPLORE_NEARBY, progress.current)
        assertFalse(progress.isComplete)
    }

    @Test
    fun should_notBeComplete_when_onlyExploreNearbyIsMissing() {
        // The guard against the checklist congratulating a user it never finished teaching.
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH, FirstStep.FIND_SPOT),
        )
        assertEquals(3, progress.doneCount)
        assertEquals(4, progress.total)
    }

    @Test
    fun should_completeTheChecklist_when_theFifthStepLandsToo() {
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH, FirstStep.FIND_SPOT),
            hasExploredNearby = true,
        )
        assertTrue(progress.isComplete)
        assertEquals(null, progress.current)
    }

    @Test
    fun should_closeOnEngage_when_theStepIsKnowledge() {
        // Both knowledge steps declare it HERE, which is what stops a `when` elsewhere deciding it.
        assertTrue(FirstStep.EXPLORE_NEARBY.completesOnEngage)
        assertTrue(FirstStep.FIND_SPOT.completesOnEngage)
        assertFalse(FirstStep.MARK_PARKING.completesOnEngage)
        assertFalse(FirstStep.UNDERSTAND_WATCH.completesOnEngage)
        assertFalse(FirstStep.FORTIFY_WATCH.completesOnEngage)
    }

    @Test
    fun should_ownNothing_when_theFifthStepIsTheOneAsking() {
        // ⛔ It must not take over any detection-surface row: it is not about the watch at all, and
        // a step that claimed one would silence a row that is still asking for something real.
        val progress = resolve(
            done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH, FirstStep.FIND_SPOT),
        )
        assertEquals(FirstStep.EXPLORE_NEARBY, progress.current)
        assertEquals(FirstStepsOwnership.NOTHING, progress.owns)
    }

    @Test
    fun should_beIndependentOfSetOrder_when_theLatchArrivesShuffled() {
        val a = resolve(done = setOf(FirstStep.FIND_SPOT, FirstStep.MARK_PARKING), isAutoDetectionStopped = true)
        val b = resolve(done = setOf(FirstStep.MARK_PARKING, FirstStep.FIND_SPOT), isAutoDetectionStopped = true)
        assertEquals(a, b)
        assertEquals(FirstStep.UNDERSTAND_WATCH, a.current)
    }
}
