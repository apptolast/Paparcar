package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit coverage for the unattended-timeout verdict. [DET-WALK-ENTERED-ANCHOR-ZONE-001]
 *
 * The seven-way precedence used to live inline in the coordinator's collect loop, reachable only
 * through a real-Clock 15-minute timeout — so none of it had ever been unit-tested. Every branch is
 * pinned here, and the field regression that motivated the extraction is the first test in the file.
 */
class EvaluateUnattendedParkingSaveUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val evaluate = EvaluateUnattendedParkingSaveUseCase(config)

    private val drivingSpeed = config.minimumTripSpeedMps + 1f

    private fun fix(
        lat: Double = 36.60840,
        lon: Double = -6.27820,
        accuracy: Float = 10f,
        timestamp: Long = 1_000L,
        speed: Float = 0f,
    ) = GpsPoint(lat, lon, accuracy, timestamp, speed)

    /** The anchor sits ~35 m north of [fix]'s default position — the car at the kerb, the user at
     *  the door. Both are well inside the honest-close minimum zone radius. */
    private val anchor = fix(lat = 36.60871, lon = -6.27796, accuracy = 9f)

    private fun input(
        maxSpeedMps: Float = drivingSpeed,
        pendingMaxSpeedMps: Float = drivingSpeed,
        credibleDrivingFixes: Int = config.rawDriveSignalMinFixes,
        anchor: GpsPoint? = this.anchor,
        currentFix: GpsPoint = fix(),
        egressOriginFix: GpsPoint? = null,
        stepCount: Int = 0,
        sessionSawSteps: Boolean = false,
        vehicleExitConfirmed: Boolean = true,
        anchorPinned: Boolean = true,
        anchorGapMs: Long = 0L,
        anchorWalkEntered: Boolean = false,
        anchorStepEventsAtCapture: Int = 0,
        anchorWalkInSpanMeters: Double = 0.0,
        egressBornAtAnchor: Boolean = true,
        egressExceedsWalkReach: Boolean = false,
        anchorRestMs: Long = config.sustainedStopForSaveMs + 1,
    ) = UnattendedSaveInput(
        maxSpeedMps = maxSpeedMps,
        pendingMaxSpeedMps = pendingMaxSpeedMps,
        credibleDrivingFixes = credibleDrivingFixes,
        anchor = anchor,
        currentFix = currentFix,
        egressOriginFix = egressOriginFix,
        stepCount = stepCount,
        sessionSawSteps = sessionSawSteps,
        vehicleExitConfirmed = vehicleExitConfirmed,
        anchorPinned = anchorPinned,
        anchorGapMs = anchorGapMs,
        anchorWalkEntered = anchorWalkEntered,
        anchorStepEventsAtCapture = anchorStepEventsAtCapture,
        anchorWalkInSpanMeters = anchorWalkInSpanMeters,
        egressBornAtAnchor = egressBornAtAnchor,
        egressExceedsWalkReach = egressExceedsWalkReach,
        anchorRestMs = anchorRestMs,
    )

    // ── The field regression ─────────────────────────────────────────────────────────────────

    /**
     * Field 2026-08-16 22:23Z, Redmi session `1786918991116`. A fully measured 25,6 min drive at up
     * to 96,7 km/h reached home; the slow final manoeuvre spent the walk-fix budget so the anchor
     * was tainted walk-entered, and the step counter had said nothing by the time it was captured.
     * The park was thrown away entirely.
     *
     * RED before the fix: the old gate required `anchorSawStepsAtCapture`, which was false, so the
     * zone was never even attempted and the session ended `aborted_unattended_walk_entered_anchor`.
     */
    @Test
    fun should_saveBoundedZone_when_walkEnteredAnchorHasMuteCounterButMeasuredWalkInSpan() {
        val verdict = evaluate(
            input(
                anchorWalkEntered = true,
                anchorStepEventsAtCapture = 0, // the mute counter that used to lose the park
                anchorWalkInSpanMeters = 42.0, // …and the GPS geometry that always could bound it
            )
        )

        val zone = assertIs<UnattendedParkingSave.SaveZone>(verdict)
        assertEquals(UnattendedSaveReason.WALK_ENTERED_ANCHOR, zone.reason)
        assertEquals(anchor, zone.center)
        assertEquals(42.0, zone.doubtMeters)
    }

    @Test
    fun should_useTheWiderWitness_when_bothStepsAndSpanBoundTheWalkIn() {
        val steps = 80
        val steppedBound = steps * config.anchorStrideMeters.toDouble()
        val verdict = evaluate(
            input(
                anchorWalkEntered = true,
                anchorStepEventsAtCapture = steps,
                anchorWalkInSpanMeters = 12.0,
            )
        )

        val zone = assertIs<UnattendedParkingSave.SaveZone>(verdict)
        assertTrue(steppedBound > 12.0, "precondition: the step witness is the wider one here")
        assertEquals(steppedBound, zone.doubtMeters)
    }

    /** Nothing bounded the walk-in at all → asking is still the honest exit. */
    @Test
    fun should_ask_when_walkEnteredAnchorHasNoBoundAtAll() {
        val verdict = evaluate(
            input(anchorWalkEntered = true, anchorStepEventsAtCapture = 0, anchorWalkInSpanMeters = 0.0)
        )

        assertEquals(UnattendedParkingSave.Ask(UnattendedSaveReason.WALK_ENTERED_ANCHOR), verdict)
    }

    /** The user's 2026-08-17 licence is "measured driving AND a settled rest". A car that has only
     *  just halted has not settled — keep asking rather than pin a red light. */
    @Test
    fun should_ask_when_walkEnteredAnchorIsBoundedButTheStopIsNotSustained() {
        val verdict = evaluate(
            input(
                anchorWalkEntered = true,
                anchorWalkInSpanMeters = 42.0,
                anchorRestMs = config.sustainedStopForSaveMs - 1,
            )
        )

        assertEquals(UnattendedParkingSave.Ask(UnattendedSaveReason.WALK_ENTERED_ANCHOR), verdict)
    }

    /**
     * Field 2026-08-18 ~20:00 (Calle Góndola), Redmi session `1787075310656` — the THIRD identical
     * home-arrival FN, one layer deeper than the two this evaluator already fixed. 26,2 min and
     * 113 km/h of measured driving, walk-entered pinned anchor with a measured walk-in span, prompt
     * unanswered — and the zone was refused because the SUSTAINED-STOP witness was the phone's stop
     * tracker, which indoor GPS noise (phantom 2–10 km/h at 100–266 m accuracy) reset every few
     * fixes: ~15 s accumulated in the 15 minutes the car provably rested at the anchor.
     *
     * RED before the fix: the licence read the phone's clock, so the verdict was Ask and the
     * session ended `aborted_unattended_walk_entered_anchor` with no pin and no zone.
     */
    @Test
    fun should_saveBoundedZone_when_carRestsAtPinnedAnchorWhileThePhoneStopClockKeepsResetting() {
        val verdict = evaluate(
            input(
                anchorWalkEntered = true,
                anchorStepEventsAtCapture = 0,
                anchorWalkInSpanMeters = 28.0,
                // The car's clock, witnessed by the anchor's stop: ~15 min. The phone's stop
                // tracker read ~10 s that night and is no longer consulted — that IS the fix.
                anchorRestMs = 902_000L,
            )
        )

        val zone = assertIs<UnattendedParkingSave.SaveZone>(verdict)
        assertEquals(UnattendedSaveReason.WALK_ENTERED_ANCHOR, zone.reason)
    }

    // ── Precedence: the branches that must NOT have changed ──────────────────────────────────

    @Test
    fun should_ask_when_theSessionNeverMeasuredDriving() {
        val verdict = evaluate(input(maxSpeedMps = 0f, pendingMaxSpeedMps = 0f))

        assertEquals(UnattendedParkingSave.Ask(UnattendedSaveReason.NO_DRIVE), verdict)
    }

    /** [DET-NODRIVE-ZONE-001] A late EXIT births the session at the destination: a LIVE counter with
     *  egress-scale steps, real displacement and an in-session vehicular signal still earn an area. */
    @Test
    fun should_saveZone_when_noProvenDriveButLiveEgressAndVehicularSignal() {
        val verdict = evaluate(
            input(
                maxSpeedMps = 0f,
                pendingMaxSpeedMps = drivingSpeed,
                currentFix = fix(lat = 36.60600, lon = -6.27820), // ~270 m from the anchor
                stepCount = config.anchorLockEgressSteps + 50,
                sessionSawSteps = true,
            )
        )

        val zone = assertIs<UnattendedParkingSave.SaveZone>(verdict)
        assertEquals(UnattendedSaveReason.NO_DRIVE_EGRESS, zone.reason)
    }

    /**
     * [DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001] The raw speed PEAK is one sample, and a receiver
     * converging out of a cold start emits exactly one. Field 2026-08-16 23:52 (Oppo): every confirm
     * path correctly refused the walk-out, and then this limb sold it an approximate zone off that
     * single 42 km/h fix. The hardening moved here when the chain became a pure verdict.
     */
    @Test
    fun should_ask_when_theOnlyVehicularSignalIsASingleRawSpeedSpike() {
        val verdict = evaluate(
            input(
                maxSpeedMps = 0f,
                pendingMaxSpeedMps = drivingSpeed,
                credibleDrivingFixes = 1,
                vehicleExitConfirmed = false,
                currentFix = fix(lat = 36.60600, lon = -6.27820),
                stepCount = config.anchorLockEgressSteps + 50,
                sessionSawSteps = true,
            )
        )

        assertEquals(UnattendedParkingSave.Ask(UnattendedSaveReason.NO_DRIVE), verdict)
    }

    /** An AR vehicle-exit is external evidence, not a sample: it carries the branch on its own. */
    @Test
    fun should_saveZone_when_anArVehicleExitBacksTheNoDriveBranch() {
        val verdict = evaluate(
            input(
                maxSpeedMps = 0f,
                pendingMaxSpeedMps = 0f,
                credibleDrivingFixes = 0,
                vehicleExitConfirmed = true,
                currentFix = fix(lat = 36.60600, lon = -6.27820),
                stepCount = config.anchorLockEgressSteps + 50,
                sessionSawSteps = true,
            )
        )

        val zone = assertIs<UnattendedParkingSave.SaveZone>(verdict)
        assertEquals(UnattendedSaveReason.NO_DRIVE_EGRESS, zone.reason)
    }

    /** [DET-FROZEN-COUNTER-001] An UNPINNED anchor travels with the walker, so only a live counter
     *  can bound it. Deliberately NOT relaxed by this ticket — the 260 m mute-walk regression. */
    @Test
    fun should_ask_when_anchorIsUnpinnedAndTheCounterIsMute() {
        val verdict = evaluate(input(anchorPinned = false, sessionSawSteps = false))

        assertEquals(UnattendedParkingSave.Ask(UnattendedSaveReason.UNPINNED_ANCHOR), verdict)
    }

    @Test
    fun should_saveZone_when_anchorIsUnpinnedButTheCounterIsAlive() {
        val verdict = evaluate(input(anchorPinned = false, sessionSawSteps = true, stepCount = 120))

        val zone = assertIs<UnattendedParkingSave.SaveZone>(verdict)
        assertEquals(UnattendedSaveReason.UNPINNED_ANCHOR, zone.reason)
    }

    /** [DET-ANCHOR-EGRESS-001] An egress born away from the anchor centres on the anchor when the
     *  counter is mute (the birth is then a kinematic guess along the walker's trail). */
    @Test
    fun should_saveZoneCenteredOnAnchor_when_egressBornAwayAndCounterMute() {
        val birth = fix(lat = 36.59000, lon = -6.27820, accuracy = 8f)
        val verdict = evaluate(input(egressBornAtAnchor = false, egressOriginFix = birth, sessionSawSteps = false))

        val zone = assertIs<UnattendedParkingSave.SaveZone>(verdict)
        assertEquals(UnattendedSaveReason.EGRESS_MISMATCH, zone.reason)
        assertEquals(anchor, zone.center)
        assertTrue(zone.doubtMeters > 1_000.0, "the doubt spans anchor↔birth, was ${zone.doubtMeters}")
    }

    @Test
    fun should_saveZoneCenteredOnBirth_when_egressBornAwayAndCounterAlive() {
        val birth = fix(lat = 36.59000, lon = -6.27820, accuracy = 8f)
        val verdict = evaluate(input(egressBornAtAnchor = false, egressOriginFix = birth, sessionSawSteps = true))

        val zone = assertIs<UnattendedParkingSave.SaveZone>(verdict)
        assertEquals(birth, zone.center)
    }

    // ── The GAP-ENTERED anchor: a hole has a duration, so the doubt it creates has a bound ───

    /**
     * [DET-GAP-ANCHOR-ZONE-001] The second field regression, field 2026-08-17, Redmi session
     * `1786970028118` (Calle Bahía de Alcudia 4). The trip was fully measured — 36 min, 50 km/h,
     * 34 driving fixes — but MIUI tore 9 holes in the stream, and the last one (126 s, entered at
     * 42 km/h) opened the stop the anchor bound to. The car then sat still for 14,7 min at an
     * anchor accurate to ~11 m, 40 m from where the Oppo pinned the same trip on the same journey.
     *
     * RED before the fix: the branch returned `Ask` unconditionally on the Boolean taint, and the
     * session ended `aborted_unattended_gap_anchor` with no pin at all.
     */
    @Test
    fun should_saveBoundedZone_when_gapEnteredAnchorCameToASustainedRest() {
        val verdict = evaluate(
            input(
                anchorGapMs = 126_000L,
                anchorRestMs = 880_000L, // the 14,7 min the car sat still at the anchor
            )
        )

        val zone = assertIs<UnattendedParkingSave.SaveZone>(verdict)
        assertEquals(UnattendedSaveReason.GAP_ANCHOR, zone.reason)
        assertEquals(anchor, zone.center, "the zone centres on the anchor the hole cast doubt on")
        // 126 s of hole at pedestrian pace — everything the phone could have walked inside it.
        assertEquals(126.0 * config.maxPedestrianSpeedMps, zone.doubtMeters, 0.5)
    }

    /**
     * [DET-GAP-ANCHOR-001] The FP the branch exists for stays dead: field 2026-07-29, Av. Sanlúcar,
     * where a 100-s hole ended in a single speed-0 fix mid-route and the pin landed 315 m before
     * the real park. Its signature is the absence of rest — the car drove ON — so without a
     * sustained stop the verdict is still the nudge, and no radius is asserted.
     */
    @Test
    fun should_ask_when_gapEnteredAnchorNeverSettled() {
        val verdict = evaluate(
            input(
                anchorGapMs = 100_000L,
                anchorRestMs = config.sustainedStopForSaveMs - 1,
            )
        )

        assertEquals(UnattendedParkingSave.Ask(UnattendedSaveReason.GAP_ANCHOR), verdict)
    }

    /** The bound is the hole itself, so a shorter hole buys a tighter zone. */
    @Test
    fun should_scaleTheDoubtWithTheHole_when_holesDiffer() {
        val short = assertIs<UnattendedParkingSave.SaveZone>(evaluate(input(anchorGapMs = 46_000L)))
        val long = assertIs<UnattendedParkingSave.SaveZone>(evaluate(input(anchorGapMs = 300_000L)))

        assertTrue(
            short.doubtMeters < long.doubtMeters,
            "a 46-s hole must bound tighter than a 300-s one (${short.doubtMeters} vs ${long.doubtMeters})",
        )
    }

    /** Nothing to centre an area on: the taint has a bound but no place to put it. */
    @Test
    fun should_ask_when_gapEnteredAndNoAnchorSurvives() {
        val verdict = evaluate(input(anchorGapMs = 126_000L, anchor = null))

        assertEquals(UnattendedParkingSave.Ask(UnattendedSaveReason.GAP_ANCHOR), verdict)
    }

    /** The gap taint still outranks the walk-entered one — both may hold, and the hole is the
     *  larger of the two doubts, so it is the one that must size the area. */
    @Test
    fun should_preferTheGapBound_when_bothTaintsHold() {
        val verdict = evaluate(
            input(anchorGapMs = 126_000L, anchorWalkEntered = true, anchorWalkInSpanMeters = 42.0)
        )

        val zone = assertIs<UnattendedParkingSave.SaveZone>(verdict)
        assertEquals(UnattendedSaveReason.GAP_ANCHOR, zone.reason)
    }

    // ── Evidence of ABSENCE outranks every precision doubt ────────────────────────────────────

    /** [DET-CONFIRM-FRESHNESS-001] Not a precision doubt — evidence the car LEFT. Never a zone. */
    @Test
    fun should_askAndStampTheDistance_when_thePositionOutranTheSteps() {
        val verdict = evaluate(
            input(egressExceedsWalkReach = true, currentFix = fix(lat = 36.60000, lon = -6.27820))
        )

        val ask = assertIs<UnattendedParkingSave.Ask>(verdict)
        assertEquals(UnattendedSaveReason.VEHICULAR_EGRESS, ask.reason)
        assertTrue((ask.distanceMeters ?: 0.0) > 500.0, "the outran distance is stamped for forensics")
    }

    /**
     * [DET-GAP-ANCHOR-ZONE-001] The precedence this ticket moved. "The car provably left" is not a
     * doubt about WHERE the anchor is, it is evidence there is nothing to record — so it outranks
     * every branch that would draw a radius, rather than sitting after them where the two taint
     * zones would shadow it.
     */
    @Test
    fun should_askVehicularEgress_when_theCarLeftAGapEnteredAnchor() {
        val verdict = evaluate(
            input(
                anchorGapMs = 126_000L,
                egressExceedsWalkReach = true,
                currentFix = fix(lat = 36.60000, lon = -6.27820),
            )
        )

        assertEquals(UnattendedSaveReason.VEHICULAR_EGRESS, assertIs<UnattendedParkingSave.Ask>(verdict).reason)
    }

    @Test
    fun should_askVehicularEgress_when_theCarLeftAWalkEnteredAnchor() {
        val verdict = evaluate(
            input(
                anchorWalkEntered = true,
                anchorWalkInSpanMeters = 42.0,
                egressExceedsWalkReach = true,
                currentFix = fix(lat = 36.60000, lon = -6.27820),
            )
        )

        assertEquals(UnattendedSaveReason.VEHICULAR_EGRESS, assertIs<UnattendedParkingSave.Ask>(verdict).reason)
    }

    @Test
    fun should_saveExact_when_everyAnchorTaintComesBackClean() {
        assertEquals(UnattendedParkingSave.SaveExact, evaluate(input()))
    }

    // ── The diagnostics vocabulary is load-bearing ───────────────────────────────────────────

    /**
     * Field traces and the project's own memory quote these strings verbatim. Two of the historical
     * decision labels drop the `_ANCHOR` suffix; renaming them would silently orphan every saved
     * session in Firestore.
     */
    @Test
    fun should_keepTheHistoricalTraceVocabulary_when_reasonsAreSpelledOut() {
        assertEquals("unattended_walk_entered_anchor", UnattendedSaveReason.WALK_ENTERED_ANCHOR.nudgeSource)
        assertEquals(
            "aborted_unattended_walk_entered_anchor",
            UnattendedSaveReason.WALK_ENTERED_ANCHOR.abortedOutcome,
        )
        assertEquals("UNATTENDED_WALK_ENTERED_NUDGE", UnattendedSaveReason.WALK_ENTERED_ANCHOR.decisionOutcome)
        assertEquals("UNATTENDED_UNPINNED_NUDGE", UnattendedSaveReason.UNPINNED_ANCHOR.decisionOutcome)
        assertEquals("aborted_unattended_no_drive", UnattendedSaveReason.NO_DRIVE.abortedOutcome)
        assertEquals("UNATTENDED_GAP_ANCHOR_NUDGE", UnattendedSaveReason.GAP_ANCHOR.decisionOutcome)
        assertEquals("UNATTENDED_VEHICULAR_EGRESS_NUDGE", UnattendedSaveReason.VEHICULAR_EGRESS.decisionOutcome)
        assertEquals("UNATTENDED_EGRESS_MISMATCH_NUDGE", UnattendedSaveReason.EGRESS_MISMATCH.decisionOutcome)
    }
}
