package io.apptolast.paparcar.domain.model

/**
 * Single source of truth for how long a published community spot stays live. [AUDIT-ARCH-001 M13]
 *
 * These TTLs were duplicated verbatim in the Android worker and the iOS scheduler (each with a
 * "mirrors the other" comment) — a business rule copy-pasted across platforms drifts the moment
 * one side is edited. It lives here in commonMain so both platforms compute the same lifetime
 * from the same numbers, and the manual-vs-auto rule is unit-testable once.
 */
object SpotTtlPolicy {
    /** Auto-detected (and home-geofence) spots: 2 hours — a parked-then-freed spot stays useful
     *  a while. */
    const val AUTO_SPOT_TTL_MS: Long = 2 * 60 * 60 * 1_000L

    /** Manually reported spots: 15 minutes — a human tap is a "right now" signal that goes stale
     *  fast. */
    const val MANUAL_SPOT_TTL_MS: Long = 15 * 60 * 1_000L

    /**
     * [DET-HANDOFF-NOT-MANUAL-001 §B] Spots published on a DEDUCED departure
     * ([io.apptolast.paparcar.domain.detection.DepartureProof.Deduced]): 12 minutes.
     *
     * These go out at full speed — a free space in a city centre is worth minutes, and delaying the
     * publication until a drive is proven would throw away the value where the app matters most.
     * What is cut is not the speed but the EXPOSURE: if the deduction was wrong (the 2026-08-19
     * bicycle ride), the phantom dies on its own in twelve minutes instead of two hours.
     *
     * **This TTL is the floor, not the plan.** The retraction path (drive never proven → withdraw)
     * can fail in every way an Android background job fails — process death, no network, OEM kill —
     * so the lifetime itself has to be short enough that failing to retract is survivable. When the
     * drive IS proven the spot is re-published with the full [AUTO_SPOT_TTL_MS].
     *
     * Sized against the proof it waits for: a session that is going to prove a drive does so within
     * ~1-3 minutes of the departure (`sustainedDriveProofMs` is 30 s of band time), so 12 minutes
     * leaves generous room for a slow start-up while keeping a wrong spot's blast radius small.
     */
    const val PROVISIONAL_SPOT_TTL_MS: Long = 12 * 60 * 1_000L

    /**
     * [DET-HANDOFF-NOT-MANUAL-001 §B.3] How long a RETRACTED spot keeps arriving in the listener's
     * snapshot before the ordinary expiry sweep deletes it: 2 minutes.
     *
     * A retraction that simply deleted the document would take the explanation with it — the
     * marker would vanish mid-approach and the driver would be left circling. Keeping the document
     * alive, flagged [SpotStatus.RETRACTED], is what lets the app say *why* it is gone. Two minutes
     * is long enough for a client that is looking at the spot right now to receive the update and
     * short enough that a withdrawn report never lingers as clutter.
     */
    const val RETRACTION_GRACE_MS: Long = 2 * 60 * 1_000L

    /** The TTL for a spot of [type]: only an explicit manual report gets the short window;
     *  everything else (auto-detected, home-geofence) uses the long one. */
    fun ttlMsForType(type: SpotType): Long =
        if (type == SpotType.MANUAL_REPORT) MANUAL_SPOT_TTL_MS else AUTO_SPOT_TTL_MS

    /**
     * [DET-HANDOFF-NOT-MANUAL-001 §B] The TTL for a spot of [type] published with a departure that
     * is only [provisional]ly proven. A manual report is a human statement and keeps its own
     * window — this only ever shortens the automatic one.
     */
    fun ttlMsForType(type: SpotType, provisional: Boolean): Long = when {
        type == SpotType.MANUAL_REPORT -> MANUAL_SPOT_TTL_MS
        provisional -> PROVISIONAL_SPOT_TTL_MS
        else -> AUTO_SPOT_TTL_MS
    }
}
