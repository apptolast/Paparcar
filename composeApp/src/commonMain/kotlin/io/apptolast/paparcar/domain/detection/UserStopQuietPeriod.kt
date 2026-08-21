package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig

/**
 * [DET-STOP-BUTTON-001] Pure policy for the quiet period the user opens by tapping "Stop
 * detection" on a live session.
 *
 * The user is the highest authority the system has: when they say "this trip is not mine", no
 * amount of measured evidence may plant a pin. But cancelling the session alone makes the button
 * a lie — the same walk to the passenger seat that armed it re-fires the AR ENTER (or the
 * significant-motion sensor, or a fence exit still ahead) seconds later, and detection "comes back
 * on its own". So a deliberate stop also SILENCES the automatic nominators for a bounded period.
 *
 * Scope, deliberately narrow (see the consumer sweep in `docs/backlog/det-stop-button-001.md`):
 *  - Only ARMING sleeps. A geofence EXIT delivered during the quiet period still releases the spot;
 *    the safety net still reconciles parked cars; the Bluetooth lane is untouched (separate rails).
 *  - [DetectionTrigger.MANUAL] is never suppressed — "I'm driving" is the same user retracting the
 *    stop, and the service clears the stamp when it fires. It is the ONLY exemption:
 *    [DetectionTrigger.ARRIVAL_HANDOFF] is an automatic nominator like any other and sleeps with
 *    them. Until [DET-HANDOFF-NOT-MANUAL-001] the safety net's handoff *borrowed* MANUAL, so it
 *    walked straight through this quiet period — the button was a lie for that lane too.
 *
 * Sibling of `SentryWakeCooldown.kt`: another quiet period, another cause, same shape — pure
 * policy in commonMain, enforced at the service's single arming gate. Neither steps on the other.
 */

/**
 * True when [trigger] must NOT arm a detection session because the user stopped detection at
 * [userStoppedAtMs] and the quiet period has not lapsed yet.
 *
 * @param userStoppedAtMs epoch-ms of the last user stop, or null when there is none on record.
 */
fun isArmSuppressedByUserStop(
    trigger: DetectionTrigger,
    userStoppedAtMs: Long?,
    nowMs: Long,
    config: ParkingDetectionConfig,
): Boolean {
    if (trigger == DetectionTrigger.MANUAL) return false
    return userStopQuietPeriodRemainingMs(userStoppedAtMs, nowMs, config) > 0L
}

/**
 * Milliseconds left of the user's quiet period, `0` when there is none (no stop on record, already
 * lapsed, or a stamp from the future — a clock jump backwards must never mute detection forever).
 */
fun userStopQuietPeriodRemainingMs(
    userStoppedAtMs: Long?,
    nowMs: Long,
    config: ParkingDetectionConfig,
): Long {
    val stoppedAt = userStoppedAtMs ?: return 0L
    val elapsed = nowMs - stoppedAt
    if (elapsed < 0L) return 0L
    return (config.userStopQuietPeriodMs - elapsed).coerceAtLeast(0L)
}
