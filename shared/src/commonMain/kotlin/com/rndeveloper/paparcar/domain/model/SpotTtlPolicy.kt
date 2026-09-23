package com.rndeveloper.paparcar.domain.model

/**
 * Single source of truth for how long a published community spot stays live. [AUDIT-ARCH-001 M13]
 *
 * These TTLs were duplicated verbatim in the Android worker and the iOS scheduler (each with a
 * "mirrors the other" comment) — a business rule copy-pasted across platforms drifts the moment
 * one side is edited. It lives here in commonMain so both platforms compute the same lifetime
 * from the same numbers, and the manual-vs-auto rule is unit-testable once.
 */
object SpotTtlPolicy {
    /**
     * How long a published spot stays in the map: 2 hours, whoever reported it.
     *
     * [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001] Manual reports used to expire in 15 minutes,
     * and that number was never about decay — a parking space fills at the same rate whether a
     * departing car or a passer-by noticed it was free. It was about how much we TRUSTED the
     * reporter, and trust already has its own channels ([Spot.confidence], [SpotStatus], the
     * person badge). One number doing two jobs.
     *
     * Deleting the report early also protected nobody: it destroyed the only signal we had. At
     * launch density an empty map is a worse failure than an old spot, and an old spot is not a
     * lie as long as the app says how old it is — which is what [SpotFreshness] now does.
     */
    const val AUTO_SPOT_TTL_MS: Long = 2 * 60 * 60 * 1_000L

    /**
     * [DET-HANDOFF-NOT-MANUAL-001 §B] Spots published on a DEDUCED departure
     * ([com.rndeveloper.paparcar.domain.detection.DepartureProof.Deduced]): 12 minutes.
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

    /**
     * [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] How long a spot published on a BOARDING
     * ([SpotStatus.RELEASING]) stays live: **4 minutes**.
     *
     * Shorter than the provisional window, and for a stronger reason. A provisional spot is
     * probably free and might not be; a releasing one is definitely NOT free yet, and only becomes
     * true if the car actually pulls out. So the window is not "how long until we find out" but
     * **how long a boarding can credibly still be a departure in progress** — past that it was
     * somebody fetching a coat, taking a call, or a head unit that powered up on its own.
     *
     * Sized against the measurement that opened the ticket: the boarding leads the departure
     * trigger by 30 s to 3 min 51 s in the four field samples, so four minutes covers the observed
     * range with room to spare and still dies before a wrong one can waste a second driver's trip.
     *
     * Like [PROVISIONAL_SPOT_TTL_MS] this is the FLOOR, not the plan — the explicit withdrawal can
     * fail every way a background job fails, so the lifetime itself has to make failing survivable.
     */
    const val RELEASING_SPOT_TTL_MS: Long = 4 * 60 * 1_000L

    /**
     * The lifetime of a newly published spot, by the CLAIM it goes out with.
     *
     * [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001] This used to take a [SpotType] and hand manual
     * reports a shorter window. It no longer does, and the parameter went with it rather than
     * lingering as a signature that claims an influence it does not have: **the kind of report
     * does not change how fast the space fills.**
     *
     * What DOES shorten it is how sure we are the space is free, and that is not a reporter's
     * trustworthiness — it is the blast radius of being wrong.
     *
     * ⛔ [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] The parameter was a `provisional: Boolean` until
     * a third claim existed. A boolean cannot answer a three-way question, and the way that fails
     * is silent: the new case inherits `false` — the FULL two-hour lifetime — by saying nothing.
     * Asked of the status, a fourth one does not compile until its author answers.
     *
     * [SpotStatus.RETRACTED] is not a publication, so it has no lifetime of its own here; it is
     * given the releasing window because a retraction only ever lands on a document that already
     * exists, and [RETRACTION_GRACE_MS] is what actually bounds it.
     */
    fun ttlMs(claim: SpotStatus = SpotStatus.CONFIRMED): Long = when (claim) {
        SpotStatus.CONFIRMED -> AUTO_SPOT_TTL_MS
        SpotStatus.PROVISIONAL -> PROVISIONAL_SPOT_TTL_MS
        SpotStatus.RELEASING, SpotStatus.RETRACTED -> RELEASING_SPOT_TTL_MS
    }
}
