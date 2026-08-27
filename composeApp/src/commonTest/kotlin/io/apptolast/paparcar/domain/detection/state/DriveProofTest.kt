package io.apptolast.paparcar.domain.detection.state

import io.apptolast.paparcar.domain.detection.physics.DriveProofBounds
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [09 §5] The transitions of the fourth sub-state.
 *
 * The short-hop profile has its own file (`DriveProofShortHopTest`, ported verbatim). What is pinned
 * here is the part that had no test of its own: the **three different lifetimes** these values have,
 * and the retroactive promotion of the banked peak.
 */
class DriveProofTest {

    private val config = ParkingDetectionConfig()

    private val bounds = DriveProofBounds(
        windowMinMs = config.driveProofWindowMinMs,
        windowMaxMs = config.driveProofWindowMaxMs,
        hopMarginMeters = config.credibleDriveHopMarginMeters,
        minDistanceMeters = config.minimumTripDistanceMeters,
        maxRateMps = config.sustainedDepartureMaxRateMps,
        progressFraction = 0.5f,
        retentionSlackMs = 30_000L,
        maxRetainedFixes = 40,
    )

    private fun fix(speed: Float, at: Long, accuracy: Float = 8f, north: Double = 0.0) =
        GpsPoint(36.6119 + north, -6.2805, accuracy, at, speed)

    private fun DriveProof.feed(
        fix: GpsPoint,
        credible: Boolean = true,
        sustainedDepartureRateMps: Double? = null,
    ) = onFix(
        fix = fix,
        nowMs = fix.timestamp,
        credibleSpeedFix = credible,
        departureAnchor = null,
        departureFenceRadiusMeters = 80f,
        elapsedSinceArmMs = fix.timestamp,
        bounds = bounds,
        config = config,
        sustainedDepartureRateMps = sustainedDepartureRateMps,
    )

    // ── [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001] the rate that survives a hole ───────────

    /**
     * The band clock and the rate measure the SAME fact and only one of them survives OEM batching.
     * Field 2026-08-26 (Redmi, Valdés→Góndola): in-band fixes 163 s and 200 s apart, so
     * `creditSpeedBand` credited nothing past the 60 s window — while the anchor baseline read
     * 24,6 m/s. Both are asserted together on purpose: if a future change makes the clock tick across
     * holes, THIS is where the two stop being independent witnesses.
     */
    @Test
    fun should_bank_the_sustained_rate_while_the_band_clock_stays_starved() {
        val batched = DriveProof()
            .feed(fix(speed = 14.97f, at = 0L), sustainedDepartureRateMps = 19.7)
            .feed(fix(speed = 26.2f, at = 163_000L), sustainedDepartureRateMps = 24.6)
            .feed(fix(speed = 13.42f, at = 363_000L), sustainedDepartureRateMps = 23.4)

        assertEquals(0L, batched.motorBandMs, "gaps of 163 s and 200 s credit no band time at all")
        assertEquals(24.6f, batched.motorDisplacementRateMps, "the high-water mark of the baselines")
    }

    /** A high-water mark, like the peak: a later, slower leg does not un-cover ground. */
    @Test
    fun should_keep_the_fastest_sustained_rate_when_a_slower_leg_follows() {
        val after = DriveProof()
            .feed(fix(speed = 20f, at = 0L), sustainedDepartureRateMps = 24.6)
            .feed(fix(speed = 3f, at = 5_000L), sustainedDepartureRateMps = 6.0)
            .feed(fix(speed = 0f, at = 10_000L), sustainedDepartureRateMps = null)

        assertEquals(24.6f, after.motorDisplacementRateMps)
    }

    /**
     * NOT drive-proof-gated, exactly like `motorBandMs` and for its reason: the value's job is to
     * REFUTE a human-powered claim, never to buy a silent pin. Gating it behind the proof would
     * recreate the 2026-08-20 shape — a session that measurably had a motor in it dying judged a
     * bicycle because a different statistic had not matured yet.
     */
    @Test
    fun should_bank_the_rate_even_before_the_track_has_proven_a_drive() {
        val after = DriveProof().feed(fix(speed = 20f, at = 0L), sustainedDepartureRateMps = 24.6)

        assertFalse(after.isProven)
        assertEquals(24.6f, after.motorDisplacementRateMps)
    }

    // ── The promotion ─────────────────────────────────────────────────────────

    /**
     * [DET-DRIVE-PROOF-001] The statistic every confirm path reads stays ZERO while the peak banks.
     * A lone mirage — 45 m/s at claimed accuracy 5 m from a phone indoors — used to set it for the
     * whole session and pin the living room (field 2026-07-27).
     */
    @Test
    fun should_bank_the_peak_without_publishing_it_until_the_drive_is_proven() {
        val after = DriveProof().feed(fix(speed = 45f, at = 1_000L))
        assertEquals(45f, after.peakMps)
        assertEquals(0f, after.provenMaxSpeedMps, "an unproven session reports no driving")
        assertFalse(after.isProven)
        assertEquals(0L, after.provenDrivingBandMs, "and no band time either")
    }

    /** …and the moment the proof lands, the banked peak is published retroactively. */
    @Test
    fun should_publish_the_banked_peak_the_instant_the_proof_arrives() {
        val banked = DriveProof(peakMps = 30f, shortHopRun = config.shortHopProofFixes - 1)
        val proven = banked.onFix(
            fix = fix(speed = 8.3f, at = 180_000L, accuracy = 10f, north = 0.0081),
            nowMs = 180_000L,
            credibleSpeedFix = true,
            departureAnchor = fix(speed = 0f, at = 0L),
            departureFenceRadiusMeters = 80f,
            elapsedSinceArmMs = 180_000L,
            bounds = bounds,
            config = config,
        )
        assertEquals(DriveProofSource.SHORT_HOP, proven.proven)
        assertEquals(30f, proven.provenMaxSpeedMps, "the peak the session already had, published")
    }

    /** An incredible fix cannot even bank: accuracy gates the peak before anything else. */
    @Test
    fun should_refuse_to_bank_a_peak_from_a_fix_that_does_not_know_where_it_is() {
        assertEquals(0f, DriveProof().feed(fix(speed = 45f, at = 1_000L), credible = false).peakMps)
    }

    // ── Three lifetimes ───────────────────────────────────────────────────────

    /**
     * **The proof LATCHES.** Once the car provably drove, no later fix un-drives it — the proof is a
     * fact about the trip, not a property of the current fix. A latch that a slow fix could reset
     * would lose the park of everyone who stops at a light after proving their drive.
     */
    @Test
    fun should_keep_the_proof_once_it_is_earned_however_slow_the_session_gets() {
        val proven = DriveProof(proven = DriveProofSource.TRACK_WINDOW, peakMps = 20f)
        val after = proven.feed(fix(speed = 0f, at = 60_000L)).feed(fix(speed = 0f, at = 120_000L))
        assertEquals(DriveProofSource.TRACK_WINDOW, after.proven)
        assertEquals(20f, after.provenMaxSpeedMps)
    }

    /** …and it keeps the provenance of whatever proved it FIRST. */
    @Test
    fun should_not_relabel_a_hop_when_a_track_window_arrives_later() {
        val hop = DriveProof(proven = DriveProofSource.SHORT_HOP)
        val after = hop.feed(fix(speed = 20f, at = 30_000L))
        assertEquals(DriveProofSource.SHORT_HOP, after.proven)
    }

    /**
     * **The short-hop run is a RUN.** Any fix that fails the geometry breaks it back to zero, so a
     * lone cache teleport never accumulates into a proof.
     */
    @Test
    fun should_break_the_short_hop_run_on_the_first_fix_that_fails_the_geometry() {
        val running = DriveProof(shortHopRun = 2)
        assertEquals(0, running.feed(fix(speed = 0f, at = 10_000L)).shortHopRun)
    }

    /** **The band clocks LATCH too**, and they credit only gaps the window trusts. */
    @Test
    fun should_accumulate_the_band_clock_across_a_gap_the_window_trusts() {
        // Timestamps start at a real epoch: `creditSpeedBand` treats 0 as "no previous in-band
        // fix", so a fixture anchored at 0 silently credits nothing.
        val first = DriveProof().feed(fix(speed = 20f, at = 1_000L))
        val second = first.feed(fix(speed = 20f, at = 11_000L))
        assertEquals(10_000L, second.drivingBandMs)
        // A slow fix credits nothing, and takes nothing away.
        assertEquals(10_000L, second.feed(fix(speed = 0f, at = 21_000L)).drivingBandMs)
    }

    /**
     * [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] The motor clock is deliberately NOT drive-proof-gated:
     * its job is to REFUTE a human-powered claim, never to buy a silent pin. Doubting the veto costs
     * a prompt; believing it cost a car (field 2026-08-20: 361 s above 40 km/h and the session still
     * died judged a bicycle).
     */
    @Test
    fun should_run_the_motor_clock_even_on_a_session_that_has_proven_nothing() {
        val after = DriveProof()
            .feed(fix(speed = config.motorProofSpeedMps + 1f, at = 1_000L))
            .feed(fix(speed = config.motorProofSpeedMps + 1f, at = 21_000L))
        assertFalse(after.isProven, "no track corroboration, so no proof")
        assertEquals(20_000L, after.motorBandMs, "and the refutation clock runs anyway")
    }

    /** [DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001] A peak is one sample; the COUNT is what tells a spike
     *  from a drive the look-back merely failed to corroborate. */
    @Test
    fun should_count_the_in_band_fixes_separately_from_the_peak() {
        val after = DriveProof().feed(fix(speed = 20f, at = 0L)).feed(fix(speed = 0f, at = 10_000L))
        assertEquals(1, after.credibleFixCount)
        assertEquals(20f, after.peakMps)
    }

    /** The ring expires by TIME: it is a window, not a memory. */
    @Test
    fun should_drop_ring_fixes_older_than_the_window_plus_its_slack() {
        val stale = fix(speed = 20f, at = 0L)
        val after = DriveProof(recentFixes = listOf(stale)).feed(fix(speed = 20f, at = 10 * 60_000L))
        assertFalse(stale in after.recentFixes, "a fix ten minutes back witnesses nothing")
    }

    /** The freshness snapshot the concurrent-step cadence judge reads travels with every fix. */
    @Test
    fun should_stamp_the_freshness_snapshot_on_every_fix() {
        val after = DriveProof().feed(fix(speed = 1f, at = 7_000L), credible = false)
        assertEquals(7_000L, after.lastFixSeenAtMs)
        assertFalse(after.lastFixCredible)
        assertNull(after.proven)
    }

    /** No departure pin — a manual or AR-armed trip — can never prove a short hop. */
    @Test
    fun should_never_prove_a_short_hop_without_a_pin_to_measure_from() {
        val after = DriveProof(shortHopRun = config.shortHopProofFixes - 1)
            .feed(fix(speed = 20f, at = 60_000L, north = 0.0081))
        assertTrue(after.proven != DriveProofSource.SHORT_HOP)
        assertEquals(0, after.shortHopRun)
    }
}
