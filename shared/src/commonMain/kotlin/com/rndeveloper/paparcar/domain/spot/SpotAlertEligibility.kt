package com.rndeveloper.paparcar.domain.spot

import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus

/**
 * Whether a spot is one we would EVER interrupt anybody about, before asking where it is.
 * [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
 *
 * ⛔ **Top-level and lane-agnostic on purpose.** These vetoes lived inside `isAlertableInZone`,
 * bolted to the zone circle, and from this ticket there are TWO lanes that need them — the zone
 * alert (Plus, works with the app closed) and the spot near your own position (free, only with the
 * map in front of you). Re-deriving them in the second lane is how the two would come to disagree,
 * and the disagreement that costs most is the one the user reminded us of the day this was written:
 * *a spot a user leaves must never be announced to that same user.* One copy, both lanes.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * What it deliberately does NOT answer: **distance**. Each lane measures from a different anchor —
 * the zone measures from a place you saved, the nearby lane from where your body is right now — and
 * folding both into one function would take the anchor as a parameter and stop being a shared rule.
 *
 * @param nowMs used only to drop spots that have already expired. A spot whose TTL ran out between
 *        the query and this call is not news; without the clock a background look would notify about
 *        a free space that stopped existing before the phone buzzed.
 */
fun isSpotWorthAnAlert(
    spot: Spot,
    ownUserId: String?,
    nowMs: Long,
): Boolean = when {
    // Withdrawn: the app stopped believing in it. [DET-HANDOFF-NOT-MANUAL-001 §B.3]
    spot.status == SpotStatus.RETRACTED -> false

    // ⛔ A PROVISIONAL spot lives 12 minutes and the background look runs every 15, so by the time
    // that path could tell anyone, the spot is gone. Excluded HERE and not only in the worker, so
    // the lanes cannot disagree — and stated rather than silently missed: promising a class of alert
    // that structurally cannot arrive is worse than not offering it.
    spot.status == SpotStatus.PROVISIONAL -> false

    // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] And a RELEASING one for both of that reason's halves,
    // each on its own enough: it lives FOUR minutes, so a 15-minute look could never reach anybody
    // in time — and it is not a free space at all yet, so an alert saying "there is a spot" would be
    // false at the instant it buzzed.
    //
    // ⚠️ Stated rather than left to the `else`: this `when` ends in one, so a NEW status would
    // become alertable by saying nothing. That is how a class of alert gets promised by accident.
    spot.status == SpotStatus.RELEASING -> false

    // ⛔ Your own published spot is the place YOU just left — never an alert, in either lane. The
    // nearby lane makes this sharper than the zone one ever did: you are standing ON it, metres
    // away, so it would be the single most likely alert to fire and the most obviously wrong.
    spot.reportedBy == ownUserId -> false

    // Already dead by the time we looked.
    spot.expiresAt != 0L && spot.expiresAt <= nowMs -> false

    else -> true
}
