package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig

/**
 * [DET-SENTRY-COOLDOWN-001] Pure decisions for the sentry-wake storm damper.
 *
 * The significant-motion sensor is the weakest nominator ([DetectionTrigger.SIGNIFICANT_MOTION]):
 * it cannot tell a walk from a drive, and while the user walks near the parked car it re-fires on
 * every re-arm. Field 2026-08-13 (Calle Góndola): one armed-and-refuted session every ~18 s for
 * over an hour — ≈130 `aborted_false_enter` sessions during a single walk. Beyond battery and
 * telemetry cost, every wake is a fresh chance for a cold-start GPS mirage to fake "measured
 * driving" (the same walk produced the 2026-08-13 false pin).
 *
 * These two functions keep the damper's POLICY in commonMain (testable, replayable): the service
 * folds each ended session into an abort streak, and the streak maps to a re-arm quiet period the
 * [SignificantMotionMonitor] enforces. Contract preserved — only the significant-motion NOMINATOR
 * sleeps during a cooldown; the geofence EXIT, the AR ENTER lane and the periodic safety net keep
 * watching, and their sync mirrors re-arm the sensor as soon as the quiet period lapses.
 */

/** Session outcome labels shared by the coordinator (producer), the service teardown and this
 *  damper (consumers) — the two SILENT walking aborts. [DET-SENTRY-COOLDOWN-001] */
object DetectionSessionOutcomes {
    const val ABORTED_FALSE_ENTER = "aborted_false_enter"
    const val ABORTED_NO_MOVEMENT = "aborted_no_movement"

    /** [DET-STOP-BUTTON-001] The user tapped "Stop detection" on a live session. Deliberately NOT
     *  one of the two walking aborts above: it is not a refuted nomination but the highest
     *  authority in the system speaking, so it resets the sentry-wake streak like any other
     *  non-abort ending. See `UserStopQuietPeriod.kt`. */
    const val STOPPED_BY_USER = "stopped_by_user"
}

/**
 * Folds one ended detection session into the running sentry-wake abort streak.
 *
 * Only a sentry-wake arm refuted as a walking abort extends the streak; ANY other ended session —
 * a different trigger, a confirm, a prompt, a supersede — proves the world moved on and resets it.
 */
fun nextSentryWakeAbortStreak(
    previousStreak: Int,
    armedBySentryWake: Boolean,
    sessionOutcome: String?,
): Int = when {
    !armedBySentryWake -> 0
    sessionOutcome == DetectionSessionOutcomes.ABORTED_FALSE_ENTER ||
        sessionOutcome == DetectionSessionOutcomes.ABORTED_NO_MOVEMENT -> previousStreak + 1
    else -> 0
}

/**
 * Quiet period (ms) the significant-motion trigger must respect before re-arming, given the
 * current walking-abort streak. `0` below the threshold (a genuine departure's first wakes are
 * never delayed); from the threshold on, the base cooldown doubling per further refuted wake,
 * capped at [ParkingDetectionConfig.sentryWakeCooldownMaxMs].
 */
fun sentryWakeRearmCooldownMs(abortStreak: Int, config: ParkingDetectionConfig): Long {
    if (abortStreak < config.sentryWakeAbortStreakForCooldown) return 0L
    var cooldownMs = config.sentryWakeCooldownBaseMs
    repeat(abortStreak - config.sentryWakeAbortStreakForCooldown) {
        cooldownMs = (cooldownMs * 2).coerceAtMost(config.sentryWakeCooldownMaxMs)
    }
    return cooldownMs.coerceAtMost(config.sentryWakeCooldownMaxMs)
}
