package io.apptolast.paparcar.domain.detection.state

import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.VehicleType

/**
 * [09 §5] **Who this session is, and what it is allowed to do** — the first of the five sub-states.
 *
 * It owns the session's identity (where and when it began, what armed it, whose car it is) and the
 * one lifecycle AUTHORIZATION in the system: [driveAuthorized]. Everything here is a `val`, and
 * every change is a named transition returning a new value — no caller ever writes a field.
 *
 * ## Why the authorization lives here and not in the drive proof
 *
 * [driveAuthorized] answers *"may this session confirm a park at all?"*, not *"how good is the
 * evidence?"* — it is NOMINATION, and the drive proof is CONFIRMATION. The governing doctrine is
 * that the event nominates and only measured movement confirms, so fusing the two is the exact bug
 * `DET-G-05` closed. It stays out of `DriveProof` on purpose [07 §3.3].
 *
 * ## What this step actually fixes
 *
 * The authorization was mutated from **five** places, and two of them were not atomic with the
 * evidence label they travel with:
 *
 *  - the arm seeds it on trust, together with `verified_*` evidence;
 *  - a late departure confirmation upgrades it to earned — evidence first, seed second, two writes;
 *  - a dismissed departure retracts it — seed first, evidence second, two writes;
 *  - the enter-arm step veto clears it and degrades the evidence — again two writes;
 *  - a fix that proves a drive makes it permanent.
 *
 * Between those two writes the session was readable in a state that is not supposed to exist:
 * authorized with `self_observed` evidence, or unauthorized with `verified_enter`. Nothing observed
 * it in practice — the writes are microseconds apart on one coroutine — but "nothing observed it"
 * is not an invariant, it is luck. Here the evidence and the seed move together or not at all.
 *
 * The other thing it fixes is quieter and worse. `onUserDeniedParking` wipes the whole session state
 * and then hand-copies back the two fields that must survive, by name. A field added to that set has
 * to REMEMBER to be listed there, and nothing fails if it is not — the same shape of bug as the five
 * copied rebind conditions P2.5 exists to remove. [keepingAuthorization] makes it one transition
 * that cannot be forgotten.
 *
 * @property origin First fix of the session, captured once and never overwritten.
 * @property firstFixAtMs Wall clock when that first fix was processed — the session's age reference.
 * @property fixCount Fixes processed so far; the `loc#N` counter in the diagnostics stream.
 * @property lastSpeedMps Speed of the most recent fix. The step gate reads it to tell the egress
 *   WALK from a stop-and-go traffic crawl. [DET-STEP-SPEED-GATE-001]
 * @property previousFix The last fix that went through stop tracking — deliberately EVERY processed
 *   fix, garbage included, because a degraded stretch's phantom stop is exactly the `prev` a
 *   deceleration hop must be measured against. [DET-CREDIBLE-DRIVE-001]
 * @property hasEverMoved Speed AND displacement crossed together at least once. Feeds only the
 *   no-movement guard against a spurious IN_VEHICLE ENTER.
 */
data class SessionTelemetry(
    val origin: GpsPoint? = null,
    val firstFixAtMs: Long? = null,
    val fixCount: Int = 0,
    val lastSpeedMps: Float = 0f,
    val previousFix: GpsPoint? = null,
    val hasEverMoved: Boolean = false,
    /**
     * `true` once this session may treat itself as post-drive: GPS speed reached the trip bar at
     * least once, OR the arm lent it on trust. Enables short-trip detection ("circled the block")
     * and disarms the anti-walking guards. [BUG-SHORT-TRIP][DET-G-04]
     */
    val driveAuthorized: Boolean = false,
    /**
     * `true` while [driveAuthorized] rests ONLY on the arm's word — granted, never measured by this
     * session. It is what decides whether a DISMISSED departure may retract the seed: the worker
     * adjudicates the EXIT, so it may take back what the EXIT lent, and nothing the trip earned.
     * [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001]
     */
    val authorizedOnArmTrustOnly: Boolean = false,
    /** The provenance label this session was armed with, as persisted on the pin. */
    val armEvidence: String = ArmEvidence.LABEL_SELF_OBSERVED,
    /** The vehicle this session was attributed to, locked on the first driving-speed fix. */
    val attributedVehicleId: String? = null,
    val attributedVehicleType: VehicleType? = null,
) {

    /** The session's age in ms, or `0` before the first fix. */
    fun ageMs(now: Long): Long = firstFixAtMs?.let { now - it } ?: 0L

    // ── Transitions ───────────────────────────────────────────────────────────

    /** Session start: the arm's provenance label. */
    fun armed(evidence: String): SessionTelemetry = copy(armEvidence = evidence)

    /**
     * [DET-G-04] The arm says the drive already happened (armed mid-trip), so the session starts
     * authorized — but ON TRUST: nothing here has measured anything yet, and the flag that says so
     * is what keeps the seed retractable until a measurement backs it.
     */
    fun seededOnArmTrust(): SessionTelemetry =
        copy(driveAuthorized = true, authorizedOnArmTrustOnly = true)

    /** One processed fix. Counted separately from [onFix] because the `loc#N` line is logged
     *  before the fix is judged, and the number in the trace must not move. */
    fun countFix(): SessionTelemetry = copy(fixCount = fixCount + 1)

    /**
     * The per-fix bookkeeping, in one place.
     *
     * @param reachedDrivingSpeed This fix was at or above the trip bar with credible accuracy.
     * @param moved Speed and displacement crossed together on this fix.
     * @param driveProven The TRACK has corroborated a drive. This — not a lone in-band fix — is
     *   what makes a trusted seed permanent: a single mirage sample is what granted the seed in the
     *   first place, so it cannot also be what settles it.
     *   [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001]
     */
    fun onFix(
        fix: GpsPoint,
        nowMs: Long,
        reachedDrivingSpeed: Boolean,
        moved: Boolean,
        driveProven: Boolean,
    ): SessionTelemetry = copy(
        origin = origin ?: fix,
        firstFixAtMs = firstFixAtMs ?: nowMs,
        lastSpeedMps = fix.speed,
        driveAuthorized = driveAuthorized || reachedDrivingSpeed,
        authorizedOnArmTrustOnly = authorizedOnArmTrustOnly && !driveProven,
        hasEverMoved = hasEverMoved || moved,
    )

    /** Stop tracking saw this fix. Separate from [onFix] because it runs in the stop-tracking pass,
     *  after the judgement, and the `prev` of the next hop must be the fix as of THAT moment. */
    fun observed(fix: GpsPoint): SessionTelemetry = copy(previousFix = fix)

    /**
     * [DET-G-05] A departure the worker MEASURED after the arm. The seed is granted (if it was not
     * already) and, either way, stops being retractable — atomically with the evidence label that
     * says so.
     */
    fun departureConfirmed(): SessionTelemetry = copy(
        driveAuthorized = true,
        authorizedOnArmTrustOnly = false,
        armEvidence = ArmEvidence.LABEL_VERIFIED_LATE,
    )

    /**
     * [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] The departure this session was armed on was
     * REFUTED: take back what the arm lent on trust, and nothing else. Restores every anti-walking
     * guard, which is what the 2026-08-22 session needed — it had already counted 9 pedestrian
     * steps indoors when this verdict landed.
     */
    fun departureDismissed(): SessionTelemetry = copy(
        driveAuthorized = false,
        authorizedOnArmTrustOnly = false,
        armEvidence = ArmEvidence.LABEL_SELF_OBSERVED,
    )

    /**
     * [DET-SOLID-001] The first step arrived within the veto window of an ENTER arm with no driving
     * seen: this was a walk, not a boarding. Degrade the evidence and un-seed so the false-ENTER
     * abort guard re-arms — one transition, because a session authorized with `self_observed`
     * evidence is a state that should not exist even for a microsecond.
     */
    fun enterArmStepVeto(): SessionTelemetry = copy(
        driveAuthorized = false,
        armEvidence = ArmEvidence.LABEL_SELF_OBSERVED,
    )

    /** The vehicle is resolved. Locked once per session. */
    fun attributeVehicle(id: String?, type: VehicleType?): SessionTelemetry =
        copy(attributedVehicleId = id, attributedVehicleType = type)

    /**
     * Everything that says WHICH session this is, kept while every heuristic it accumulated starts
     * over. Used when the session survives a full state wipe: the arm's provenance, the vehicle it
     * was attributed to and the diagnostics fix counter belong to the session, not to the reasoning.
     */
    fun keepingIdentity(): SessionTelemetry = SessionTelemetry(
        fixCount = fixCount,
        armEvidence = armEvidence,
        attributedVehicleId = attributedVehicleId,
        attributedVehicleType = attributedVehicleType,
    )

    /**
     * The user said "keep driving": the session is still the same session and it still drove, so
     * the authorization and the movement fact survive on top of [keepingIdentity].
     *
     * A named transition rather than a hand-copied field list at the call site — that list is how a
     * field added later silently fails to survive.
     *
     * ⚠️ [authorizedOnArmTrustOnly] deliberately does NOT survive, because it does not survive
     * today either: a dismissal after this point can no longer retract a seed the arm only lent.
     * It is a quirk, not a design, and changing it would change behaviour — so it is written down
     * here rather than fixed in a move.
     */
    fun keepingAuthorization(): SessionTelemetry = keepingIdentity().copy(
        driveAuthorized = driveAuthorized,
        hasEverMoved = hasEverMoved,
    )
}
