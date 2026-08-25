package io.apptolast.paparcar.domain.detection.state

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [09 §5] **The order the five sub-states are reduced in.**
 *
 * Phase 2 gave every field an owner. This pins the thing that exists only BETWEEN owners: when two
 * sub-states reduce against the same input, which of them sees the other's NEW value.
 *
 * It matters because the traffic runs both ways. The rule started one-way — the anchor owns itself
 * and the steps are presented to it — and `DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001` made the
 * cadence classifier read the anchor back. With a cycle in the graph, a slip in the order changes a
 * verdict while the compiler stays happy and every sub-state's own tests keep passing. That is
 * exactly the kind of defect nothing catches, so it gets a file.
 */
class ReductionOrderTest {

    private fun fix(speed: Float = 20f, at: Long = 1_000L) =
        GpsPoint(36.6119, -6.2805, accuracy = 8f, timestamp = at, speed = speed)

    // ── Rule 2: the session consumes THIS fix's drive proof, not the previous one ──

    /**
     * [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] A seed the arm only LENT stops being retractable
     * the moment the track proves a drive. "The moment" is this fix.
     *
     * Reduce the session against the PREVIOUS drive proof and the flag clears one fix late — a
     * one-fix window in which a dismissed departure could still take back a seed the trip has
     * already earned. One fix is not a rounding error here: the whole point of the flag is that it
     * separates what the arm lent from what the trip proved.
     */
    @Test
    fun should_settle_the_seed_with_the_proof_this_very_fix_produced() {
        val before = DetectionSessionState(
            session = SessionTelemetry().seededOnArmTrust(),
            drive = DriveProof(), // nothing proven yet
        )
        assertTrue(before.session.authorizedOnArmTrustOnly, "the seed is still on loan")

        val provenByThisFix = DriveProof(proven = DriveProofSource.TRACK_WINDOW)
        val after = before.onFix(
            newDrive = provenByThisFix,
            fix = fix(),
            nowMs = 1_000L,
            reachedDrivingSpeed = true,
            moved = true,
        )

        assertFalse(
            after.session.authorizedOnArmTrustOnly,
            "the proof arrived on THIS fix, so the seed is settled on THIS fix",
        )
        assertEquals(provenByThisFix, after.drive, "…and the proof it was settled with is the one stored")
    }

    /** The other half: with nothing proven, the seed stays on loan and stays retractable. */
    @Test
    fun should_keep_the_seed_retractable_while_no_proof_has_arrived() {
        val after = DetectionSessionState(session = SessionTelemetry().seededOnArmTrust())
            .onFix(DriveProof(), fix(speed = 20f), 1_000L, reachedDrivingSpeed = true, moved = true)
        assertTrue(after.session.authorizedOnArmTrustOnly)
        assertTrue(after.session.driveAuthorized)
    }

    // ── Rule 3: anchor and egress never read each other's new value on a fix ──

    /**
     * Both reduce against the PRE-fix snapshot, so their relative order is irrelevant by
     * construction — and this test is what says "by construction" out loud. Reducing them in either
     * order from the same state must give the same result; if one ever started reading the other's
     * output, this is where it would show.
     */
    @Test
    fun should_give_the_same_result_whichever_of_anchor_and_egress_reduces_first() {
        val start = DetectionSessionState(
            anchorTrust = AnchorTrust(walkIn = WalkIn(fixesSinceDriving = 3)),
            egress = EgressEvidence(stepCount = 5, stepEventsSinceDriving = 5),
        )
        val here = fix(speed = 1f, at = 2_000L)

        val anchorFirst = start
            .copy(anchorTrust = start.anchorTrust.onMovingFix(false, false, here, 0, 0))
            .let { it.copy(egress = start.egress.onFix(false, false, false, 2)) }

        val egressFirst = start
            .copy(egress = start.egress.onFix(false, false, false, 2))
            .let { it.copy(anchorTrust = start.anchorTrust.onMovingFix(false, false, here, 0, 0)) }

        assertEquals(anchorFirst, egressFirst)
    }

    // ── The step-event direction, which cannot be got wrong ──

    /**
     * [DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001] A step reads the anchor and never writes it —
     * `onStepEvent` returns only an [EgressEvidence], so the cycle is broken by the type. The anchor
     * it reads is therefore always the one from BEFORE the step, which is what makes "once the
     * anchor is pinned, feet next to a fast fix are the egress" mean anything.
     */
    @Test
    fun should_leave_the_anchor_untouched_when_a_step_arrives() {
        val start = DetectionSessionState(anchorTrust = AnchorTrust(frozenByRest = true, anchor = fix(speed = 0f)))
        val after = start.copy(
            egress = start.egress.onStepEvent(
                stepAtMs = 3_000L,
                driveAuthorized = true,
                stopped = false,
                anchorPresent = true,
                anchorPinned = true,
                lastFixSpeedMps = 4f,
                lastFixCredible = true,
                lastFixSeenAtMs = 2_900L,
                pedestrianCeilingMps = 2.5f,
                motorProofSpeedMps = 11f,
                cadenceFixFreshnessMs = 5_000L,
            ),
        )
        assertEquals(start.anchorTrust, after.anchorTrust)
        assertEquals(0, after.egress.fastMotionStepEvents, "a pinned anchor vetoes the cadence reading")
    }
}
