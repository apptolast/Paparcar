package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig

/**
 * [DET-A-SAVED-CARD-MUST-NOT-OUTLIVE-ITS-TRIP-001] Has the saved-parking card
 * («Kamiq aparcado · Cancelar», ranura `PARKING_CONFIRMATION_NOTIFICATION_ID`) stopped belonging to
 * the car it talks about — i.e. may a starting trip take its slot?
 *
 * A PREDICATE, not a verdict: it produces no `detectionPath`, no `outcome` and nothing the user
 * reads. It answers one question that **both lanes** have to ask when a trip begins, which is
 * exactly the shape that lives here rather than as an injected `Evaluate…UseCase`
 * ([freedSpotIsStillThere], [isHumanPoweredRide], [VehicleFenceOwnershipPolicy]…).
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * **Why it exists.** The rule is old — [REFACTOR-300-FIX] wrote it so a spurious `IN_VEHICLE_ENTER`
 * fired by Activity Recognition while the user walks away from the car cannot wipe their chance to
 * tap «Cancelar» — but it lived as an inline expression inside `CoordinatorParkingDetector.invoke()`,
 * the one caller that happens to start a Coordinator session. **The Bluetooth lane never got it**:
 * `btHuntJob` / `btTrackingJob` do not pass through that function, so a BT trip could begin, run and
 * end with the previous trip's card still sitting in the tray, its buttons still pointing at a
 * parking that no longer exists.
 *
 * **A card younger than the window keeps its slot, in both lanes.** The protection is deliberate and
 * survives this ticket: a real trip that starts inside the window still leaves the card visible. What
 * stops that being dangerous is not this predicate but [com.rndeveloper.paparcar.domain.usecase.parking.RevertParkingUseCase],
 * which since the same ticket resolves its subject against the repository — so a card may outlive its
 * trip on screen, but its buttons can no longer act on a session that ended.
 *
 * **An unreadable clock is not an old card.** A negative age (wall-clock skew, a restored backup)
 * means we cannot date the card, and the asymmetry is the same one the rest of detection lives by:
 * keeping a card too long costs a stale notification the user swipes away, dismissing it too early
 * costs them the undo of a wrong pin. Doubt keeps the card.
 *
 * @param postedAtMs When the card was posted, epoch-ms, or **null when none has been posted** (or
 *   the process forgot — the executor's stamp is in-memory by design). Null reads as "nothing of
 *   ours is in that slot worth protecting", which is what the original inline expression did too.
 * @param nowMs Wall clock — the instant the trip starts.
 * @param config Supplies `confirmationResponseTimeoutMs` (15 min) — how long the user gets to undo.
 */
fun savedCardIsStale(
    postedAtMs: Long?,
    nowMs: Long,
    config: ParkingDetectionConfig,
): Boolean {
    val postedAt = postedAtMs ?: return true
    val ageMs = nowMs - postedAt
    if (ageMs < 0) return false
    return ageMs > config.confirmationResponseTimeoutMs
}
