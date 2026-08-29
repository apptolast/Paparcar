package com.rndeveloper.paparcar.domain.detection.stages

import com.rndeveloper.paparcar.domain.detection.HoldAction
import com.rndeveloper.paparcar.domain.detection.physics.SavedParkingShape
import com.rndeveloper.paparcar.domain.detection.physics.isCredibleFixAccuracy
import com.rndeveloper.paparcar.domain.detection.physics.outrunsPedestrianReach
import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.detection.state.PendingConfirm
import com.rndeveloper.paparcar.domain.detection.state.isAnchorPinned
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig

/**
 * [DET-C-02] **The first stage of the precedence: a held confirm owns the fix that would otherwise
 * re-decide it.**
 *
 * A tentative egress-confirm waits here to rule out an errand stop — park, walk to a kiosk, drive on
 * to park properly. If the car drives off again before the grace window elapses, the confirm is
 * discarded and detection keeps going so the saved park re-anchors at the FINAL spot. An explicit
 * user "Sí" finalises immediately.
 *
 * Nothing outranks it, and `should_swallow_the_fix_while_a_tentative_confirm_is_holding` plus
 * `should_plant_the_held_pin_not_the_answer_fix_when_the_user_says_yes_during_a_hold` (P0.1) are what
 * hold that in place. The second is the sharp one: when the user answers during a hold, the pin goes
 * where the CAR was when the hold opened, not where the person is standing when they tap.
 *
 * ## Four branches, and two of them do NOT end the pass
 *
 * That asymmetry is easy to lose in a move and was never written down anywhere:
 *
 *  1. **Stale at settle** — discard, then **fall through** and keep detecting toward the real park.
 *  2. **Settle** — confirm at the held pin. Ends the pass.
 *  3. **Drove off** — discard, then **fall through**, same as (1).
 *  4. **Still holding** — end the pass and change nothing: the window has not elapsed.
 *
 * A discard is not an ending. It says *this pin was wrong*, and the session must go on to find the
 * right one — the whole point of the hold. Making a discard end the pass would delay every real park
 * by one fix, and making the still-holding branch fall through would let the stages below re-decide
 * a fix the hold has already claimed.
 *
 * ## The two guards on the way in
 *
 * [ANCHOR-LOCK-001][DET-ANCHOR-FREEZE-001] With the anchor PINNED — egress steps in hand, or the
 * end-of-drive stop matured — the user is on foot, so only REAL driving speed may mean "errand over,
 * drove off". Brisk walking must not discard a hold.
 *
 * [DET-CONFIRM-FRESHNESS-001] And the confirm's evidence must still be TRUE when the pin is planted:
 * if the current fix sits farther from the held pin than the counted steps could walk, a VEHICLE
 * covered that ground during the hold. That is a pick-up stop whose departure the drove-off branch
 * missed — field 2026-07-23, Calle Abeto: the only rolling fix carried accuracy 71 m against a 50 m
 * trust gate, then 95 s of GPS silence while driving, and the hold settled with the car at another
 * traffic light 570 m away, pinning the pick-up spot. **The user-yes path is exempt**: an explicit
 * answer outranks every guard.
 *
 * ## Why a "Sí" during the hold becomes the USER path
 *
 * Not the auto path that opened the hold. Reliability 1.0, every guard bypassed — the repark guard
 * must not veto a park the user explicitly confirmed. The POSITION stays the pinned hold location
 * either way, which is the distinction the whole stage turns on: the answer settles whether, the
 * hold already settled where.
 */
class HoldResolutionStage : SessionStage {

    override val stage = DetectionStage.HOLD_RESOLUTION

    override fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        stoppedDurationMs: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict {
        val pending = state.confirmation.pendingConfirm ?: return StageVerdict.Skip()
        val heldMs = now - pending.confirmedAt
        val userSaidYes = state.confirmation.userConfirmed

        // Only REAL driving speed clears a pinned anchor; an unpinned one yields to the lower bar.
        val resumeSpeedBar =
            if (state.isAnchorPinned(config)) config.minimumTripSpeedMps else config.clearBestStopSpeedMps
        // [DET-C-02] Strictly greater, deliberately: this discards a pin that has already EARNED its
        // confirm, so the boundary is not moved by a pure-move refactor. Only the accuracy gate is
        // shared with the driving predicates.
        val drivingResumed = fix.speed > resumeSpeedBar &&
            isCredibleFixAccuracy(fix, config.minGpsAccuracyForDriving)

        return when {
            !userSaidYes && heldMs >= config.confirmHoldMs &&
                heldConfirmOutrunByVehicle(pending, state, fix, config) ->
                discard(state, HoldAction.DISCARDED_STALE, heldMs, pending, fix, STALE_NOTE)

            userSaidYes || heldMs >= config.confirmHoldMs -> settle(state, pending, heldMs, userSaidYes, config)

            drivingResumed ->
                discard(state, HoldAction.DISCARDED_DROVE_OFF, heldMs, pending, fix, droveOffNote(heldMs))

            // Still holding (stopped, window not elapsed) — keep the session alive and let no stage
            // below touch this fix.
            else -> StageVerdict.Handled(newState = state, stopsIteration = true)
        }
    }

    /**
     * Discard and KEEP DETECTING. On a real driving fix the stop tracking has already cleared anchor
     * and steps; on an ambiguous walking-band fix with an unpinned anchor it may have KEPT them —
     * harmless, because the next fix re-confirms from the same anchor and re-enters the hold. A
     * delayed finalize, never a lost park.
     */
    private fun discard(
        state: DetectionSessionState,
        action: HoldAction,
        heldMs: Long,
        pending: PendingConfirm,
        fix: GpsPoint,
        note: String,
    ) = StageVerdict.Handled(
        newState = state,
        effects = listOf(DetectionEffect.DiscardHold(action, heldMs, pending.pathLabel, fix)),
        stopsIteration = false,
        notes = notes(note),
    )

    private fun settle(
        state: DetectionSessionState,
        pending: PendingConfirm,
        heldMs: Long,
        userSaidYes: Boolean,
        config: ParkingDetectionConfig,
    ): StageVerdict {
        val pathLabel = if (userSaidYes) "user" else pending.pathLabel
        val reliability = if (userSaidYes) config.reliabilityUserConfirmed else pending.reliability
        return StageVerdict.Handled(
            newState = state,
            effects = listOf(
                // The settle marker is stamped BEFORE the save, and against the HELD pin — the save
                // may fail, and the trace has to say the hold resolved either way.
                DetectionEffect.RecordHoldSettled(heldMs, pathLabel, pending.location),
                DetectionEffect.Confirm(
                    shape = SavedParkingShape.ExactPin(pending.location, reliability),
                    vehicleId = pending.vehicleId,
                    pathLabel = pathLabel,
                    // The grace window is what just elapsed. There is nothing left to hold.
                    mayHold = false,
                ),
            ),
            stopsIteration = true,
            notes = notes("  ✓ hold settled (held=${heldMs}ms, userYes=$userSaidYes) — finalizing tentative confirm [DET-C-02]"),
        )
    }

    private fun droveOffNote(heldMs: Long) =
        "  ↩ tentative confirm DISCARDED — drove off ${heldMs}ms into the hold (errand), re-anchoring [DET-C-02]"

    private companion object {
        const val STALE_NOTE = "  ↩ tentative confirm STALE at settle — position outran the steps " +
            "from the held pin (errand/pick-up stop), discarding and re-anchoring [DET-CONFIRM-FRESHNESS-001]"
    }
}

/** [DET-CONFIRM-FRESHNESS-001] Settle-time freshness check for a held confirm: the current fix
 *  sits farther from the HELD pin than the steps counted for this stop could walk (stride +
 *  both accuracy envelopes + the generous egress-birth floor — the same physics as
 *  `egressExceedsWalkReach`, measured against the pending pin so it needs no live anchor).
 *  TRUE means a vehicle covered that ground after the tentative confirm: the evidence the hold
 *  was opened on is no longer true, and finalizing it would pin a stop the car provably left.
 *  A degraded fix inflates the reach through its own accuracy — fails conservative.
 *
 *  Lives with the one stage that asks it, because it is a predicate with a single consumer
 *  [DET-VERDICT-NOT-PREDICATE-001] — unlike the anchor family, which three stages share. */
private fun heldConfirmOutrunByVehicle(
    pending: PendingConfirm,
    state: DetectionSessionState,
    current: GpsPoint,
    config: ParkingDetectionConfig,
): Boolean = outrunsPedestrianReach(
    base = pending.location,
    fix = current,
    steps = state.egress.stepCount,
    strideMeters = config.anchorStrideMeters,
    floorMeters = config.egressBirthFloorMeters,
)
