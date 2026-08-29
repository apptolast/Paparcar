package com.rndeveloper.paparcar.domain.detection.sentry

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit coverage for the cheap-wake triage. [DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001]
 *
 * The damper used to switch the significant-motion sensor OFF during a quiet period. This replaces
 * that with a one-fix triage, so the two properties under test are:
 *
 *  1. It is CHEAP — a cadence floor stops a walk from buying a fix every ~18 s.
 *  2. It is never BLIND — anything that cannot be a pedestrian near their car escalates, and so
 *     does a triage that could not see at all.
 *
 * The `── Field 2026-08-22 ──` section replays the Redmi sequence that made this ticket real.
 */
class SentryWakeTriageTest {

    private val config = ParkingDetectionConfig()

    /** El Puerto de Santa María — the parked car of the 2026-08-22 field case. */
    private val carLat = 36.59772
    private val carLon = -6.25055

    private fun parkedAt(
        lat: Double = carLat,
        lon: Double = carLon,
        accuracy: Float = 5f,
        size: VehicleSize? = VehicleSize.MEDIUM_SUV,
    ) = UserParking(
        id = "pin-$lat-$lon",
        location = GpsPoint(lat, lon, accuracy = accuracy, timestamp = 0L, speed = 0f),
        sizeCategory = size,
    )

    private fun fixAt(
        lat: Double = carLat,
        lon: Double = carLon,
        accuracy: Float = 5f,
        speedMps: Float = 0f,
    ) = GpsPoint(lat, lon, accuracy = accuracy, timestamp = 0L, speed = speedMps)

    // ── Cadence floor ────────────────────────────────────────────────────────

    @Test
    fun should_allow_the_triage_when_there_is_no_previous_one() {
        assertTrue(mayTriageSentryWake(msSinceLastTriage = null, config = config))
    }

    @Test
    fun should_drop_the_triage_when_the_previous_one_is_under_the_floor() {
        // A walk re-fires significant motion every ~18 s — the exact cadence the floor exists for.
        assertFalse(mayTriageSentryWake(msSinceLastTriage = 18_000L, config = config))
    }

    @Test
    fun should_allow_the_triage_once_the_floor_has_elapsed() {
        assertTrue(
            mayTriageSentryWake(
                msSinceLastTriage = config.cheapWakeMinTriageIntervalMs,
                config = config,
            ),
        )
    }

    @Test
    fun should_fit_several_triages_inside_one_quiet_period() {
        // One triage per quiet period would be a coin flip, not a watch. Guards the config invariant
        // from the outside, in the terms that matter rather than as a bare inequality.
        val triagesPerQuietPeriod = config.sentryWakeCooldownBaseMs / config.cheapWakeMinTriageIntervalMs
        assertTrue(triagesPerQuietPeriod >= 2, "a quiet period must contain at least 2 triages, was $triagesPerQuietPeriod")
    }

    // ── Verdict ──────────────────────────────────────────────────────────────

    @Test
    fun should_stay_quiet_when_the_fix_is_standing_next_to_the_parked_car() {
        val verdict = cheapWakeVerdict(fixAt(), listOf(parkedAt()), config)

        assertEquals(CheapWakeVerdict.STAY_QUIET, verdict)
    }

    @Test
    fun should_stay_quiet_when_walking_pace_is_read_inside_the_fence() {
        // 1.4 m/s ≈ 5 km/h, below the 10 km/h departure bar: a person, not a car.
        val verdict = cheapWakeVerdict(fixAt(speedMps = 1.4f), listOf(parkedAt()), config)

        assertEquals(CheapWakeVerdict.STAY_QUIET, verdict)
    }

    @Test
    fun should_escalate_when_the_fix_reads_credible_driving_speed() {
        // 21 m/s ≈ 75 km/h with a clean 8 m accuracy — the Redmi's drive of 2026-08-22.
        val verdict = cheapWakeVerdict(fixAt(speedMps = 21f, accuracy = 8f), listOf(parkedAt()), config)

        assertEquals(CheapWakeVerdict.ESCALATE, verdict)
    }

    @Test
    fun should_not_escalate_on_a_fast_reading_with_incredible_accuracy() {
        // The cold-start Doppler mirage the damper's own doc warns about: a big speed on a 200 m
        // fix. The escalation bar demands credible ACCURACY too, so reading a fix buys no lottery
        // ticket — it is still inside the fence, so it stays quiet.
        val verdict = cheapWakeVerdict(fixAt(speedMps = 21f, accuracy = 200f), listOf(parkedAt()), config)

        assertEquals(CheapWakeVerdict.STAY_QUIET, verdict)
    }

    @Test
    fun should_escalate_when_the_body_has_left_every_owned_fence() {
        // ~0.005° of latitude ≈ 550 m north of the car: outside any fence, at any speed. Once the
        // fence can no longer fire, the quiet period has outlived its own justification.
        val verdict = cheapWakeVerdict(fixAt(lat = carLat + 0.005), listOf(parkedAt()), config)

        assertEquals(CheapWakeVerdict.ESCALATE, verdict)
    }

    @Test
    fun should_escalate_when_the_triage_could_not_get_a_fix() {
        // Failing towards noise costs one session; failing towards silence costs a parking spot.
        val verdict = cheapWakeVerdict(fix = null, parkedSessions = listOf(parkedAt()), config = config)

        assertEquals(CheapWakeVerdict.ESCALATE, verdict)
    }

    @Test
    fun should_escalate_when_there_is_no_parked_session_left_to_be_near() {
        val verdict = cheapWakeVerdict(fixAt(), parkedSessions = emptyList(), config = config)

        assertEquals(CheapWakeVerdict.ESCALATE, verdict)
    }

    // ── Field 2026-08-22, Redmi ──────────────────────────────────────────────
    // Three sentry-wake aborts in 114 s (18:38:26, 18:39:39, 18:40:20) while walking inside the
    // fence, then a 75 km/h drive at 18:43:59. The old damper switched the sensor off for the whole
    // window; only GEOFENCE_EXIT saved the trip. The cheap lane must survive the same sequence.

    @Test
    fun should_replay_the_2026_08_22_redmi_still_aborts_without_paying_for_a_session() {
        val car = listOf(parkedAt())
        // Two of the three aborts (18:39:39 and 18:40:20) summarised as `vmax 0km/h`: standing or
        // strolling beside the car. These are the ones the cheap lane must refuse to pay for.
        val stillFixes = listOf(
            fixAt(speedMps = 0f),
            fixAt(lat = carLat + 0.00008, speedMps = 0f),
        )

        stillFixes.forEach { fix ->
            assertEquals(
                CheapWakeVerdict.STAY_QUIET,
                cheapWakeVerdict(fix, car, config),
                "a walk beside the car must not buy a session",
            )
        }
    }

    @Test
    fun should_escalate_the_2026_08_22_abort_that_actually_read_departure_speed() {
        // 18:38:26 summarised as `vmax 14km/h` — above the 10 km/h departure bar. The triage is a
        // FILTER, not a judge: it escalates, the real session runs, and the coordinator refutes it
        // as `aborted_false_enter` exactly as it did in the field. The cheap lane saves the obvious
        // cases and forwards the doubtful ones to the evaluator that is allowed to decide, which is
        // the asymmetry the project is built on.
        val verdict = cheapWakeVerdict(fixAt(speedMps = 3.9f), listOf(parkedAt()), config)

        assertEquals(CheapWakeVerdict.ESCALATE, verdict)
    }

    @Test
    fun should_replay_the_2026_08_22_redmi_drive_and_escalate_without_the_fence() {
        val car = listOf(parkedAt())
        // 18:43:59 — the drive the sentry was blind to. No GEOFENCE_EXIT in this replay ON PURPOSE:
        // that is the thread the system was hanging by, and the point of the ticket is not to need it.
        val driving = fixAt(speedMps = 20.8f, accuracy = 25f)

        assertEquals(CheapWakeVerdict.ESCALATE, cheapWakeVerdict(driving, car, config))
    }
}
