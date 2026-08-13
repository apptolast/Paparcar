package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for the sentry-wake storm damper's pure policy. [DET-SENTRY-COOLDOWN-001]
 *
 * Field 2026-08-13 (Calle Góndola): a walk near the parked car re-fired the significant-motion
 * trigger every ~18 s for over an hour — ≈130 armed-and-refuted sessions. The reducer counts the
 * refuted nominations; the mapping turns the streak into an escalating re-arm quiet period.
 */
class SentryWakeCooldownTest {

    private val config = ParkingDetectionConfig()

    // ── Streak reducer ──────────────────────────────────────────────────────────

    @Test
    fun should_extend_streak_when_sentry_wake_aborts_as_false_enter() {
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 2,
            armedBySentryWake = true,
            sessionOutcome = DetectionSessionOutcomes.ABORTED_FALSE_ENTER,
        )
        assertEquals(3, streak)
    }

    @Test
    fun should_extend_streak_when_sentry_wake_aborts_as_no_movement() {
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 0,
            armedBySentryWake = true,
            sessionOutcome = DetectionSessionOutcomes.ABORTED_NO_MOVEMENT,
        )
        assertEquals(1, streak)
    }

    @Test
    fun should_reset_streak_when_sentry_wake_session_confirms() {
        // A confirm proves the wake was a REAL departure — the storm is over.
        val streak = nextSentryWakeAbortStreak(
            previousStreak = 5,
            armedBySentryWake = true,
            sessionOutcome = "confirmed_steps+egress",
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
            sessionOutcome = DetectionSessionOutcomes.ABORTED_FALSE_ENTER,
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
        )
        assertEquals(0, streak)
    }

    // ── Cooldown mapping ────────────────────────────────────────────────────────

    @Test
    fun should_not_cooldown_below_the_streak_threshold() {
        // A genuine departure's first wakes are never delayed.
        for (streak in 0 until config.sentryWakeAbortStreakForCooldown) {
            assertEquals(0L, sentryWakeRearmCooldownMs(streak, config), "streak=$streak")
        }
    }

    @Test
    fun should_apply_base_cooldown_at_the_threshold() {
        val cooldown = sentryWakeRearmCooldownMs(config.sentryWakeAbortStreakForCooldown, config)
        assertEquals(config.sentryWakeCooldownBaseMs, cooldown)
    }

    @Test
    fun should_double_per_further_refuted_wake() {
        val threshold = config.sentryWakeAbortStreakForCooldown
        assertEquals(config.sentryWakeCooldownBaseMs * 2, sentryWakeRearmCooldownMs(threshold + 1, config))
        assertEquals(config.sentryWakeCooldownBaseMs * 4, sentryWakeRearmCooldownMs(threshold + 2, config))
    }

    @Test
    fun should_cap_the_escalation_at_the_configured_ceiling() {
        // Far beyond the threshold the quiet period pins to the cap (and must not overflow).
        val cooldown = sentryWakeRearmCooldownMs(config.sentryWakeAbortStreakForCooldown + 50, config)
        assertEquals(config.sentryWakeCooldownMaxMs, cooldown)
    }
}
