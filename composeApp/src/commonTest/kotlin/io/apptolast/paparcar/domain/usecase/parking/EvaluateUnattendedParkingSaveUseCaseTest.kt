package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
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
        anchorGapEntered: Boolean = false,
        anchorWalkEntered: Boolean = false,
        anchorStepEventsAtCapture: Int = 0,
        anchorWalkInSpanMeters: Double = 0.0,
        egressBornAtAnchor: Boolean = true,
        egressExceedsWalkReach: Boolean = false,
        stoppedDurationMs: Long = config.sustainedStopForSaveMs + 1,
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
        anchorGapEntered = anchorGapEntered,
        anchorWalkEntered = anchorWalkEntered,
        anchorStepEventsAtCapture = anchorStepEventsAtCapture,
        anchorWalkInSpanMeters = anchorWalkInSpanMeters,
        egressBornAtAnchor = egressBornAtAnchor,
        egressExceedsWalkReach = egressExceedsWalkReach,
        stoppedDurationMs = stoppedDurationMs,
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
                stoppedDurationMs = config.sustainedStopForSaveMs - 1,
            )
        )

        assertEquals(UnattendedParkingSave.Ask(UnattendedSaveReason.WALK_ENTERED_ANCHOR), verdict)
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

    /** [DET-GAP-ANCHOR-001] The forward error is UNBOUNDABLE — the car may have driven arbitrarily
     *  far inside the hole. No zone is honest, whatever the ticket's licence says. */
    @Test
    fun should_ask_when_anchorWasEnteredThroughAGpsHole() {
        val verdict = evaluate(input(anchorGapEntered = true, anchorWalkInSpanMeters = 42.0))

        assertEquals(UnattendedParkingSave.Ask(UnattendedSaveReason.GAP_ANCHOR), verdict)
    }

    /** The gap taint outranks the walk-entered one: both may hold, and only one of them is safe. */
    @Test
    fun should_preferTheGapAsk_when_bothTaintsHold() {
        val verdict = evaluate(
            input(anchorGapEntered = true, anchorWalkEntered = true, anchorWalkInSpanMeters = 42.0)
        )

        assertEquals(UnattendedParkingSave.Ask(UnattendedSaveReason.GAP_ANCHOR), verdict)
    }

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
