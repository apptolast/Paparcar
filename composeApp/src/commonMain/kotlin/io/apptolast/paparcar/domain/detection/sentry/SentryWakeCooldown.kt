package io.apptolast.paparcar.domain.detection.sentry

import io.apptolast.paparcar.domain.detection.DetectionTrigger

import io.apptolast.paparcar.domain.detection.physics.SessionOutcome
import io.apptolast.paparcar.domain.detection.physics.isWithinFence
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking

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
 * These functions keep the damper's POLICY in commonMain (testable, replayable): the service folds
 * each ended session into an abort streak, and the streak maps to a re-arm quiet period the
 * [SignificantMotionMonitor] enforces.
 *
 * **[DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001] The premise this damper rests on, now checked.**
 * The original contract read "only the significant-motion NOMINATOR sleeps — the geofence EXIT, the
 * AR ENTER lane and the periodic safety net keep watching". That was an assumption, asserted in a
 * comment and verified nowhere, and on the night of 2026-08-21 it was false TWICE:
 *
 *  - Redmi 22:12:00, Covirán: the fence EXIT had already been delivered and consumed at 22:10:38
 *    and the phone was 389 m OUTSIDE its only fence — no fence left that could ever fire again. AR
 *    delivered `ON_BICYCLE`, never `IN_VEHICLE`. The 180 s quiet period covered the whole drive
 *    (~22:12:30 → 22:15:38) and the parking was lost outright.
 *  - Oppo 23:38:55, Covirán → home: the fence never delivered its EXIT at all, the 5-minute exact
 *    heartbeat had been dead since 18:28, and the 15-minute worker's grid swallowed the entire
 *    988 m drive (checks at 23:15:21 → 23:46:38). The car ended up pinned inside the user's house.
 *
 * So the two premises are now inputs, not comments:
 *
 *  1. **A storm is a CADENCE, not a tally** ([nextSentryWakeAbortStreak]'s `msSinceLastAbort`). The
 *     08-13 storm fired every ~18 s; the three aborts that blinded the Oppo were spread across 52
 *     minutes (22:46:56, 22:59:48, 23:38:15) — that is a person living their evening, not a sensor
 *     screaming. Aborts further apart than [ParkingDetectionConfig.sentryWakeStreakDecayMs] start a
 *     fresh streak instead of accumulating.
 *  2. **Silencing the LAST nominator is going blind** ([sentryWakeRearmCooldownMs]'s
 *     `hasFenceThatCanStillFire`). A geofence can only emit an EXIT while the phone is still INSIDE
 *     it ([isInsideAnyOwnedFence]); outside every owned fence, significant motion is all that is
 *     left and the damper stands down.
 *
 * Both incidents are covered — each by a different one of the two — and the storm the damper exists
 * for still damps: it fired every ~18 s from 36 m inside a 109 m fence, so it satisfies neither
 * escape hatch.
 *
 * **[DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001] What a quiet period MEANS changed — read this before
 * reasoning about the numbers below.** A quiet period no longer switches the significant-motion
 * sensor off. The sensor stays armed and a trigger inside the period buys a ONE-FIX triage
 * (`SentryWakeTriage.kt`) instead of a full session. So the value these functions return is no
 * longer "how long we stop looking"; it is "how long a look costs one fix instead of a session".
 *
 * Why the change: on 2026-08-22 the Redmi took three aborts in 114 s while walking INSIDE its fence
 * and drove at 75 km/h three minutes later. Neither escape hatch applied — correctly, by their own
 * terms — so the sensor slept exactly as designed, and only a `GEOFENCE_EXIT` saved the trip. The
 * escape hatches make the damper safe when it can PROVE another lane is watching; the cheap lane
 * makes it safe when it cannot.
 *
 * [sentryWakeRearmCooldownMs]'s fence gate survives the change and still means what it says: with
 * the phone outside every fence, even a triage is too little — that wake goes straight to a full
 * session.
 */

/**
 * Folds one ended detection session into the running sentry-wake abort streak.
 *
 * Only a sentry-wake arm refuted as a walking abort extends the streak; ANY other ended session —
 * a different trigger, a confirm, a prompt, a supersede — proves the world moved on and resets it.
 *
 * [11 bug #3] That membership used to be an equality test against two outcome STRINGS, so a new
 * label joined or left this set by how it was spelled. It is now declared per outcome
 * ([SessionOutcome.sentryStreakEffect]) and the `when` below reads as what it is: a membership
 * question and a cadence question, in that order.
 *
 * [DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001] "In a row" is a claim about TIME, and it is now
 * measured. A refuted wake whose predecessor is older than
 * [ParkingDetectionConfig.sentryWakeStreakDecayMs] is not the continuation of a storm — it is the
 * first abort of a new one, and counts as 1.
 *
 * @param msSinceLastAbort Elapsed since the streak's previous walking abort, or null when there is
 *   none to compare against (fresh process, or the streak was just reset). Null starts at 1 — with
 *   nothing to be "in a row" WITH, a single abort is a single abort. A negative elapsed (clock
 *   skew) is caller-mapped to null.
 */
fun nextSentryWakeAbortStreak(
    previousStreak: Int,
    armedBySentryWake: Boolean,
    sessionOutcome: SessionOutcome?,
    msSinceLastAbort: Long?,
    config: ParkingDetectionConfig,
): Int = when {
    // ── Declared membership: which endings are even eligible ──────────────────
    !armedBySentryWake -> 0
    sessionOutcome?.extendsSentryStreak != true -> 0
    // ── Measured cadence: is this the same storm as the last one? ─────────────
    msSinceLastAbort == null || msSinceLastAbort > config.sentryWakeStreakDecayMs -> 1
    else -> previousStreak + 1
}

/**
 * Is the phone still inside ANY of its own parked fences — i.e. is there a geofence left that could
 * still deliver an EXIT while the significant-motion sensor sleeps?
 * [DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001]
 *
 * A PREDICATE, not a verdict: it produces no `detectionPath`, no `outcome` and nothing the user
 * reads — it is the one input that makes the damper's safety argument true or false. So it lives
 * here with the rest of detection's pure policy rather than as an injected `Evaluate…UseCase`.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * Radius comes from [ParkingDetectionConfig.geofenceRadiusFor] — the SAME resolver
 * `ConfirmParkingUseCase` registers the fence with and the safety net measures against, so there is
 * no parallel notion of "inside". The fix's own accuracy pads the boundary: a fix vague enough to
 * straddle the ring must not be read as "definitely outside", because that would stand the damper
 * down on GPS noise alone.
 *
 * @param fix Where the body was when the session aborted, or null when the session produced no
 *   usable fix. **Null answers `false`** — unknown position means the premise cannot be shown to
 *   hold, and the asymmetry runs the other way here: a damper that fails open costs a handful of
 *   extra wake-ups, a damper that fails closed costs a parking spot.
 * @param parkedSessions The vehicle's currently active parked sessions. Empty → no fence exists,
 *   so none can fire.
 */
fun isInsideAnyOwnedFence(
    fix: GpsPoint?,
    parkedSessions: List<UserParking>,
    config: ParkingDetectionConfig,
): Boolean {
    val here = fix ?: return false
    return parkedSessions.any { session ->
        isWithinFence(
            fix = here,
            fenceCenter = session.location,
            fenceRadiusMeters = config.geofenceRadiusFor(session.sizeCategory, session.location.accuracy),
        )
    }
}

/**
 * Quiet period (ms) during which a significant-motion trigger buys a one-fix triage instead of a
 * full session ([DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001] — before that ticket, the period suppressed
 * the re-arm outright), given the current walking-abort streak. `0` below the threshold (a genuine departure's first wakes are
 * never delayed); from the threshold on, the base cooldown doubling per further refuted wake,
 * capped at [ParkingDetectionConfig.sentryWakeCooldownMaxMs].
 *
 * @param hasFenceThatCanStillFire Whether a parked fence remains that could deliver an EXIT while
 *   the sensor sleeps ([isInsideAnyOwnedFence]). **`false` returns 0 regardless of the streak**:
 *   the damper's entire licence to silence this nominator is that other lanes keep watching, and
 *   with the phone outside every fence the significant-motion sensor IS the last lane. Silencing it
 *   there is not damping a storm, it is choosing not to look.
 *   [DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001]
 */
fun sentryWakeRearmCooldownMs(
    abortStreak: Int,
    hasFenceThatCanStillFire: Boolean,
    config: ParkingDetectionConfig,
): Long {
    if (!hasFenceThatCanStillFire) return 0L
    if (abortStreak < config.sentryWakeAbortStreakForCooldown) return 0L
    var cooldownMs = config.sentryWakeCooldownBaseMs
    repeat(abortStreak - config.sentryWakeAbortStreakForCooldown) {
        cooldownMs = (cooldownMs * 2).coerceAtMost(config.sentryWakeCooldownMaxMs)
    }
    return cooldownMs.coerceAtMost(config.sentryWakeCooldownMaxMs)
}
