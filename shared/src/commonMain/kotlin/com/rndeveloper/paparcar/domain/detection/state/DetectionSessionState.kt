package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.model.GpsPoint

/**
 * [09 §5] The five sub-states, composed — and **the order they are reduced in**.
 *
 * Phase 2 took forty flat fields and gave each of them an owner. What it could not give them, one
 * sub-state at a time, is the thing that only exists BETWEEN them: when two sub-states are reduced
 * against the same input, which of them sees the other's NEW value and which sees the old one.
 *
 * ## Why the order is not a detail
 *
 * The design rule was one-way — *`AnchorTrust` owns the anchor; the steps are PRESENTED to it, never
 * copied in* [07 §2.4]. `DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001` made the traffic run both ways:
 * the cadence classifier has to know whether the anchor is PINNED, because feet moving next to a
 * fast fix mean opposite things on either side of it. With traffic in both directions, a slip in the
 * reduction order changes a verdict and **nothing says so** — the compiler is happy either way and
 * every sub-state's own tests still pass.
 *
 * ## The order, stated
 *
 * **On a GPS fix:**
 *
 *  1. `DriveProof` reduces first, against the pre-fix state.
 *  2. `SessionTelemetry` reduces next and consumes the drive proof **produced by this same fix** —
 *     see [onFix]. This is the one dependency that is ENFORCED here rather than merely written down.
 *  3. `AnchorTrust` and `EgressEvidence` both reduce against the PRE-fix snapshot, so their relative
 *     order is irrelevant by construction: neither reads the other's new value.
 *
 * **On a step event:** the anchor is read as it stood BEFORE the step, and the step never writes the
 * anchor. `EgressEvidence.onStepEvent` returns only itself, so this direction cannot be got wrong.
 *
 * ⚠️ Only rule 2 is enforced by this file. Rules 1 and 3 are held by the coordinator's own fix block
 * today; they become structural when the stages land and the stage list is the order (Fase 3, P3.0
 * `StageOrderTest`). Saying which is which matters more than the list itself — an order that claims
 * to be enforced and is not is worse than one that admits it is a convention.
 */
data class DetectionSessionState(
    val anchorTrust: AnchorTrust = AnchorTrust(),
    val confirmation: ConfirmationLifecycle = ConfirmationLifecycle(),
    val egress: EgressEvidence = EgressEvidence(),
    val session: SessionTelemetry = SessionTelemetry(),
    val drive: DriveProof = DriveProof(),
) {

    /**
     * Rules 1 and 2 of the fix reduction, as one indivisible step.
     *
     * [SessionTelemetry.authorizedOnArmTrustOnly] — the flag that decides whether a dismissed
     * departure may still retract a seed the arm only lent — stops being retractable the moment the
     * TRACK proves a drive. "The moment" is this fix, not the next one: the session must read the
     * proof produced by the same sample it is being reduced against.
     *
     * Passing the drive proof in rather than computing it here is deliberate. The coordinator needs
     * the new proof for its own edge logging (`drive PROVEN by …`, `sustained drive`,
     * `MOTOR witnessed`), which fires on the transition and must keep firing at the same instant
     * with the same numbers. So the proof is computed once, logged, and handed here — instead of
     * being computed twice and agreeing by luck.
     */
    fun onFix(
        newDrive: DriveProof,
        fix: GpsPoint,
        nowMs: Long,
        reachedDrivingSpeed: Boolean,
        moved: Boolean,
    ): DetectionSessionState = copy(
        drive = newDrive,
        session = session.onFix(
            fix = fix,
            nowMs = nowMs,
            reachedDrivingSpeed = reachedDrivingSpeed,
            moved = moved,
            driveProven = newDrive.isProven,
        ),
    )

    // ── Convenience reads, so a call site never has to know which sub-state owns what ──

    /** @see EgressEvidence.freshStepCount */
    val freshStepCount: Int get() = egress.freshStepCount

    /** @see AnchorTrust.bestFix */
    fun bestFix(fallback: GpsPoint): GpsPoint = anchorTrust.bestFix(fallback)

    /** Convenience accessor for the mismatch heuristic — km/h is the human-facing unit. */
    val maxSpeedKmh: Float get() = drive.provenMaxSpeedMps * 3.6f

    /** @see DriveProof.provenDrivingBandMs */
    val provenDrivingBandMs: Long get() = drive.provenDrivingBandMs

    /** Wall-clock duration since the first GPS fix, in ms; `0` if no fix has arrived yet. */
    fun sessionDurationMs(now: Long): Long = session.ageMs(now)

    /** @see AnchorCapture.gapEntered */
    val anchorGapEnteredAtCapture: Boolean get() = anchorTrust.capture.gapEntered
}
