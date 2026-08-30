package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fix reduction that runs before the precedence: *is the car stopped, and if so where does that
 * put the anchor*. Every stage below it reads an anchor this function has already settled.
 *
 * It is 480 lines and thirty-odd branches, and until now **no test named it once**. What it had was
 * indirect coverage from the trace replays — which is real coverage, and is also why the gap was
 * invisible: the replays assert the END of a trip (was a pin planted, where), so a branch can change
 * its mind about an intermediate fix and still land the same pin on all sixteen traces.
 *
 * ## What is pinned here
 *
 * Every assert below corresponds to a branch the code itself annotates with a ticket and a dated
 * field incident, because those are the branches that exist against an intuition:
 *
 *  - a fix reporting 0 m/s is not evidence of rest if its own track says it moved 122 m
 *    (Góndola, 2026-08-22 — the anchor froze in the side-street mouth, 70 m short);
 *  - a refuted stop loses its EVIDENCE but keeps its CLOCK, which are two different things that
 *    were once one (Redmi, 2026-08-28 — matured by time while driving home, pin inside the house);
 *  - a brisk walk away from the car is above the clear bar and must not wipe a locked anchor
 *    (2026-07-04 — the park re-anchored 55 m away, where the user next stood still);
 *  - one trip-speed sample does not overturn a rest this session witnessed; it takes a run.
 *
 * Distances are built north of a fixed base so a metre in the helper is a metre on the ground.
 *
 * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class StopTrackingTest {

    private val config = ParkingDetectionConfig()

    private fun point(
        metersNorth: Double = 0.0,
        accuracy: Float = 8f,
        at: Long = 0L,
        speed: Float = 0f,
    ) = GpsPoint(
        latitude = BASE_LAT + metersNorth / METERS_PER_DEGREE_LAT,
        longitude = BASE_LON,
        accuracy = accuracy,
        timestamp = at,
        speed = speed,
    )

    private fun state(
        anchorTrust: AnchorTrust = AnchorTrust(),
        steps: Int = 0,
        sensorAlive: Boolean = false,
        driveAuthorized: Boolean = false,
        previousFix: GpsPoint? = null,
    ) = DetectionSessionState(
        anchorTrust = anchorTrust,
        egress = EgressEvidence(stepCount = steps, sensorAlive = sensorAlive),
        session = SessionTelemetry(driveAuthorized = driveAuthorized, previousFix = previousFix),
    )

    private fun StopTracking.trace() = notes.joinToString("\n") { it.text }

    // ── The stop opens and keeps its clock ───────────────────────────────────

    @Test
    fun should_open_a_stop_and_take_its_first_fix_as_the_anchor() {
        val fix = point(accuracy = 12f, at = 1_000L)

        val result = state().updateStopTracking(fix, now = 1_000L, config = config)

        assertEquals(1_000L, result.state.anchorTrust.stopStartedAt)
        assertEquals(fix, result.state.anchorTrust.anchor)
        assertEquals(0L, result.stoppedDurationMs, "the stop starts now, so nothing has elapsed")
    }

    @Test
    fun should_measure_the_stop_from_when_it_opened_not_from_this_fix() {
        val open = state().updateStopTracking(point(at = 1_000L), now = 1_000L, config = config)

        val later = open.state.updateStopTracking(point(at = 31_000L), now = 31_000L, config = config)

        assertEquals(30_000L, later.stoppedDurationMs)
    }

    /**
     * Same-stop refinement, which the freeze doctrine keeps open for the whole stop while no step
     * has been counted: the 30-second cutoff once kept a 260-m approach-drift fix as the anchor
     * while the real 9.8-m fix arrived at second 71 of the same stop.
     */
    @Test
    fun should_refine_the_anchor_to_a_sharper_fix_of_the_same_stop() {
        val open = state().updateStopTracking(point(accuracy = 20f, at = 0L), now = 0L, config = config)

        val refined = open.state.updateStopTracking(
            point(metersNorth = 4.0, accuracy = 6f, at = 71_000L),
            now = 71_000L,
            config = config,
        )

        assertEquals(6f, refined.state.anchorTrust.anchor?.accuracy)
    }

    @Test
    fun should_keep_the_sharper_anchor_when_a_blurrier_fix_of_the_same_stop_arrives() {
        val open = state().updateStopTracking(point(accuracy = 6f, at = 0L), now = 0L, config = config)

        val blurry = open.state.updateStopTracking(
            point(metersNorth = 4.0, accuracy = 30f, at = 10_000L),
            now = 10_000L,
            config = config,
        )

        assertEquals(6f, blurry.state.anchorTrust.anchor?.accuracy)
    }

    // ── A stop is a claim about POSITION ─────────────────────────────────────

    /**
     * The Góndola shape, at its exact numbers: a fix reporting **0.0 m/s** that sits 122 m from the
     * stop's own origin 9.6 s later. Declared Doppler says rest; the track says 12.7 m/s of ground.
     *
     * The load-bearing half is that the sharper fix does not become the anchor. In the field its
     * 6.0 m beat the 10.8 m of the fix that opened the stop, which is precisely how a still-rolling
     * car got pinned in the mouth of a side street. [DET-STOP-MUST-BE-STILL-IN-SPACE-001]
     */
    @Test
    fun should_refuse_a_still_reported_fix_that_its_own_track_proves_was_moving() {
        val origin = point(accuracy = 10.8f, at = 0L)
        val creeping = point(metersNorth = 122.0, accuracy = 6f, at = 9_600L, speed = 0f)
        val stopped = state(
            anchorTrust = AnchorTrust(
                anchor = origin,
                capturedAtStop = 0L,
                stopStartedAt = 0L,
                stopWindowFixes = listOf(origin),
                stopEvidenceSince = 0L,
            ),
        )

        val result = stopped.updateStopTracking(creeping, now = 9_600L, config = config)

        assertTrue(result.trace().contains("stop REFUTED by its own track"), result.trace())
        // The contradiction itself, in the trace: a fix claiming rest, and the ground it covered
        // while claiming it. Not the exact metre count — that is display truncation, not behaviour.
        assertTrue(result.trace().contains("from the stop origin"), result.trace())
        assertTrue(result.trace().contains("while reporting 0.0 m/s"), result.trace())
        assertEquals(
            null,
            result.state.anchorTrust.anchor,
            "the anchor was captured from fixes the track proved were motion — it is disowned, and " +
                "the sharper refuted fix does not take its place",
        )
        assertTrue(result.trace().contains("anchor DISOWNED with its refuted stop"), result.trace())
    }

    /**
     * **A refutation revokes the stop's evidence, not its clock.** The two used to be one field, and
     * separating them is what stops a creeping stop from maturing on the clock alone — while still
     * letting scoring and prompts read the full duration, because asking is the cheap side of the
     * asymmetric doctrine. [DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001]
     */
    @Test
    fun should_keep_the_stop_clock_running_through_a_refutation_while_restarting_its_evidence() {
        val origin = point(at = 0L)
        val stopped = state(
            driveAuthorized = true,
            anchorTrust = AnchorTrust(
                anchor = origin,
                capturedAtStop = 0L,
                stopStartedAt = 0L,
                stopWindowFixes = listOf(origin),
                stopEvidenceSince = 0L,
            ),
        )
        val creeping = point(metersNorth = 400.0, at = 61_000L, speed = 0f)

        val result = stopped.updateStopTracking(creeping, now = 61_000L, config = config)

        assertEquals(61_000L, result.stoppedDurationMs, "the clock is untouched")
        assertFalse(
            result.state.anchorTrust.frozenByRest,
            "…but a stop that spent that clock provably moving has earned no credit toward rest",
        )
        assertEquals(61_000L, result.state.anchorTrust.stopEvidenceSince, "evidence restarts here")
    }

    // ── The stop that opens on the far side of a GPS hole ────────────────────

    /**
     * The car's deceleration to rest happened entirely inside the hole, so this position may be a
     * drive-past point rather than the park. The hole's SIZE is kept, not merely its existence: it
     * is the only bound on how far the phone could have walked before the stream came back.
     * [DET-GAP-ANCHOR-001][DET-GAP-ANCHOR-ZONE-001]
     */
    @Test
    fun should_mark_a_stop_that_opened_after_a_gps_hole_with_the_car_last_seen_driving() {
        val beforeTheHole = point(accuracy = 44f, at = 0L, speed = 17f)
        val afterTheHole = point(metersNorth = 300.0, at = 50_000L, speed = 0f)

        val result = state(previousFix = beforeTheHole)
            .updateStopTracking(afterTheHole, now = 50_000L, config = config)

        assertTrue(result.trace().contains("stop opened after a 50000ms GPS hole"), result.trace())
        assertTrue(result.state.anchorTrust.capture.gapEntered, "the anchor is GAP-ENTERED")
        assertEquals(50_000L, result.state.anchorTrust.capture.gapMs, "and the hole's size is kept")
    }

    /** A hole the car did not enter at driving speed is just a sparse stream, not a gap-entered stop. */
    @Test
    fun should_not_mark_a_gap_when_the_car_was_not_driving_before_the_hole() {
        val strolling = point(accuracy = 10f, at = 0L, speed = 1.2f)

        val result = state(previousFix = strolling)
            .updateStopTracking(point(at = 50_000L), now = 50_000L, config = config)

        assertFalse(result.state.anchorTrust.capture.gapEntered)
        assertEquals(0L, result.state.anchorTrust.capture.gapMs)
    }

    // ── End-of-drive maturation ──────────────────────────────────────────────

    /**
     * Rest proven by EVIDENCE rather than by time: a short trip's destination stop rarely lasts a
     * minute before the user walks off, but a quorum of dense stopped fixes proves the car came to
     * rest here. [DET-SHORT-TRIP-FREEZE-001]
     */
    @Test
    fun should_freeze_the_anchor_when_a_quorum_of_stopped_fixes_proves_the_rest() {
        val anchor = point(accuracy = 6f, at = 0L)
        val stopped = state(
            driveAuthorized = true,
            anchorTrust = AnchorTrust(
                anchor = anchor,
                capturedAtStop = 0L,
                stopStartedAt = 0L,
                stopEvidenceSince = 0L,
                stopWindowFixes = List(config.anchorFreezeStableFixes) { point(accuracy = 6f, at = it * 1_000L) },
            ),
        )

        val result = stopped.updateStopTracking(point(accuracy = 20f, at = 5_000L), now = 5_000L, config = config)

        assertTrue(result.state.anchorTrust.frozenByRest)
        assertTrue(result.trace().contains("anchor FROZEN"), result.trace())
        assertTrue(result.trace().contains("stableFixes="), "the trace must say WHICH proof: ${result.trace()}")
    }

    /** Rest proven by TIME, which is the long-stop half of the same rule. */
    @Test
    fun should_freeze_the_anchor_when_the_stop_has_lasted_long_enough() {
        val anchor = point(accuracy = 6f, at = 0L)
        val stopped = state(
            driveAuthorized = true,
            anchorTrust = AnchorTrust(
                anchor = anchor,
                capturedAtStop = 0L,
                stopStartedAt = 0L,
                stopEvidenceSince = 0L,
                stopWindowFixes = listOf(anchor),
            ),
        )

        val result = stopped.updateStopTracking(
            point(accuracy = 20f, at = config.anchorFreezeStopMs),
            now = config.anchorFreezeStopMs,
            config = config,
        )

        assertTrue(result.state.anchorTrust.frozenByRest)
        assertTrue(result.trace().contains("time="), "the trace must say WHICH proof: ${result.trace()}")
    }

    /**
     * The session must have witnessed driving. Without it, "the car came to rest here" is a claim
     * about a car nobody saw move — the phone has simply been sitting still.
     */
    @Test
    fun should_not_freeze_the_anchor_of_a_session_that_never_witnessed_a_drive() {
        val anchor = point(accuracy = 6f, at = 0L)
        val stopped = state(
            driveAuthorized = false,
            anchorTrust = AnchorTrust(
                anchor = anchor,
                capturedAtStop = 0L,
                stopStartedAt = 0L,
                stopEvidenceSince = 0L,
                stopWindowFixes = List(config.anchorFreezeStableFixes) { point(accuracy = 6f, at = it * 1_000L) },
            ),
        )

        val result = stopped.updateStopTracking(point(accuracy = 20f, at = 5_000L), now = 5_000L, config = config)

        assertFalse(result.state.anchorTrust.frozenByRest)
    }

    /**
     * A pinned anchor is never re-captured at a LATER stop: the car provably rests at the anchor, so
     * a new stop is the pedestrian standing still. The fix offered here is far sharper than the
     * anchor precisely so that "sharper wins" cannot be what carries the assert.
     * [ANCHOR-LOCK-001][DET-ANCHOR-FREEZE-001]
     */
    @Test
    fun should_not_recapture_a_pinned_anchor_at_a_later_stop_however_sharp_the_new_fix_is() {
        val anchor = point(accuracy = 25f, at = 0L)
        val parked = state(
            anchorTrust = AnchorTrust(anchor = anchor, capturedAtStop = 0L, frozenByRest = true),
        )

        val result = parked.updateStopTracking(
            point(metersNorth = 80.0, accuracy = 3f, at = 300_000L),
            now = 300_000L,
            config = config,
        )

        assertEquals(anchor, result.state.anchorTrust.anchor, "the car rests where it rested")
    }

    // ── The moving branch ────────────────────────────────────────────────────

    @Test
    fun should_report_no_stopped_duration_on_a_moving_fix() {
        val result = state().updateStopTracking(point(speed = 12f, at = 1_000L), now = 1_000L, config = config)
        assertEquals(0L, result.stoppedDurationMs)
    }

    @Test
    fun should_clear_an_unpinned_anchor_on_real_driving() {
        val moving = state(anchorTrust = AnchorTrust(anchor = point(), capturedAtStop = 0L))

        val result = moving.updateStopTracking(
            point(metersNorth = 60.0, speed = 12f, at = 5_000L),
            now = 5_000L,
            config = config,
        )

        assertNull(result.state.anchorTrust.anchor)
    }

    /**
     * The 2026-07-04 incident: brisk walking away from the parked car produced Doppler fixes of
     * 2.5–3.6 m/s — above the clear bar — that wiped the true anchor, and the park re-anchored 55 m
     * away where the user next stood still. Once egress steps are counted, only real driving clears
     * it. [ANCHOR-LOCK-001]
     */
    @Test
    fun should_hold_a_step_locked_anchor_against_the_walking_speed_band() {
        val anchor = point()
        val locked = state(
            anchorTrust = AnchorTrust(anchor = anchor, capturedAtStop = 0L),
            steps = config.anchorLockEgressSteps,
        )

        val result = locked.updateStopTracking(
            point(metersNorth = 30.0, speed = 3.4f, at = 12_000L),
            now = 12_000L,
            config = config,
        )

        assertEquals(anchor, result.state.anchorTrust.anchor)
        assertTrue(result.trace().contains("anchor LOCKED"), result.trace())
    }

    /**
     * A rest this session witnessed is not overturned by one sample. The refusal is traced on
     * purpose and reads as PROVISIONAL — without the line, the trip-speed fix and the unmoved anchor
     * would sit next to each other with nothing saying why.
     * [DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001]
     */
    @Test
    fun should_hold_a_frozen_anchor_against_a_lone_trip_speed_fix() {
        val anchor = point()
        val frozen = state(
            anchorTrust = AnchorTrust(anchor = anchor, capturedAtStop = 0L, frozenByRest = true),
        )

        val result = frozen.updateStopTracking(
            point(metersNorth = 40.0, speed = 12f, at = 4_000L),
            now = 4_000L,
            config = config,
        )

        assertEquals(anchor, result.state.anchorTrust.anchor)
        assertTrue(result.trace().contains("anchor HELD against a lone trip-speed fix"), result.trace())
        assertEquals(1, result.state.anchorTrust.realDriveStreak, "run 1 of ${config.pinnedAnchorRealDriveFixes}")
    }

    /** …and the corroborating fix is what takes it. The run, not the sample, is the evidence. */
    @Test
    fun should_release_a_frozen_anchor_once_the_real_drive_run_corroborates_itself() {
        val frozen = state(
            anchorTrust = AnchorTrust(
                anchor = point(),
                capturedAtStop = 0L,
                frozenByRest = true,
                realDriveStreak = config.pinnedAnchorRealDriveFixes - 1,
            ),
        )

        val result = frozen.updateStopTracking(
            point(metersNorth = 40.0, speed = 12f, at = 4_000L),
            now = 4_000L,
            config = config,
        )

        assertNull(result.state.anchorTrust.anchor)
    }

    /**
     * The fix that says it is driving and cannot prove it. `src=` is what turns a wall of these into
     * an answer: 38 of them in eleven minutes could not be told apart as bad GNSS geometry or a
     * network fix carrying a speed it never measured.
     * [DET-A-FIX-MUST-SAY-WHERE-IT-CAME-FROM-001]
     */
    @Test
    fun should_ignore_a_driving_speed_fix_whose_accuracy_cannot_back_it_and_say_where_it_came_from() {
        val anchor = point()
        val watching = state(anchorTrust = AnchorTrust(anchor = anchor, capturedAtStop = 0L))
        val blurry = point(
            metersNorth = 60.0,
            accuracy = config.minGpsAccuracyForDriving + 30f,
            at = 5_000L,
            speed = 12f,
        )

        val result = watching.updateStopTracking(blurry, now = 5_000L, config = config)

        assertTrue(result.trace().contains("ignoring driving-speed fix with poor accuracy"), result.trace())
        assertTrue(result.trace().contains("src="), "the fix must say which world produced it: ${result.trace()}")
        assertEquals(anchor, result.state.anchorTrust.anchor, "an unprovable claim moves nothing")
    }

    /**
     * The measured departure rides out on the result rather than being recomputed by the caller —
     * a second call site of a pure function agreeing by luck is the failure shape
     * `DetectionSessionState.onFix` already refuses.
     * [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001]
     */
    @Test
    fun should_carry_the_measured_sustained_departure_out_with_the_reduction() {
        val departing = state(anchorTrust = AnchorTrust(anchor = point(), capturedAtStop = 0L))

        val result = departing.updateStopTracking(
            point(metersNorth = 600.0, speed = 12f, at = 60_000L),
            now = 60_000L,
            config = config,
        )

        assertNotNull(result.sustainedDeparture)
        assertTrue(result.trace().contains("SUSTAINED DEPARTURE"), result.trace())
    }

    @Test
    fun should_carry_no_departure_when_the_fix_is_merely_stopped() {
        val result = state().updateStopTracking(point(at = 1_000L), now = 1_000L, config = config)
        assertNull(result.sustainedDeparture, "a stopped fix departs from nothing")
    }

    /**
     * The egress walk as GPS sees it when the step counter is mute: pedestrian-band fixes while the
     * anchor is frozen. It shares the accuracy gate with the driving test, not the question.
     * [DET-KINEMATIC-EGRESS-001]
     */
    @Test
    fun should_count_a_pedestrian_band_fix_as_kinematic_egress_while_the_anchor_is_frozen() {
        val walking = state(
            anchorTrust = AnchorTrust(anchor = point(), capturedAtStop = 0L, frozenByRest = true),
        )

        val result = walking.updateStopTracking(
            point(metersNorth = 12.0, speed = 1.5f, at = 8_000L),
            now = 8_000L,
            config = config,
        )

        assertEquals(1, result.state.anchorTrust.kinematicEgressFixes)
        assertNotNull(result.state.anchorTrust.anchor, "the walk does not clear the anchor it measures")
    }

    /** No freeze, no kinematic egress: the same fixes describe an approach on the other side of it. */
    @Test
    fun should_not_count_kinematic_egress_while_the_anchor_is_still_open() {
        val approaching = state(anchorTrust = AnchorTrust(anchor = point(), capturedAtStop = 0L))

        val result = approaching.updateStopTracking(
            point(metersNorth = 12.0, speed = 1.5f, at = 8_000L),
            now = 8_000L,
            config = config,
        )

        assertEquals(0, result.state.anchorTrust.kinematicEgressFixes)
    }

    private companion object {
        const val BASE_LAT = 36.6119
        const val BASE_LON = -6.2805
        const val METERS_PER_DEGREE_LAT = 111_320.0
    }
}
