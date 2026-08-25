package io.apptolast.paparcar.domain.detection.state

import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.domain.detection.physics.SessionOutcome
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
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
    /**
     * [VEH-ACTIVE-FENCE-001] The vehicle whose fence NOMINATED this session, if a fence did. Session
     * identity in exactly the sense [armEvidence] is: fixed at the arm, read once when the
     * attribution stage settles ownership, never re-derived.
     *
     * It lived as a parameter of the detection loop until P3.7, which is fine while only the loop
     * reads it and stops being fine the moment a STAGE does — a stage sees the state and nothing
     * else, by design.
     */
    val nominatingVehicleId: String? = null,
    /** The vehicle this session was attributed to, locked on the first driving-speed fix. */
    val attributedVehicleId: String? = null,
    val attributedVehicleType: VehicleType? = null,
    /**
     * [DET-ASSERTION-OUTRANKS-INFERENCE-001] The vehicle's ACTIVE parked session as it stood when
     * THIS one armed. A SNAPSHOT, not a live read: the question it answers is whether this session
     * may relocate the pin that existed when it armed, and only the pin's position and
     * `detectionReliability` are ever consulted. Null when the vehicle held no active pin.
     *
     * Session identity in exactly the sense [armEvidence] and [nominatingVehicleId] are — fixed at
     * the arm, never re-derived. It lived as a `@Volatile` of the coordinator until the decision
     * input moved to `stages/`, which is the same reason [nominatingVehicleId] moved in P3.7: a
     * stage sees the state and nothing else, by design.
     */
    val activeParkedPin: UserParking? = null,
    /**
     * [11 bug #3] How this session ENDS — the label its `SessionEnded` event carries and the typed
     * answer the service asks its membership questions of. Defaults to [SessionOutcome.Ended] and is
     * refined by the abort paths and by the confirm.
     *
     * ## Why it is here rather than in `ConfirmationLifecycle`
     *
     * [09 §3] filed it with the confirmation, and the confirmation is wrong for it: both user vetoes
     * WIPE that sub-state (`onUserStoppedDetection`, `onUserDeniedParking`) and the outcome must
     * survive the wipe — `stopped_by_user` is set BY the very call that wipes. It lived as a
     * `@Volatile` outside the state for exactly that reason, and [keepingIdentity] is what lets it
     * come inside without losing the property. It is session lifecycle, not conversation.
     */
    val outcome: SessionOutcome = SessionOutcome.Ended,
    /**
     * The session is over: a confirm landed, an abort fired, or the epilogue finalized a hold.
     *
     * Was a local `var` of the detection loop, read by three sibling coroutines through a closure.
     * In the state it is readable by the hold watchdog and the epilogue without capturing anything,
     * which is what makes the loop a single writer. Survives the wipes for the same reason
     * [outcome] does.
     */
    val completed: Boolean = false,
) {

    /** The session's age in ms, or `0` before the first fix. */
    fun ageMs(now: Long): Long = firstFixAtMs?.let { now - it } ?: 0L

    // ── Transitions ───────────────────────────────────────────────────────────

    /** Session start: the arm's provenance label, the fence that nominated it and the pin it must
     *  not silently move. */
    fun armed(
        evidence: String,
        nominatingVehicleId: String? = null,
        activeParkedPin: UserParking? = null,
    ): SessionTelemetry = copy(
        armEvidence = evidence,
        nominatingVehicleId = nominatingVehicleId,
        activeParkedPin = activeParkedPin,
    )

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

    /** [11 bug #3] This session's ending, named. */
    fun endedWith(outcome: SessionOutcome): SessionTelemetry = copy(outcome = outcome)

    /** The session is over. Nothing below re-decides it. */
    fun sessionCompleted(): SessionTelemetry = copy(completed = true)

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
        nominatingVehicleId = nominatingVehicleId,
        attributedVehicleId = attributedVehicleId,
        attributedVehicleType = attributedVehicleType,
        // The asserted pin survives a wipe for the same reason it is here at all: it describes the
        // world this session found, not anything this session reasoned. As a `@Volatile` it survived
        // by accident of not being in the state; now it survives on purpose.
        activeParkedPin = activeParkedPin,
        // …and so do the two that describe the ENDING. `onUserStoppedDetection` stamps
        // `stopped_by_user` and then wipes; without these two lines the wipe would erase the very
        // thing the call was made to say.
        outcome = outcome,
        completed = completed,
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
