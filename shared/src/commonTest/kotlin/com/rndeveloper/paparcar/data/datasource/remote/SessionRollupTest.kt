package com.rndeveloper.paparcar.data.datasource.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DET-A-SESSION-ROLLUP-MUST-USE-THE-NUMBERS-THE-VERDICT-USED-001] The session digest, which had
 * never been asked a single question because it lived inside a Firestore-bound class.
 *
 * The case being pinned is field 2026-08-16 (Samsung SM-A536B, session `1786873042480`): a header
 * reading `vmax 80km/h` above an outcome reading `aborted_unattended_no_drive`. Both were correct
 * and the pair was unreadable — the 80 km/h fix carried 180 m of accuracy, so no verdict ever saw
 * it.
 */
class SessionRollupTest {

    private val drivingBarKmh = 18f
    private val accuracyGate = 50f

    private fun SessionRollup.fix(speedKmh: Float, accuracyMeters: Float?, lat: Double? = null, lon: Double? = null) =
        onFix(
            speedKmh = speedKmh,
            accuracyMeters = accuracyMeters,
            latitude = lat,
            longitude = lon,
            drivingBarKmh = drivingBarKmh,
            accuracyGateMeters = accuracyGate,
        )

    @Test
    fun should_report_the_credible_peak_beside_the_claimed_one_when_the_fast_fixes_were_coarse() {
        // The Samsung's stream in miniature: the fast fixes are the coarse ones.
        val r = SessionRollup(startedAt = 0L)
        r.fix(speedKmh = 80f, accuracyMeters = 180f)   // the rumour the header used to print alone
        r.fix(speedKmh = 49f, accuracyMeters = 49.5f)  // the best fix a verdict could actually use
        r.fix(speedKmh = 3f, accuracyMeters = 8f)

        assertEquals(80f, r.maxSpeedKmh, "the claimed peak is kept — a wild one is itself a symptom")
        assertEquals(49f, r.credibleMaxSpeedKmh)

        val summary = r.summary(outcome = "aborted_unattended_no_drive", endedAtMs = 60_000L)
        assertTrue(
            summary.contains("vmax 80km/h (cred 49)"),
            "the digest must carry both peaks so the header cannot contradict the verdict — was: $summary",
        )
    }

    @Test
    fun should_count_a_coarse_fast_fix_as_driving_but_never_as_credible() {
        val r = SessionRollup()
        r.fix(speedKmh = 80f, accuracyMeters = 180f)
        r.fix(speedKmh = 25f, accuracyMeters = 20f)

        assertEquals(2, r.drivingFixes, "the ungated count keeps counting, so old sessions stay comparable")
        assertEquals(1, r.credibleDrivingFixes, "…and only the gated one matches what the confirm paths read")
    }

    @Test
    fun should_treat_a_fix_without_accuracy_as_not_credible() {
        // A fix that cannot say how wrong it might be is exactly what the gate holds back.
        val r = SessionRollup()
        r.fix(speedKmh = 90f, accuracyMeters = null)

        assertEquals(90f, r.maxSpeedKmh)
        assertEquals(0f, r.credibleMaxSpeedKmh)
        assertEquals(0, r.credibleDrivingFixes)
    }

    @Test
    fun should_leave_both_peaks_equal_when_every_fast_fix_was_sharp() {
        // The ordinary drive: nothing to reconcile, and the digest says so at a glance.
        val r = SessionRollup()
        r.fix(speedKmh = 62f, accuracyMeters = 6f)
        r.fix(speedKmh = 40f, accuracyMeters = 5f)

        assertEquals(r.maxSpeedKmh, r.credibleMaxSpeedKmh)
        assertTrue(r.summary("confirmed", 0L).contains("vmax 62km/h (cred 62)"))
    }

    @Test
    fun should_keep_the_last_positioned_fix_as_the_session_end() {
        val r = SessionRollup()
        r.fix(speedKmh = 10f, accuracyMeters = 5f, lat = 40.12345678, lon = -3.7)
        r.fix(speedKmh = 0f, accuracyMeters = 5f)   // no position: must not erase the last one

        assertTrue(
            r.summary("ended", 0L).contains("end 40.12346,-3.7"),
            "was: ${r.summary("ended", 0L)}",
        )
    }

    @Test
    fun should_report_steps_as_the_high_water_mark() {
        val r = SessionRollup()
        r.onStep(4)
        r.onStep(11)
        r.onStep(9)   // a counter that goes backwards never lowers the mark

        assertEquals(11, r.maxStepCount)
        assertTrue(r.summary("ended", 0L).contains("steps 11"))
    }

    @Test
    fun should_open_the_digest_with_the_outcome_and_the_duration() {
        val r = SessionRollup(startedAt = 1_000L)
        r.fix(speedKmh = 0f, accuracyMeters = 5f)

        val summary = r.summary(outcome = "confirmed", endedAtMs = 1_000L + 90_000L)
        assertTrue(summary.startsWith("confirmed · 1.5min"), "was: $summary")
    }
}
