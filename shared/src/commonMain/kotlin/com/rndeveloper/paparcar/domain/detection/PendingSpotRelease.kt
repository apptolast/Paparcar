package com.rndeveloper.paparcar.domain.detection

/**
 * [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] Durable record of a plaza published as
 * [com.rndeveloper.paparcar.domain.model.SpotStatus.RELEASING] — *"somebody got into the car parked
 * here"* — that has not yet become a real departure.
 *
 * ## Why this has to be durable
 *
 * Two answers, and only the second is obvious.
 *
 * 1. **The withdrawal needs a bound.** Retracting a spot is an UPDATE, and every branch of the
 *    Firestore `allow update` rule dereferences `resource.data` — a document the expiry already
 *    swept answers PERMISSION_DENIED, not NOT_FOUND. Without a record of "there is something out
 *    there, published at T", the refutation path writes into the void on every session end and the
 *    log fills with what reads like a rules bug: 256 denials in five days, measured on the Oppo.
 *    [DET-RETRACT-DENIED-FOREVER-001]
 * 2. **The nomination outlives the process.** The boarding is observed by a foreground service that
 *    Android may kill at any moment; the departure that promotes it arrives minutes later, possibly
 *    in another process. An in-memory flag would leak a releasing spot on every OEM freeze.
 *
 * ## Why preferences and not a Room column
 *
 * [com.rndeveloper.paparcar.data.datasource.local.room.UserParkingEntity.provisionalDepartureAtMs]
 * is the closest sibling and it IS a column — but it is read by the sync mapper and the reconcile,
 * because a deduced departure changes what the session IS. This changes nothing about the session:
 * it is a note about a document we published elsewhere. A column would buy a migration, a mapper
 * line, a reconcile line and a DTO-parity obligation for a fact that is local by definition.
 *
 * One slot, not a list: you board one car at a time. A second boarding before the first resolves
 * overwrites, which is the safe direction — the older nomination then expires on
 * [com.rndeveloper.paparcar.domain.model.SpotTtlPolicy.RELEASING_SPOT_TTL_MS] instead of being
 * withdrawn early, and the TTL is the floor this whole design rests on.
 */
data class PendingSpotRelease(
    /** The published spot's id — the SESSION's id, so the promotion lands on the same document. */
    val spotId: String,
    /** Which vehicle was boarded, so a departure can tell whether it promotes THIS nomination. */
    val vehicleId: String?,
    /** Epoch-ms the nomination was published. Bounds the withdrawal against the TTL. */
    val nominatedAtMs: Long,
) {
    /**
     * Is there still a document out there to withdraw at [nowMs]?
     *
     * Past the releasing TTL the expiry sweep has already done it, and asking again is the denial
     * storm described above. A negative age (clock skew) reads as live, which is the conservative
     * side: we try to withdraw something that may not exist rather than leave a phantom standing.
     */
    fun isLive(nowMs: Long, ttlMs: Long): Boolean = nowMs - nominatedAtMs <= ttlMs
}
