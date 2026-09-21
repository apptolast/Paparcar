package com.rndeveloper.paparcar.domain.detection

/**
 * [DET-A-STILL-PARKED-ASK-MUST-EARN-ITS-INTERRUPTION-001] What the safety net can ask a human, and
 * when it has earned the right to ask it again.
 *
 * Two different doubts reach the user from the parked-session net, and they had collapsed into one
 * notification:
 *
 *  - [SafetyNetAskKind.StillParked] — a doubt about the **departure**: the phone is far from a
 *    parking we still hold and nothing here can tell "you were driven away" from "you walked".
 *    Nominated by the evaluator as `SafetyNetAction.PromptStillParked`.
 *  - [SafetyNetAskKind.WhereDidYouPark] — a doubt about the **arrival**: the departure is already
 *    dispatched, nobody can take the new park (live detection denied, no bounded backfill), so the
 *    only remaining answer is the user's pin. This one used to be raised with the *departure*
 *    notification — «¿Sigues aparcado aquí? Parece que estás lejos» — at the exact moment the user
 *    is standing next to the car they have just parked, which is both the wrong question and the
 *    reason the ask looked like it fired at random.
 *
 * **One budget per fence, shared by both.** What runs out is not "how many times we asked about this
 * departure", it is **how many times we interrupted about this car**. Keeping the two questions on
 * separate clocks would just rebuild the defect with an extra step.
 */
enum class SafetyNetAskKind {
    /** "Are you still parked here?" — the departure is in doubt. */
    StillParked,

    /** "Mark where you parked" — the departure is settled, the arrival is not. */
    WhereDidYouPark,
    ;

    /** Diagnostic label for the departure verdict trace, so a remote session can tell the two
     *  questions apart. The arrival ask used to leave no verdict at all — only a log line — which is
     *  why it was invisible in `diagnostics/{uid}/sessions`. */
    val verdictLabel: String
        get() = when (this) {
            StillParked -> "safety_net_prompt"
            WhereDidYouPark -> "safety_net_arrival_ask"
        }
}

/**
 * Has the per-fence interruption budget refilled? [DET-A-STILL-PARKED-ASK-MUST-EARN-ITS-INTERRUPTION-001]
 *
 * A PREDICATE, not a verdict — it produces no `detectionPath` and no `outcome`, it only guards a
 * door — so it lives here with the rest of detection's pure policy instead of as an injected use
 * case. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * It exists as a function because the throttle used to be an `if` inside ONE arm of the worker's
 * `when`, and the other arm — the arrival ask — neither read it nor stamped it, so that path could
 * repeat every safety-net tick (the exact-alarm lane wakes ~every 5 min) and never consumed the
 * window it skipped either. An invariant that lives in one branch of a `when` is not an invariant.
 *
 * @param lastAskAtMs Epoch-ms of the last ask raised for this fence, or null when none was ever
 *   raised. A non-positive stamp reads as "no record" (SharedPreferences' `0L` default).
 * @param throttleMs [com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig.safetyNetAskThrottleMs].
 */
fun safetyNetAskIsDue(lastAskAtMs: Long?, nowMs: Long, throttleMs: Long): Boolean {
    val last = lastAskAtMs?.takeIf { it > 0L } ?: return true
    val elapsed = nowMs - last
    // A stamp in the FUTURE is corrupt state (the user moved the clock back, or a restored backup
    // brought a stranger's timestamps), not a record of an interruption that happened. Reading it as
    // "asked recently" would mute this fence until the clock caught up — hours or days of silence
    // bought by a bug. Unknown reads the same as never.
    if (elapsed < 0L) return true
    return elapsed >= throttleMs
}
