package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-HEARTBEAT-MISS-IS-EVIDENCE-001] The exact-alarm lane measuring its own silence.
 *
 * Field 2026-08-21/22, Oppo CPH2371: the 15-min periodic worker ran on the dot for three hours,
 * re-arming the heartbeat every pass, and not one tick came back. Nothing in the app could tell —
 * the punctuality metric lived inside the receiver that never ran — so a safety net silently
 * degraded to a 15-minute grid, which is the size of the hole that night's drive fell through.
 */
class ExactHeartbeatHealthTest {

    private val config = ParkingDetectionConfig()

    private val overdue = config.exactHeartbeatMissGraceMs + 1
    private val notOverdue = config.exactHeartbeatMissGraceMs

    // ── One observation ─────────────────────────────────────────────────────────

    @Test
    fun should_count_a_tick_as_lost_when_its_moment_passed_and_nothing_re_armed_it() {
        // A receiver that RAN would have pushed the schedule forward before this is read again, so
        // an arm still sitting in the past is one the lane never delivered.
        val streak = nextExactHeartbeatMissStreak(
            previousStreak = 0,
            scheduledAtMs = 1_000_000L,
            nowMs = 1_000_000L + overdue,
            config = config,
        )
        assertEquals(1, streak)
    }

    @Test
    fun should_not_count_a_tick_caught_mid_flight_inside_the_grace() {
        val streak = nextExactHeartbeatMissStreak(
            previousStreak = 2,
            scheduledAtMs = 1_000_000L,
            nowMs = 1_000_000L + notOverdue,
            config = config,
        )
        assertEquals(0, streak)
    }

    @Test
    fun should_keep_the_grace_short_enough_to_see_the_periodic_cadence() {
        // The regression this test exists for: a grace wider than the periodic's own look-in makes
        // the metric blind. The 2026-08 Oppo's arms were exactly 10 min stale at every pass, so a
        // grace of, say, 20 min would have reported a healthy lane through three hours of silence.
        // The grace must stay under that gap; the STREAK is what separates late from lost.
        val periodicIntervalMs = 15 * 60_000L
        val heartbeatIntervalMs = 5 * 60_000L
        assertTrue(
            config.exactHeartbeatMissGraceMs < periodicIntervalMs - heartbeatIntervalMs,
            "a grace of ${config.exactHeartbeatMissGraceMs} ms cannot see a lane that never delivers",
        )
    }

    @Test
    fun should_not_count_a_tick_scheduled_in_the_future() {
        val streak = nextExactHeartbeatMissStreak(
            previousStreak = 5,
            scheduledAtMs = 2_000_000L,
            nowMs = 1_000_000L,
            config = config,
        )
        assertEquals(0, streak)
    }

    @Test
    fun should_reset_when_nothing_was_armed_to_be_missed() {
        // First arm after a park, or the lane was disarmed with the session. No tick, no verdict.
        val streak = nextExactHeartbeatMissStreak(
            previousStreak = 4,
            scheduledAtMs = null,
            nowMs = 1_000_000L,
            config = config,
        )
        assertEquals(0, streak)
    }

    // ── The verdict ─────────────────────────────────────────────────────────────

    @Test
    fun should_not_call_the_lane_dead_below_the_threshold() {
        for (streak in 0 until config.exactHeartbeatDeadAfterMisses) {
            assertFalse(isExactHeartbeatLaneDead(streak, config), "streak=$streak")
        }
    }

    @Test
    fun should_call_the_lane_dead_at_the_threshold_and_beyond() {
        assertTrue(isExactHeartbeatLaneDead(config.exactHeartbeatDeadAfterMisses, config))
        assertTrue(isExactHeartbeatLaneDead(config.exactHeartbeatDeadAfterMisses + 10, config))
    }

    // ── Field 2026-08-21/22 ─────────────────────────────────────────────────────

    @Test
    fun should_call_the_oppo_lane_dead_after_three_hours_of_periodic_ticks_with_no_heartbeat() {
        // Replay of the real cadence: the periodic worker woke every ~15 min (00:01:39, 00:16:39,
        // 00:31:39, 00:46:39, 01:01:40, 01:16:41, 01:31:41) and each pass re-armed the heartbeat to
        // now + 5 min. Every one of those arms expired untouched.
        val periodicIntervalMs = 15 * 60_000L
        val heartbeatIntervalMs = 5 * 60_000L
        var now = 0L
        var scheduledAt: Long? = null
        var streak = 0
        repeat(7) {
            streak = nextExactHeartbeatMissStreak(streak, scheduledAt, now, config)
            scheduledAt = now + heartbeatIntervalMs
            now += periodicIntervalMs
        }
        // First pass had nothing armed yet; the six after it each lost their tick.
        assertEquals(6, streak)
        assertTrue(isExactHeartbeatLaneDead(streak, config), "three hours of silence is a dead lane")
    }

    @Test
    fun should_stay_quiet_on_a_device_whose_ticks_come_back() {
        // The Redmi beside it fired 34 heartbeats that day. Each delivery re-arms from inside the
        // receiver, so by the time the periodic looks, the schedule is fresh — never overdue.
        var streak = 0
        var now = 0L
        repeat(10) {
            val scheduledAt = now + 5 * 60_000L
            now += 5 * 60_000L // the tick arrives on time and re-arms
            streak = nextExactHeartbeatMissStreak(streak, scheduledAt, now, config)
        }
        assertEquals(0, streak)
        assertFalse(isExactHeartbeatLaneDead(streak, config))
    }
}
