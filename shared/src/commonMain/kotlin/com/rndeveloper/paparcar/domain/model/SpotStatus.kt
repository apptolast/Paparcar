package com.rndeveloper.paparcar.domain.model

/**
 * [DET-HANDOFF-NOT-MANUAL-001 §B.3] How well the departure behind a published spot is proven —
 * and, when it turned out not to be proven at all, that the report was withdrawn.
 *
 * Withdrawing a spot is a STATE, not a delete. A deleted document simply stops arriving in the
 * listener's snapshot: the marker vanishes with no explanation, and a driver already heading there
 * is left circling a spot the app silently stopped believing in. A retracted document keeps
 * arriving for [SpotTtlPolicy.RETRACTION_GRACE_MS], long enough for whoever is looking at it to be
 * told why it is gone; the ordinary expiry sweep prunes it afterwards.
 *
 * The transitions are one-way per publication:
 *
 *     RELEASING ──departure──▶ PROVISIONAL ──drive measured──▶ CONFIRMED
 *         │                        │
 *         └───never left───────────┴───────no drive, ever────▶ RETRACTED
 */
enum class SpotStatus {
    /**
     * The departure was WITNESSED (Bluetooth disconnect + displacement, or a geofence exit with
     * measured driving). The default for every spot published before this field existed, and for
     * every manual report — a human tap is a statement about right now.
     */
    CONFIRMED,

    /**
     * The departure was only DEDUCED ([com.rndeveloper.paparcar.domain.detection.DepartureProof.Deduced]):
     * published at full speed because a free space is worth minutes, but nothing measured says the
     * car moved. Shown as unconfirmed and ranked below confirmed spots, and it carries
     * [SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS] instead of the full lifetime.
     */
    PROVISIONAL,

    /**
     * The deduction was refuted — the trip it was deduced from ended without ever measuring a
     * drive (field 2026-08-19: a bicycle ride). The spot is no longer offered to anyone; it only
     * stays around long enough to explain itself to whoever was already looking at it.
     */
    RETRACTED,

    /**
     * [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] **The car is still in it, and somebody just got in.**
     * Published on a BOARDING — a Bluetooth CONNECT to the paired car that holds this plaza, or an
     * Activity-Recognition `IN_VEHICLE` ENTER inside the parked car's own fence.
     *
     * Measured in the field (trigger ledger of 2026-09-22, one user, one day): the boarding lands
     * **30 s to 3 min 51 s** before the trigger that arms the departure today, and the publication
     * is later still — in one of the four samples the geofence only reacted with the car already at
     * 34 km/h.
     *
     * ⛔ **It is NOT an earlier [PROVISIONAL], and that distinction is the whole ticket.**
     * `PROVISIONAL` claims *"the car has probably already gone, nobody measured it"*. This claims
     * something different and stronger: *"the car is STILL THERE and is leaving now"*. A driver who
     * takes it and arrives in sixty seconds finds the car in place. Publishing that as free would
     * spend the one resource a parking app cannot give back — somebody else's drive — so it says
     * what it is, sorts below every genuinely free spot, and carries
     * [SpotTtlPolicy.RELEASING_SPOT_TTL_MS] rather than the provisional twelve minutes.
     *
     * It is still [isAvailable]: hiding it would be the same as not publishing it, and the point is
     * that a nearby driver can start heading over while the space is being vacated.
     */
    RELEASING,
    ;

    /** Whether this spot is still on offer to the community. */
    val isAvailable: Boolean get() = this != RETRACTED

    /**
     * Whether an offer this status carries has to SAY that nobody measured the departure.
     * [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001]
     *
     * ⛔ Hoisted here when the alert became its second consumer: it was a local `val` inside
     * `SpotPeek`, and two copies of «¿hay que decir que no está confirmada?» is how one surface ends
     * up hedging and the other promising — about the same plaza, in the same minute.
     *
     * ⚠️ Not the same question as [isAvailable]. A RELEASING spot is available and NOT unconfirmed:
     * there is no doubt about it at all, the car is simply still inside. Its words are its own.
     */
    val isUnconfirmedOffer: Boolean get() = this == PROVISIONAL
}
