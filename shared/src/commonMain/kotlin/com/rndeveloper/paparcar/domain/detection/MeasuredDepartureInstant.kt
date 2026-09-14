package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.physics.isAdmissibleEvidence

/**
 * [DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001] The instant the car was MEASURED to leave,
 * or null when nothing measured it.
 *
 * A PREDICATE, not a verdict: it yields no `detectionPath`, no `outcome` and nothing the user
 * reads. It answers the one question every closing path has to answer before it may date a
 * departure, so it lives here with the rest of detection's pure policy functions
 * ([freedSpotIsStillThere], [isHumanPoweredRide], [VehicleFenceOwnershipPolicy]…).
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * **Why it exists.** `freedSpotIsStillThere` asks how old the freed plaza is, and every producer
 * was handing it `System.currentTimeMillis()` — the instant WE processed the trigger, which by
 * construction makes the age zero and the gate unfireable. The measured instants existed all
 * along and went unread: Play Services' `triggeringLocation.time` (the fix that actually crossed
 * the fence), the anchor seal, the AR boarding stamp. This is the funnel they pass through.
 *
 * Two ways a candidate is refused, both of them silence rather than a guess:
 *
 * - **Older than the session** — it describes the trip that CREATED this parking, or an earlier
 *   life entirely, and cannot date a departure FROM it. The same filter
 *   ([isAdmissibleEvidence]) that kills the re-delivered-ENTER class. [DET-SESSION-BIRTH-001]
 * - **In the future** — a provider clock skewed forward would make a long-gone plaza look
 *   younger than it is, which is the exact failure this whole ticket closes, arriving through
 *   the back door. A departure cannot have happened after now.
 *
 * ⛔ The refusal is `null`, and `null` must be carried as `null`: `freedSpotIsStillThere` already
 * reads it as *"an unknown hour is not a recent hour"* and declines to publish. Laundering it
 * into `nowMs` at any call site re-creates the bug — that `?: now` is what
 * [DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001] removed from four places.
 *
 * @param candidateAtMs the measured instant on offer (fence-crossing fix, anchor seal, AR
 *   boarding), or null when this lane measured nothing.
 * @param sessionStartMs when the parking being left began ([UserParking.location.timestamp]);
 *   null when no session is in hand, which [isAdmissibleEvidence] reads as "nothing to contradict".
 * @param nowMs wall clock.
 */
fun measuredDepartureAtMs(
    candidateAtMs: Long?,
    sessionStartMs: Long?,
    nowMs: Long,
): Long? {
    val candidate = candidateAtMs ?: return null
    if (!isAdmissibleEvidence(candidate, sessionStartMs)) return null
    if (candidate > nowMs) return null
    return candidate
}
