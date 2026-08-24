package io.apptolast.paparcar.domain.detection.physics

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DET-CREDIBLE-DRIVE-001][DET-DRIVE-PROOF-001] Corroboration by displacement — believing the track
 * instead of the fix.
 *
 * The cases here are the ones the KDocs name, because those are the traces these functions were
 * calibrated against and therefore the ones a later "simplification" would break.
 */
class DriveCorroborationTest {

    private fun at(meters: Double, tMs: Long, accuracy: Float = 3f, speed: Float = 0f) =
        GpsPoint(
            latitude = meters / 111_320.0, longitude = 0.0,
            accuracy = accuracy, timestamp = tMs, speed = speed,
        )

    private val bounds = DriveProofBounds(
        windowMinMs = 20_000L,
        windowMaxMs = 60_000L,
        hopMarginMeters = 10f,
        minDistanceMeters = 150f,
        maxRateMps = 55f,
        progressFraction = 0.25f,
        retentionSlackMs = 30_000L,
        maxRetainedFixes = 48,
    )

    // ── isCorroboratedVehicleHop ──────────────────────────────────────────────

    /** Field 2026-07-16, Galeote: the car rolling to the kerb — 23.7 m in 5 s against 9.9 m of
     *  joint accuracy. This one MUST pass, or the deceleration taints a correct anchor. */
    @Test
    fun should_corroborate_the_hop_when_the_car_rolls_to_the_kerb() {
        assertTrue(
            isCorroboratedVehicleHop(
                prev = at(0.0, 0L, accuracy = 4.9f),
                curr = at(23.7, 5_000L, accuracy = 5.0f),
                hopMarginMeters = 10f, minRateMps = 2.5f,
            ),
        )
    }

    /**
     * Field 2026-07-15, Camelias: the walk-back GPS recovery swing. Its envelopes balloon exactly
     * when it "moves" — 11.9 m of displacement against 14.1 m of joint noise. It must fail, and its
     * failing is what keeps the drag-to-home laundering impossible.
     */
    @Test
    fun should_refuse_the_hop_when_the_envelopes_balloon_with_the_movement() {
        assertFalse(
            isCorroboratedVehicleHop(
                prev = at(0.0, 0L, accuracy = 7.0f),
                curr = at(11.9, 4_000L, accuracy = 7.1f),
                hopMarginMeters = 10f, minRateMps = 2.5f,
            ),
        )
    }

    @Test
    fun should_refuse_the_hop_when_there_is_no_previous_fix() {
        assertFalse(
            isCorroboratedVehicleHop(null, at(500.0, 5_000L), hopMarginMeters = 10f, minRateMps = 2.5f),
        )
    }

    /** Far but slow is a walk, not a hop: the rate is half the question. */
    @Test
    fun should_refuse_the_hop_when_the_ground_rate_is_pedestrian() {
        assertFalse(
            isCorroboratedVehicleHop(
                prev = at(0.0, 0L), curr = at(100.0, 120_000L),
                hopMarginMeters = 10f, minRateMps = 2.5f,
            ),
        )
    }

    // ── sustainedDepartureFromAnchor ──────────────────────────────────────────

    /** Field 2026-07-15, Enamorados: the OEM starved every fix of credible accuracy, and this is
     *  what unfreezes the anchor anyway — the track, not the fix. */
    @Test
    fun should_report_the_measurement_when_the_position_ran_from_the_anchor() {
        val d = sustainedDepartureFromAnchor(
            anchor = at(0.0, 0L, accuracy = 20f),
            anchorStoppedSinceMs = 0L,
            fix = at(366.0, 30_000L, accuracy = 52.4f, speed = 10.12f),
            nowMs = 30_000L,
            movingBarMps = 2.5f, floorMeters = 150f, minRateMps = 5f, maxRateMps = 55f,
        )
        assertNotNull(d, "a 366 m run in 30 s is a drive whatever the accuracy says")
        assertEquals(12.2, d.rateMps, 0.1)
    }

    /**
     * A pedestrian-band fix never carries this verdict however far the anchor sits — that judgement
     * belongs to the egress machinery, and letting it in here is how a walk becomes a drive.
     */
    @Test
    fun should_stay_silent_when_the_current_fix_is_in_the_pedestrian_band() {
        assertNull(
            sustainedDepartureFromAnchor(
                anchor = at(0.0, 0L), anchorStoppedSinceMs = 0L,
                fix = at(400.0, 30_000L, speed = 1.1f), nowMs = 30_000L,
                movingBarMps = 2.5f, floorMeters = 150f, minRateMps = 5f, maxRateMps = 55f,
            ),
        )
    }

    /** The walk home: real displacement, real time, but ~2 m/s average. Below the rate window. */
    @Test
    fun should_stay_silent_when_the_average_rate_is_a_walk() {
        assertNull(
            sustainedDepartureFromAnchor(
                anchor = at(0.0, 0L), anchorStoppedSinceMs = 0L,
                fix = at(400.0, 200_000L, speed = 3f), nowMs = 200_000L,
                movingBarMps = 2.5f, floorMeters = 150f, minRateMps = 5f, maxRateMps = 55f,
            ),
        )
    }

    /** The cache teleport claims an absurd rate. Above the window — refused from the other side. */
    @Test
    fun should_stay_silent_when_the_rate_is_a_teleport() {
        assertNull(
            sustainedDepartureFromAnchor(
                anchor = at(0.0, 0L), anchorStoppedSinceMs = 0L,
                fix = at(6_000.0, 30_000L, speed = 30f), nowMs = 30_000L,
                movingBarMps = 2.5f, floorMeters = 150f, minRateMps = 5f, maxRateMps = 55f,
            ),
        )
    }

    // ── corroboratesDrive ─────────────────────────────────────────────────────

    /** Field Calle Gavia: the whole drive is ONE 36-s hop of 255 m with no in-window witnesses.
     *  A sparse stream must still be able to prove itself. */
    @Test
    fun should_prove_the_drive_when_the_stream_is_sparse_and_the_hop_is_real() {
        val curr = at(255.0, 36_000L, speed = 12f)
        assertTrue(corroboratesDrive(listOf(at(0.0, 0L)), curr, bounds))
    }

    /**
     * Field 2026-07-27, the at-home mirage: the phone sat still for every in-window fix and "moved"
     * only at the burst. The progress clause is what tells flat-then-jump from a real drive, and it
     * is the term most likely to look redundant to someone tidying this up later.
     */
    @Test
    fun should_refuse_the_drive_when_the_window_is_flat_then_jump() {
        val history = listOf(
            at(0.0, 0L),
            at(0.5, 10_000L),
            at(0.5, 20_000L),
            at(1.0, 30_000L), // still at home, in the window's late half
        )
        assertFalse(
            corroboratesDrive(history, at(400.0, 36_000L, speed = 12f), bounds),
            "the late-half witnesses never left home — this is a burst, not a drive",
        )
    }

    @Test
    fun should_refuse_the_drive_when_the_look_back_fix_is_too_young_to_say_anything() {
        // 5 s old: inside windowMinMs, so there is nothing eligible to judge against.
        assertFalse(corroboratesDrive(listOf(at(0.0, 31_000L)), at(400.0, 36_000L, speed = 12f), bounds))
    }

    // ── pruneRecentFixes ──────────────────────────────────────────────────────

    /**
     * **The invariant [DriveProofBounds] exists for.** The ring must keep fixes at least as old as
     * the widest look-back window, or the window finds nothing to look back at and a real drive
     * silently stops proving itself — with no error anywhere. If someone ever tunes one number
     * without the other, this is where it shows.
     */
    @Test
    fun should_keep_fixes_old_enough_to_serve_the_widest_window() {
        val curr = at(500.0, 100_000L)
        val oldestUseful = at(0.0, 100_000L - bounds.windowMaxMs)
        val kept = pruneRecentFixes(listOf(oldestUseful), curr, bounds)
        assertTrue(
            oldestUseful in kept,
            "the ring dropped a fix the window would still have looked back at",
        )
    }

    @Test
    fun should_drop_fixes_past_the_retention_window() {
        val curr = at(500.0, 200_000L)
        val ancient = at(0.0, 0L)
        assertFalse(ancient in pruneRecentFixes(listOf(ancient), curr, bounds))
    }

    @Test
    fun should_cap_the_ring_when_the_stream_is_hot() {
        val curr = at(500.0, 60_000L)
        val history = (1..200).map { at(it.toDouble(), 60_000L - it) }
        assertEquals(bounds.maxRetainedFixes, pruneRecentFixes(history, curr, bounds).size)
    }
}
