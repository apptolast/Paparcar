package io.apptolast.paparcar.domain.detection.sentry

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig

/**
 * [DET-HEARTBEAT-MISS-IS-EVIDENCE-001] Is the exact-alarm fast lane still catching anything?
 *
 * **Why this has to exist.** The heartbeat is one-shot by construction: each tick re-arms the next
 * from inside its own receiver, and the 15-minute safety-net worker re-arms it too, unconditionally,
 * on every pass. That self-healing hides its own failure — a lane that never fires again is
 * indistinguishable, from inside the app, from one firing perfectly. `firedDelayMs` existed to
 * measure the Doze stretch, but it is read INSIDE the receiver that never runs, so by construction
 * the miss could not be observed.
 *
 * Field 2026-08-21/22, Oppo CPH2371 (ColorOS, Android 13): the last `exact heartbeat fired` line of
 * the day is 18:28. From 22:41 onward the periodic worker ran on the dot every ~15 min for three
 * hours and re-armed the alarm each time; the alarm never came back once. The device says the
 * alarms WERE delivered (`dumpsys alarm`: 82 wakeups, in-flight records piling up for the receiver)
 * while the process was alive with a running foreground service, the app sat in standby bucket
 * EXEMPTED, and `SCHEDULE_EXACT_ALARM` was granted. Nothing on our side was misconfigured — the
 * broadcast simply never landed.
 *
 * That is the OS, and the project's contract accepts the OS as an excuse on ONE condition: *it must
 * be detectable a posteriori*. It was not. On that Oppo the safety net had silently degraded to the
 * 15-minute grid, which is exactly the hole a 7 min 43 s drive fell through that night — and
 * diagnosing it took a cable and `dumpsys`, because Firestore said nothing.
 *
 * So the app now measures its own lane. This changes no decision: nothing arms, confirms or
 * releases differently. It only makes a dead lane say so.
 */

/**
 * Folds one arm-time observation into the running count of consecutive lost ticks.
 *
 * A tick is LOST when its scheduled moment has passed by more than [ParkingDetectionConfig.exactHeartbeatMissGraceMs]
 * and the receiver never came back to re-arm — because a receiver that DID run would have pushed
 * `scheduledAtMs` forward to its own now-plus-interval before this is ever read again.
 *
 * Deliberately a STREAK, and that is where the discrimination lives. One glance at a stale schedule
 * cannot tell "lost" from "stretched by Doze and still pending" — `setExactAndAllowWhileIdle`
 * legally slips to ~9-15 min — and no grace value can separate them, because both read identically.
 * What separates them is TIME: a stretched tick eventually lands and clears the streak, a lost one
 * never does. So the grace stays short and the streak carries the verdict.
 *
 * @param scheduledAtMs When the last arm asked to fire, or null when nothing was armed (first arm
 *   after a park, or the lane was disarmed). Null resets — there is no tick to have missed.
 */
fun nextExactHeartbeatMissStreak(
    previousStreak: Int,
    scheduledAtMs: Long?,
    nowMs: Long,
    config: ParkingDetectionConfig,
): Int {
    val scheduled = scheduledAtMs ?: return 0
    val overdueMs = nowMs - scheduled
    return if (overdueMs > config.exactHeartbeatMissGraceMs) previousStreak + 1 else 0
}

/**
 * Has the fast lane stopped working on this device?
 *
 * `true` means the safety net has degraded to the 15-minute periodic grid alone — the honest health
 * fact to stamp into a session header next to `requiresOemBatteryFreeze`, so a lost trip can be
 * attributed from Firestore instead of from a cable.
 */
fun isExactHeartbeatLaneDead(missStreak: Int, config: ParkingDetectionConfig): Boolean =
    missStreak >= config.exactHeartbeatDeadAfterMisses
