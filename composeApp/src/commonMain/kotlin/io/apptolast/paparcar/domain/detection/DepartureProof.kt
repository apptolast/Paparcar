package io.apptolast.paparcar.domain.detection

/**
 * [DET-HANDOFF-NOT-MANUAL-001 §B] How well a confirmed departure is PROVEN — the departure half of
 * the doctrine the parking half already obeys: *the event nominates, only measured movement
 * confirms*.
 *
 * Until now every confirmed departure committed the same three irreversible things at once —
 * publish the community spot, release the user's parking session, remove its geofence — no matter
 * what had actually been observed. Field 2026-08-19 22:32: the safety net deduced a departure from
 * the PHONE having left the parked car's neighbourhood, and the user was on a BICYCLE. The car
 * never moved, and it cost the user their car (session released, geofence gone) plus a phantom spot
 * advertised to the community for two hours.
 *
 * The two commitments have opposite risk profiles, so they stop travelling together:
 *
 * | | [Witnessed] | [Deduced] |
 * |---|---|---|
 * | Community spot | published as-is (full TTL) | published **immediately** but PROVISIONAL: short TTL, so a wrong one dies on its own in minutes instead of two hours |
 * | Parking session + geofence | released | **kept** — nothing is given up until a drive is measured |
 *
 * Speed matters for the spot (a free space in a city centre is worth minutes) and is worth nothing
 * for releasing the user's own session — nobody but its owner reads it, and to them an early
 * release is pure loss. See `docs/backlog/det-handoff-not-manual-001.md` §B.
 */
enum class DepartureProof {
    /**
     * The departure was MEASURED: a fresh fix at credible driving speed, a Bluetooth disconnect
     * followed by displacement, or a live session that proved the drive on its own track. The car
     * demonstrably moved.
     */
    Witnessed,

    /**
     * The departure was INFERRED from something short of measured driving: the phone is far from
     * the parked car (safety-net reconcile), or an Activity-Recognition boarding with no speed
     * behind it. Every one of those is equally satisfied by a walk, a bicycle, or somebody else's
     * car — which is exactly how the 2026-08-19 bicycle ride "departed" two cars that were parked
     * at the kerb the whole time.
     */
    Deduced,
}
