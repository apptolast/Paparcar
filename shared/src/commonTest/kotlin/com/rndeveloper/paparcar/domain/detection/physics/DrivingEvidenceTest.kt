package com.rndeveloper.paparcar.domain.detection.physics

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] The three bars of `Measured`, each one exercised against
 * the field night of 2026-08-29→30 (Redmi `2201117TY`, `parkdiag.log`, 6.464 lines).
 *
 * The numbers in these tests are MEASURED, not invented — counted with the same definition
 * `DriveProof.onFix` applies (`speed >= 5,0 m/s` AND `acc <= 50 m`):
 *
 * | session                    | fixes | credible | excursion | band     | reality              |
 * |----------------------------|-------|----------|-----------|----------|----------------------|
 * | real trip 21:47            | 324   | 86       | 3.098 m   | 30.001ms | correct pin          |
 * | FP "La Parafarmacia" 23:47 | 102   | **1**    | **72 m**  | **0**    | silent pin at 0.9 🔴 |
 * | home 01:20 (real trip)     | 376   | **7**    | 2.493 m   | 45.021ms | real drive, bad anchor|
 */
class DrivingEvidenceTest {

    private val config = ParkingDetectionConfig()

    // ── The false positive, and each bar killing it on its own ──────────────────────────────────

    @Test
    fun should_be_weak_when_the_parafarmacia_session_is_replayed() {
        // FIELD REPLAY. Every bar fails at once, which is why this session should never have been
        // able to plant anything: 1 credible fix, 72 m of excursion, and no band time at all.
        val evidence = drivingEvidence(
            credibleFixes = 1,
            excursionMeters = 72.0,
            sustainedBandMs = 0L,
            shortHopProven = false,
            config = config,
        )
        val weak = assertIs<DrivingEvidence.Weak>(evidence)
        assertEquals(1, weak.credibleFixes)
        assertFalse(evidence.mayConfirmSilently)
        // It may still ASK: one credible fix is not nothing, and doctrine says that in doubt we ask.
        assertTrue(evidence.mayAskAboutAPark)
    }

    @Test
    fun should_be_weak_when_only_the_fix_count_bar_fails() {
        // Neutralisation: hand it the real trip's excursion and band, keep the FP's fix count.
        // If this ever returns Measured, the count bar has stopped doing anything.
        val evidence = drivingEvidence(
            credibleFixes = 1,
            excursionMeters = 3098.0,
            sustainedBandMs = 30_001L,
            shortHopProven = false,
            config = config,
        )
        assertIs<DrivingEvidence.Weak>(evidence)
    }

    @Test
    fun should_be_weak_when_only_the_excursion_bar_fails() {
        // The mirage shape with everything else healthy: plenty of credible fixes and band time,
        // but the position never got anywhere. 71,6 m out and 64,8 m back is not a trip.
        val evidence = drivingEvidence(
            credibleFixes = 40,
            excursionMeters = 72.0,
            sustainedBandMs = 30_001L,
            shortHopProven = false,
            config = config,
        )
        val weak = assertIs<DrivingEvidence.Weak>(evidence)
        assertTrue(weak.why.contains("72"), "the reason must name the distance it fell short at: ${weak.why}")
    }

    @Test
    fun should_be_weak_when_only_the_sustained_band_bar_fails() {
        val evidence = drivingEvidence(
            credibleFixes = 40,
            excursionMeters = 3098.0,
            sustainedBandMs = 0L,
            shortHopProven = false,
            config = config,
        )
        assertIs<DrivingEvidence.Weak>(evidence)
    }

    // ── The real trips of the same night must survive all three ─────────────────────────────────

    @Test
    fun should_be_measured_when_the_real_2147_trip_is_replayed() {
        val evidence = drivingEvidence(
            credibleFixes = 86,
            excursionMeters = 3098.0,
            sustainedBandMs = 30_001L,
            shortHopProven = false,
            config = config,
        )
        val measured = assertIs<DrivingEvidence.Measured>(evidence)
        assertEquals(86, measured.credibleFixes)
        assertTrue(evidence.mayConfirmSilently)
    }

    @Test
    fun should_be_measured_when_the_calle_gavia_skeletal_stream_is_replayed() {
        // THE TRACE THAT SET THE BAR. Field 2026-07-04, the correct Calle Gavia park on a skeletal
        // MIUI stream: 11 fixes in the whole session, of which only TWO are at driving speed with
        // credible accuracy. The redesign proposed a bar of 5 and this park went silently missing —
        // its own replay test (`calle_gavia_001_correct_detection_still_anchors_at_calle_gavia`)
        // is what caught it. A fix count measures OS sampling density, not driving; the physical
        // bars are what this trace clears with room to spare, 543 m and 36 s.
        val evidence = drivingEvidence(
            credibleFixes = 2,
            excursionMeters = 543.0,
            sustainedBandMs = 36_000L,
            shortHopProven = false,
            config = config,
        )
        assertIs<DrivingEvidence.Measured>(evidence)
    }

    @Test
    fun should_be_measured_when_the_home_trip_is_replayed_despite_its_thin_margin() {
        // The weakest real trip of the night, and the one that documents how thin the margin is:
        // SEVEN credible fixes against a bar of five. The redesign's §6.1 credited this session
        // with 44 — that figure counts `speed >= 5` without the accuracy gate, and 37 of those
        // fixes carried accuracy worse than 50 m. The drive was real; the GPS was not.
        val evidence = drivingEvidence(
            credibleFixes = 7,
            excursionMeters = 2493.0,
            sustainedBandMs = 45_021L,
            shortHopProven = false,
            config = config,
        )
        assertIs<DrivingEvidence.Measured>(evidence)
    }

    // ── The tiers at their edges ────────────────────────────────────────────────────────────────

    @Test
    fun should_be_none_when_not_one_credible_driving_fix_was_seen() {
        // The walking-noise sessions of that night (23:30, 00:13, 01:59) all sat here.
        val evidence = drivingEvidence(
            credibleFixes = 0,
            excursionMeters = 16.0,
            sustainedBandMs = 0L,
            shortHopProven = false,
            config = config,
        )
        assertEquals(DrivingEvidence.None, evidence)
        assertFalse(evidence.mayConfirmSilently)
        // None may not even ask: a question about a park nothing suggests happened has no good answer.
        assertFalse(evidence.mayAskAboutAPark)
    }

    @Test
    fun should_be_measured_when_a_short_hop_is_proven_below_the_excursion_bar() {
        // A 100 m re-park down the street. Without the exemption this is a false negative: the
        // excursion bar is 150 m, and SHORT_HOP's own proof is already a corroborated displacement.
        val evidence = drivingEvidence(
            credibleFixes = 6,
            excursionMeters = 100.0,
            sustainedBandMs = 30_001L,
            shortHopProven = true,
            config = config,
        )
        assertIs<DrivingEvidence.Measured>(evidence)
    }

    @Test
    fun should_be_weak_when_the_fix_count_sits_exactly_one_below_the_bar() {
        val evidence = drivingEvidence(
            credibleFixes = config.minDrivingFixesForConfirm - 1,
            excursionMeters = 3098.0,
            sustainedBandMs = 30_001L,
            shortHopProven = false,
            config = config,
        )
        assertIs<DrivingEvidence.Weak>(evidence)
    }

    @Test
    fun should_be_measured_when_the_fix_count_sits_exactly_on_the_bar() {
        val evidence = drivingEvidence(
            credibleFixes = config.minDrivingFixesForConfirm,
            excursionMeters = 3098.0,
            sustainedBandMs = 30_001L,
            shortHopProven = false,
            config = config,
        )
        assertIs<DrivingEvidence.Measured>(evidence)
    }
}
