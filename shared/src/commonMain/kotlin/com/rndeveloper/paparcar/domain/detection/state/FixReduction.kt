package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.detection.physics.DriveProofBounds
import com.rndeveloper.paparcar.domain.detection.physics.isCredibleFixAccuracy
import com.rndeveloper.paparcar.domain.detection.stages.DiagnosticNote
import com.rndeveloper.paparcar.domain.detection.stages.plusAssign
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * [09 §5] **The other half of the fix reduction** — the twin of [updateStopTracking], and the last
 * block of the collector that was still a body instead of a reducer.
 *
 * [updateStopTracking] answers *is the car stopped, and where does that leave the anchor*. This one
 * answers the question that comes next and is just as much a reduction: *what did this fix prove*
 * — did the session cross driving speed, did it move, and did it earn a drive proof. Both run
 * before the precedence, neither decides anything about parking, and only one of them had been
 * moved. It sat in the collector for no better reason than that P3.13 stopped there.
 *
 * ## What the move buys, which is the same thing it bought last time
 *
 * The body lived inside a `MutableStateFlow.updateAndGet { }` lambda, and an `update` lambda is
 * RETRYABLE by contract — it re-runs on CAS contention. This one performed I/O: **five**
 * `PaparcarLogger` calls, every one of them describing a TRANSITION ("→ true", "PROVEN by",
 * "witnessed"). Under contention the trace could announce a crossing twice that happened once,
 * which is precisely the duplication class [07 §4.2] names. The five lines come back as
 * [FixReduction.notes] and are said by the caller once, after the winning attempt.
 *
 * The note text was **moved, never retyped** — same rule as the twin, for the same reason: the
 * suite does not read `parkdiag`, so a transcription slip in a diagnostic string is invisible until
 * the day someone is reading a trace to explain a lost trip.
 *
 * ## The constraint this dissolves
 *
 * [DetectionSessionState.onFix] takes the new [DriveProof] as a PARAMETER, and its KDoc explains
 * why: the coordinator needed the proof for its own edge logging, so computing it inside would have
 * meant computing it twice and hoping the two agreed. That reason was real and it is now gone —
 * the reduction that computes the proof is the same one that emits the lines. `onFix` keeps its
 * signature and its meaning (rules 1 and 2, indivisible); it simply has one caller now, which is
 * this function, and the proof reaches it computed exactly once.
 */

/** What one fix PROVED: the reduced state and the trace lines the reduction produced. Mirrors
 *  [StopTracking], minus a duration that has no counterpart here. */
data class FixReduction(
    val state: DetectionSessionState,
    val notes: List<DiagnosticNote> = emptyList(),
)

/**
 * Reduce one fix into what it proved about the session: the two crossings ([SessionTelemetry]'s
 * `hasEverReachedDrivingSpeed` and `hasEverMoved`) and the drive proof, settled together so the
 * session's authorization reads the proof produced by THIS sample rather than the previous one.
 *
 * @param elapsedSinceArmMs Wall clock since the session armed — presented, because the session
 *   clock belongs to the loop and not to the state.
 * @param departureAnchor The pin the car left, which anchors the short-hop profile. Null when the
 *   session armed without one, and then that profile can never fire.
 * @param sustainedDepartureRateMps The ground rate the STOP reduction already measured from the
 *   anchor for this same fix, or null when there was no such departure. Presented for the reason
 *   everything here is presented: it reads the anchor as it stood BEFORE this fix, and a second call
 *   site would be a copy that agrees by luck. [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001]
 */
@Suppress("LongParameterList")
fun DetectionSessionState.reduceFix(
    fix: GpsPoint,
    nowMs: Long,
    elapsedSinceArmMs: Long,
    departureAnchor: GpsPoint?,
    departureFenceRadiusMeters: Float,
    bounds: DriveProofBounds,
    config: ParkingDetectionConfig,
    sustainedDepartureRateMps: Double? = null,
): FixReduction {
    val notes = mutableListOf<DiagnosticNote>()

    val origin = session.origin ?: fix
    val distFromOrigin = haversineMeters(
        origin.latitude, origin.longitude,
        fix.latitude, fix.longitude,
    )
    // [DET-SOLID-001] A driving-speed crossing is only trusted from a fix whose accuracy is
    // credible: a single degraded fix (walking, acc 80–200 m) used to flip
    // hasEverReachedDrivingSpeed and unlock every confirm path — the same hole the DET-G-04 seed
    // opened, but via GPS noise. Same 50 m gate that already protects the driving-clears-anchor
    // decision [LOC-002].
    val credibleSpeedFix = isCredibleFixAccuracy(fix, config.minGpsAccuracyForDriving)
    // [DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001] Judge an OUTSTANDING provisional
    // crossing against this fix, before this fix may mint one. The crossing fix claimed the session
    // left its origin; a fix back INSIDE the origin envelope within the drive-proof window says the
    // ground was never covered — the same two-positions-two-envelopes arithmetic that refutes a
    // stop's stillness [DET-STOP-MUST-BE-STILL-IN-SPACE-001], with the sign flipped. Field
    // 2026-08-27 02:16 (Oppo, sofa): 230 m out at a declared 21,6 m/s, back at the origin 7 s
    // later; the latch stood and bought a 19-minute session and a 02:35 prompt. Past the window (or
    // on a second in-band fix) the crossing settles permanent: staying alive is the cheap side.
    val outstandingCrossing = session.provisionalCrossing
    val withinCoherenceWindow = outstandingCrossing != null &&
        fix.timestamp - outstandingCrossing.timestamp <= config.driveProofWindowMaxMs
    val backInsideOriginEnvelope =
        distFromOrigin <= origin.accuracy + fix.accuracy + config.credibleDriveHopMarginMeters
    val crossingRefuted = outstandingCrossing != null && withinCoherenceWindow && backInsideOriginEnvelope
    val crossingSettled = outstandingCrossing != null && !crossingRefuted &&
        (!withinCoherenceWindow || (credibleSpeedFix && fix.speed >= config.minimumTripSpeedMps))
    val hasJustReachedSpeed = !session.driveAuthorized &&
            fix.speed >= config.minimumTripSpeedMps &&
            credibleSpeedFix
    // Only a crossing that CLAIMS separation is held provisionally — it is the only kind the track
    // can later contradict; a crossing still inside its own origin envelope stays permanent as ever.
    val crossingClaimsSeparation = hasJustReachedSpeed && !backInsideOriginEnvelope
    val hasJustMoved = !session.hasEverMoved &&
            fix.speed >= config.minimumTripSpeedMps &&
            credibleSpeedFix &&
            distFromOrigin >= config.minimumTripDistanceMeters
    if (crossingRefuted) {
        notes += "  ✗ trip crossing REVOKED by its own track — back within the origin envelope " +
            "(${distFromOrigin.toInt()}m, envelopes ${origin.accuracy}+${fix.accuracy}m) " +
            "${(fix.timestamp - outstandingCrossing.timestamp) / 1000}s after claiming " +
            "${outstandingCrossing.speed} m/s; no car produces that track — authorization returns " +
            "to unproven [DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001]"
    }
    if (hasJustReachedSpeed) {
        notes += "  ✓ hasEverReachedDrivingSpeed → true (speed=${fix.speed}≥${config.minimumTripSpeedMps}) dist=${distFromOrigin}m [BUG-SHORT-TRIP]"
    }
    if (crossingClaimsSeparation) {
        notes += "  ⏳ crossing is PROVISIONAL — it also claims ${distFromOrigin.toInt()}m of " +
            "separation, so the next fix must keep its word (a return to the origin inside " +
            "${config.driveProofWindowMaxMs / 1000}s revokes it) " +
            "[DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001]"
    }
    if (hasJustMoved) {
        notes += "  ✓ hasEverMoved → true (speed≥${config.minimumTripSpeedMps}, dist≥${config.minimumTripDistanceMeters}m, actual=${distFromOrigin}m)"
    }
    // [DET-DRIVE-PROOF-001][DET-SHORT-HOP-PROOF-001] Both proofs, the promotion, the ring and the
    // two band clocks live in the sub-state that owns them; the departure pin, its fence and the
    // session clock are PRESENTED.
    val newDrive = drive.onFix(
        fix = fix,
        nowMs = nowMs,
        credibleSpeedFix = credibleSpeedFix,
        departureAnchor = departureAnchor,
        departureFenceRadiusMeters = departureFenceRadiusMeters,
        elapsedSinceArmMs = elapsedSinceArmMs,
        bounds = bounds,
        config = config,
        sustainedDepartureRateMps = sustainedDepartureRateMps,
        // [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] Presented, like everything else here: the origin
        // belongs to the session telemetry and the distance is already measured above. A second
        // call site would be a copy that agrees by luck.
        distanceFromOriginMeters = distFromOrigin,
    )
    if (newDrive.isProven && !drive.isProven) {
        val how = when (newDrive.proven) {
            DriveProofSource.SHORT_HOP -> "displacement from the pin [DET-SHORT-HOP-PROOF-001]"
            else -> "track [DET-DRIVE-PROOF-001]"
        }
        notes += "  ✓ drive PROVEN by $how — session speed statistic unlocked (pendingMax=${newDrive.peakMps}m/s)"
    }
    if (newDrive.drivingBandMs >= config.sustainedDriveProofMs && drive.drivingBandMs < config.sustainedDriveProofMs) {
        notes += "  ✓ sustained drive — ${newDrive.drivingBandMs}ms accumulated in the driving band (≥${config.sustainedDriveProofMs}ms) [DET-MOTOR-PROOF-001]"
    }
    if (newDrive.motorBandMs >= config.sustainedDriveProofMs && drive.motorBandMs < config.sustainedDriveProofMs) {
        notes += "  ✓ MOTOR witnessed — ${newDrive.motorBandMs}ms held above ${config.motorProofSpeedMps} m/s; no bicycle claim can stand against this session [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001]"
    }
    // [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001] The same refutation reached by the other road.
    // Edge-logged separately on purpose: when a session is NOT vetoed, the trace has to say WHICH
    // measurement did the refuting, or the next field test cannot tell a working clock from a
    // working baseline.
    if (newDrive.motorDisplacementRateMps >= config.motorProofSpeedMps &&
        drive.motorDisplacementRateMps < config.motorProofSpeedMps
    ) {
        notes += "  ✓ MOTOR witnessed by displacement — sustained " +
            "${(newDrive.motorDisplacementRateMps * 10).toInt() / 10.0} m/s from the anchor, above " +
            "${config.motorProofSpeedMps} m/s; a hole in the stream cannot erase ground already " +
            "covered [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001]"
    }
    // [09 §5] Rules 1 and 2 of the fix reduction, as one indivisible step: the session's
    // authorization is settled by the proof produced by THIS fix, not by the previous one.
    // See [DetectionSessionState.onFix].
    return FixReduction(
        state = onFix(
            newDrive = newDrive,
            fix = fix,
            nowMs = nowMs,
            reachedDrivingSpeed = hasJustReachedSpeed,
            moved = hasJustMoved,
            crossingClaimsSeparation = crossingClaimsSeparation,
            crossingRefuted = crossingRefuted,
            crossingSettled = crossingSettled,
        ),
        notes = notes,
    )
}
