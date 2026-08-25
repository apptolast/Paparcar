package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.detection.physics.SessionOutcome
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit coverage for the sentry-wake storm damper's pure policy. [DET-SENTRY-COOLDOWN-001]
 *
 * Field 2026-08-13 (Calle Góndola): a walk near the parked car re-fired the significant-motion
 * trigger every ~18 s for over an hour — ≈130 armed-and-refuted sessions. The reducer counts the
 * refuted nominations; the mapping turns the streak into an escalating re-arm quiet period.
 *
 * [DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001] Field 2026-08-21 showed the damper silencing the last
 * nominator standing, twice, and losing both drives. The `── Field 2026-08-21 ──` section at the
 * bottom replays all four real cooldown decisions of that night (two that must keep damping, two
 * that must not) with their measured distances and gaps.
 */
class SentryWakeCooldownTest {

    private val config = ParkingDetectionConfig()

    /** A fence exists and the phone sits on top of it — the damper's precondition is satisfied,
     *  so streak-only tests are not silently short-circuited by the fence gate. */
    private val fenceCanFire = true

    private fun parkedAt(
        lat: Double,
        lon: Double,
        accuracy: Float = 5f,
        size: VehicleSize? = VehicleSize.MEDIUM_SUV,
    ) = UserParking(
        id = "pin-$lat-$lon",
        location = GpsPoint(lat, lon, accuracy = accuracy, timestamp = 0L, speed = 0f),
        sizeCategory = size,
    )

    private fun fixAt(lat: Double, lon: Double, accuracy: Float = 5f) =
        GpsPoint(lat, lon, accuracy = accuracy, timestamp = 0L, speed = 0f)

    // ── Streak reducer ──────────────────────────────────────────────────────────

    @Test
    fun should_extend_streak_when_sentry_wake_aborts_as_false_enter() {
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 2,
            armedBySentryWake = true,
            sessionOutcome = SessionOutcome.AbortedFalseEnter,
            msSinceLastAbort = 20_000L,
            config = config,
        )
        assertEquals(3, streak)
    }

    @Test
    fun should_extend_streak_when_sentry_wake_aborts_as_no_movement() {
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 0,
            armedBySentryWake = true,
            sessionOutcome = SessionOutcome.AbortedNoMovement,
            msSinceLastAbort = 20_000L,
            config = config,
        )
        assertEquals(1, streak)
    }

    @Test
    fun should_reset_streak_when_sentry_wake_session_confirms() {
        // A confirm proves the wake was a REAL departure — the storm is over.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 5,
            armedBySentryWake = true,
            sessionOutcome = SessionOutcome.Confirmed("steps+egress"),
            msSinceLastAbort = 20_000L,
            config = config,
        )
        assertEquals(0, streak)
    }

    @Test
    fun should_reset_streak_when_session_armed_by_another_trigger() {
        // A geofence-EXIT / AR arm ending in ANY outcome (even a walking abort) proves the world
        // moved past the wake storm — the damper must not outlive its cause.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 5,
            armedBySentryWake = false,
            sessionOutcome = SessionOutcome.AbortedFalseEnter,
            msSinceLastAbort = 20_000L,
            config = config,
        )
        assertEquals(0, streak)
    }

    @Test
    fun should_reset_streak_when_outcome_is_unknown() {
        // No rollup (null outcome — e.g. a supersede) is not a refuted nomination: never punish
        // the trigger for a session whose verdict was never rendered.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 5,
            armedBySentryWake = true,
            sessionOutcome = null,
            msSinceLastAbort = 20_000L,
            config = config,
        )
        assertEquals(0, streak)
    }

    @Test
    fun should_reset_streak_when_the_user_stopped_the_session() {
        // [DET-STOP-BUTTON-001] A user stop is not a refuted nomination: the wake may well have
        // been a real departure the user simply did not want followed. Its own quiet period already
        // silences the sensor, so the walking damper must not also escalate off the back of it.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 5,
            armedBySentryWake = true,
            sessionOutcome = SessionOutcome.StoppedByUser,
            msSinceLastAbort = 20_000L,
            config = config,
        )
        assertEquals(0, streak)
    }

    // ── Streak decay: a storm is a cadence [DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001] ──

    @Test
    fun should_restart_streak_when_the_previous_abort_is_older_than_the_decay_window() {
        // Three errands an hour apart are three errands, not a storm.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 2,
            armedBySentryWake = true,
            sessionOutcome = SessionOutcome.AbortedFalseEnter,
            msSinceLastAbort = config.sentryWakeStreakDecayMs + 1,
            config = config,
        )
        assertEquals(1, streak)
    }

    @Test
    fun should_keep_extending_streak_exactly_at_the_decay_boundary() {
        // The window is inclusive: only strictly-older aborts decay, so the boundary never flips
        // the damper on a millisecond of scheduler jitter.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 2,
            armedBySentryWake = true,
            sessionOutcome = SessionOutcome.AbortedFalseEnter,
            msSinceLastAbort = config.sentryWakeStreakDecayMs,
            config = config,
        )
        assertEquals(3, streak)
    }

    @Test
    fun should_start_streak_at_one_when_there_is_no_previous_abort_to_be_in_a_row_with() {
        // Null predecessor (fresh process, or the streak was just reset). With nothing to be
        // consecutive WITH, a single abort is a single abort — never an inherited tally.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 7,
            armedBySentryWake = true,
            sessionOutcome = SessionOutcome.AbortedFalseEnter,
            msSinceLastAbort = null,
            config = config,
        )
        assertEquals(1, streak)
    }

    // ── Fence gate: never silence the last watcher [DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001] ──

    @Test
    fun should_report_inside_when_the_fix_sits_on_the_parked_pin() {
        assertTrue(
            isInsideAnyOwnedFence(
                fix = fixAt(36.6087317, -6.2782342),
                parkedSessions = listOf(parkedAt(36.6087317, -6.2782342)),
                config = config,
            )
        )
    }

    @Test
    fun should_report_outside_when_the_fix_is_far_beyond_every_fence() {
        // ~389 m from the pin with a fence radius around 100 m — no EXIT can ever fire from here.
        assertFalse(
            isInsideAnyOwnedFence(
                fix = fixAt(36.6116488, -6.2808343),
                parkedSessions = listOf(parkedAt(36.6087317, -6.2782342)),
                config = config,
            )
        )
    }

    @Test
    fun should_report_inside_when_any_one_of_several_fences_still_contains_the_fix() {
        // Two cars parked; standing at the second one still leaves a fence that can fire.
        assertTrue(
            isInsideAnyOwnedFence(
                fix = fixAt(36.6087317, -6.2782342),
                parkedSessions = listOf(
                    parkedAt(36.6143783, -6.2863817),
                    parkedAt(36.6087317, -6.2782342),
                ),
                config = config,
            )
        )
    }

    @Test
    fun should_report_outside_when_no_session_is_parked_at_all() {
        assertFalse(isInsideAnyOwnedFence(fix = fixAt(36.6087317, -6.2782342), parkedSessions = emptyList(), config = config))
    }

    @Test
    fun should_report_outside_when_the_aborting_session_produced_no_fix() {
        // Unknown position cannot demonstrate the damper's premise. Fail open: a few extra wake-ups
        // beat a silenced sensor, because the cost of the latter is a lost parking spot.
        assertFalse(
            isInsideAnyOwnedFence(
                fix = null,
                parkedSessions = listOf(parkedAt(36.6087317, -6.2782342)),
                config = config,
            )
        )
    }

    @Test
    fun should_pad_the_fence_boundary_with_the_fix_own_accuracy() {
        // A fix vague enough to straddle the ring must not read as "definitely outside" — that
        // would stand the damper down on GPS noise alone. 150 m out with 150 m of accuracy is
        // inside the padded boundary of a ~100 m fence.
        assertTrue(
            isInsideAnyOwnedFence(
                fix = fixAt(36.6100800, -6.2782342, accuracy = 150f),
                parkedSessions = listOf(parkedAt(36.6087317, -6.2782342)),
                config = config,
            )
        )
    }

    // ── Cooldown mapping ────────────────────────────────────────────────────────

    @Test
    fun should_not_cooldown_below_the_streak_threshold() {
        // A genuine departure's first wakes are never delayed.
        for (streak in 0 until config.sentryWakeAbortStreakForCooldown) {
            assertEquals(0L, sentryWakeRearmCooldownMs(streak, fenceCanFire, config), "streak=$streak")
        }
    }

    @Test
    fun should_apply_base_cooldown_at_the_threshold() {
        val cooldown = sentryWakeRearmCooldownMs(config.sentryWakeAbortStreakForCooldown, fenceCanFire, config)
        assertEquals(config.sentryWakeCooldownBaseMs, cooldown)
    }

    @Test
    fun should_double_per_further_refuted_wake() {
        val threshold = config.sentryWakeAbortStreakForCooldown
        assertEquals(config.sentryWakeCooldownBaseMs * 2, sentryWakeRearmCooldownMs(threshold + 1, fenceCanFire, config))
        assertEquals(config.sentryWakeCooldownBaseMs * 4, sentryWakeRearmCooldownMs(threshold + 2, fenceCanFire, config))
    }

    @Test
    fun should_cap_the_escalation_at_the_configured_ceiling() {
        // Far beyond the threshold the quiet period pins to the cap (and must not overflow).
        val cooldown = sentryWakeRearmCooldownMs(config.sentryWakeAbortStreakForCooldown + 50, fenceCanFire, config)
        assertEquals(config.sentryWakeCooldownMaxMs, cooldown)
    }

    @Test
    fun should_never_cooldown_when_no_fence_can_still_fire_however_long_the_streak() {
        // The damper's whole licence to silence this nominator is that other lanes keep watching.
        // Outside every fence there are no other lanes, so no streak buys silence.
        for (streak in listOf(3, 4, 10, config.sentryWakeAbortStreakForCooldown + 50)) {
            assertEquals(
                0L,
                sentryWakeRearmCooldownMs(streak, hasFenceThatCanStillFire = false, config = config),
                "streak=$streak",
            )
        }
    }

    // ── Field 2026-08-21 · the four real decisions of that night ────────────────
    // [DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001] Two must keep damping, two must not. Distances and
    // gaps are the measured ones from parkdiag.

    @Test
    fun should_still_damp_the_2026_08_13_walk_storm() {
        // ≈130 armed-and-refuted sessions, one every ~18 s, from 36 m inside a fence. The reason
        // the damper exists: it must survive both new gates untouched.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 2,
            armedBySentryWake = true,
            sessionOutcome = SessionOutcome.AbortedFalseEnter,
            msSinceLastAbort = 18_000L,
            config = config,
        )
        assertEquals(3, streak)
        val insideFence = isInsideAnyOwnedFence(
            // 36 m north of the pin, well inside the fence.
            fix = fixAt(36.6090553, -6.2782342),
            parkedSessions = listOf(parkedAt(36.6087317, -6.2782342)),
            config = config,
        )
        assertTrue(insideFence)
        assertEquals(config.sentryWakeCooldownBaseMs, sentryWakeRearmCooldownMs(streak, insideFence, config))
    }

    @Test
    fun should_still_damp_the_oppo_2206_walk_beside_its_own_car() {
        // 22:06:37, Oppo: aborts ~30 s apart, 6 m from the pin. Its fence DID catch the real
        // departure three minutes later — exactly the case the damper is safe for.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 2,
            armedBySentryWake = true,
            sessionOutcome = SessionOutcome.AbortedFalseEnter,
            msSinceLastAbort = 30_000L,
            config = config,
        )
        val insideFence = isInsideAnyOwnedFence(
            fix = fixAt(36.6084239, -6.2781443),
            parkedSessions = listOf(parkedAt(36.608368, -6.2781358)),
            config = config,
        )
        assertTrue(insideFence)
        assertEquals(config.sentryWakeCooldownBaseMs, sentryWakeRearmCooldownMs(streak, insideFence, config))
    }

    @Test
    fun should_not_blind_the_oppo_when_its_three_aborts_span_fifty_two_minutes() {
        // 23:38:55, Oppo. Previous abort 22:59:48 — 39 minutes earlier. Today that read as
        // "streak 3" and the 180 s quiet period covered the 988 m drive home; the pin ended up
        // inside the user's house. The cadence gate restarts the streak instead.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 2,
            armedBySentryWake = true,
            sessionOutcome = SessionOutcome.AbortedFalseEnter,
            msSinceLastAbort = 39 * 60_000L,
            config = config,
        )
        assertEquals(1, streak)
        // Still standing beside the car (11 m) — the fence gate does NOT save this one; the decay does.
        val insideFence = isInsideAnyOwnedFence(
            fix = fixAt(36.6143112, -6.2864856),
            parkedSessions = listOf(parkedAt(36.6143783, -6.2863817)),
            config = config,
        )
        assertTrue(insideFence)
        assertEquals(0L, sentryWakeRearmCooldownMs(streak, insideFence, config))
    }

    @Test
    fun should_not_blind_the_redmi_when_it_is_already_outside_its_only_fence() {
        // 22:12:00, Redmi. Aborts ~25 s apart, so the cadence gate does NOT save this one — the
        // streak legitimately reaches 3. But the phone was 389 m outside its only fence and that
        // fence's EXIT had already been delivered and consumed at 22:10:38: nothing else was
        // watching. Today the 180 s quiet period swallowed the whole drive to Covirán.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 2,
            armedBySentryWake = true,
            sessionOutcome = SessionOutcome.AbortedFalseEnter,
            msSinceLastAbort = 25_000L,
            config = config,
        )
        assertEquals(3, streak)
        val insideFence = isInsideAnyOwnedFence(
            fix = fixAt(36.6116464, -6.2806562, accuracy = 30.04f),
            parkedSessions = listOf(parkedAt(36.6087317, -6.2782342, accuracy = 6.388f, size = VehicleSize.LARGE_SEDAN)),
            config = config,
        )
        assertFalse(insideFence)
        assertEquals(0L, sentryWakeRearmCooldownMs(streak, insideFence, config))
    }
}
