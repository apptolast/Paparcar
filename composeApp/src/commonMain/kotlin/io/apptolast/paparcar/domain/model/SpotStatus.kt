package io.apptolast.paparcar.domain.model

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
 *     PROVISIONAL ──drive measured──▶ CONFIRMED
 *          │
 *          └──────no drive, ever────▶ RETRACTED
 */
enum class SpotStatus {
    /**
     * The departure was WITNESSED (Bluetooth disconnect + displacement, or a geofence exit with
     * measured driving). The default for every spot published before this field existed, and for
     * every manual report — a human tap is a statement about right now.
     */
    CONFIRMED,

    /**
     * The departure was only DEDUCED ([io.apptolast.paparcar.domain.detection.DepartureProof.Deduced]):
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
    ;

    /** Whether this spot is still on offer to the community. */
    val isAvailable: Boolean get() = this != RETRACTED
}
