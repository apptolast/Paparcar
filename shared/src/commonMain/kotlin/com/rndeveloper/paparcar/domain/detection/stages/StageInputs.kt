package com.rndeveloper.paparcar.domain.detection.stages

import com.rndeveloper.paparcar.domain.detection.assertionBlocksRelocation
import com.rndeveloper.paparcar.domain.detection.isHumanPoweredRide
import com.rndeveloper.paparcar.domain.detection.physics.sustainedDriveWitnessed
import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.detection.state.anchorRestMs
import com.rndeveloper.paparcar.domain.detection.state.egressExceedsWalkReach
import com.rndeveloper.paparcar.domain.detection.state.hasEgressDisplacement
import com.rndeveloper.paparcar.domain.detection.state.hasKinematicEgressSignal
import com.rndeveloper.paparcar.domain.detection.state.isAnchorPinned
import com.rndeveloper.paparcar.domain.detection.state.isAnchorWalkEntered
import com.rndeveloper.paparcar.domain.detection.state.isEgressBornAtAnchor
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.usecase.parking.ParkingDecisionInput
import com.rndeveloper.paparcar.domain.usecase.parking.UnattendedParkingSave
import com.rndeveloper.paparcar.domain.usecase.parking.UnattendedSaveInput

/**
 * [09 §C.4] **What the stages hand their verdicts**, and the last thing the stages were still asking
 * the coordinator for.
 *
 * Every stage that consults a pure evaluator needs the same translation — session state and config
 * into the evaluator's flat input — and until now each stage received it as a constructor lambda
 * bound by the coordinator. That indirection had one job, stated where it lived: *keeping it here
 * meanwhile is what lets the vehicle TYPE stay a live read of `attributedVehicleType`*. With the
 * loop presenting the resolved vehicle on the state it hands each stage, the live read IS
 * `state.session.attributedVehicleType`, and the lambda has nothing left to buy.
 *
 * ## One thing was removed, and it was never used
 *
 * `parkingDecisionInput` took an `activeVehicleType` parameter and then ignored it, reading the
 * coordinator's live `attributedVehicleType` instead. Its only caller passed exactly that value, so
 * the two could never disagree — a vestigial parameter, not a behaviour. It is gone.
 */

/**
 * [DET-HUMAN-POWERED-EARLY-CLOSE-001] The ONE place that assembles the pure decision's inputs.
 * Three lanes ask the same question (fast steps+egress confirm, the candidate phase, and the
 * stop-matured check as High is reached) and each used to build the 16-field input by hand — a
 * copy-paste triple where a signal added to one lane silently missed the others.
 *
 * Only three things genuinely differ per lane and they are the parameters: how long ago the
 * candidate reached High, whether a vehicle-exit was seen at that moment, and whether a
 * sustained stop has been certified.
 */
fun DetectionSessionState.parkingDecisionInput(
    location: GpsPoint,
    now: Long,
    elapsedSinceHighMs: Long,
    hadVehicleExit: Boolean,
    restCertified: Boolean,
    config: ParkingDetectionConfig,
) = ParkingDecisionInput(
    // [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] The confirm evaluator asks "may this pin
    // NOW?", so it gets the FRESH count. The unattended verdict asks "did the user walk away
    // from the car at all?" and keeps reading the full `stepCount` — same counter, two
    // questions, and conflating them is what let a discard erase a real egress.
    stepCount = freshStepCount,
    hasEgressDisplacement = hasEgressDisplacement(location, config),
    hadVehicleExit = hadVehicleExit,
    elapsedSinceHighMs = elapsedSinceHighMs,
    vehicleType = session.attributedVehicleType,
    sessionDurationMs = sessionDurationMs(now),
    maxSpeedKmh = maxSpeedKmh,
    sustainedDrivingMs = provenDrivingBandMs, // [DET-MOTOR-PROOF-001]
    drivingEvidence = drivingEvidence(config), // [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001]
    evidenceLabel = session.armEvidence,
    hasKinematicEgress = hasKinematicEgressSignal(config),
    lastSpeedMps = session.lastSpeedMps,
    egressBornAtAnchor = isEgressBornAtAnchor(config),
    anchorWalkEntered = isAnchorWalkEntered(config),
    anchorGapEntered = anchorGapEnteredAtCapture,
    egressExceedsWalkReach = egressExceedsWalkReach(location, config),
    humanPoweredRide = humanPoweredRide(now, config),
    restCertified = restCertified,
    // [DET-ASSERTION-OUTRANKS-INFERENCE-001] Would confirming HERE move a pin the user
    // asserted minutes ago and metres away, on a session that never measured a drive? Then
    // nothing this evaluator can prove outranks it.
    assertedPinBlocksRelocation = session.activeParkedPin?.let { pin ->
        assertionBlocksRelocation(
            pinReliability = pin.detectionReliability,
            pinLocation = pin.location,
            candidate = location,
            nowMs = now,
            // [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] The same verdict the confirm policy reads.
            // This asks literally the same question — *did THIS session witness driving?* — and
            // answering it with a second expression is how four answers to one question came about.
            // The direction is safe: a stricter verdict blocks MORE relocations of a pin the user
            // asserted, which is the side the asymmetric-failure doctrine wants to err on.
            sessionSawDriving = drivingEvidence(config).mayConfirmSilently,
            userConfirmedReliability = config.reliabilityUserConfirmed,
            freshWindowMs = config.reparkPlausibilityWindowMs,
            radiusMeters = config.reparkPlausibilityRadiusMeters,
        )
    } ?: false,
)

/** [DET-WALK-ENTERED-ANCHOR-ZONE-001] The unattended verdict's input, built from the state and
 *  the anchor predicates. */
fun DetectionSessionState.unattendedSaveInput(
    location: GpsPoint,
    now: Long,
    restMs: Long,
    config: ParkingDetectionConfig,
) = UnattendedSaveInput(
    maxSpeedMps = drive.provenMaxSpeedMps,
    pendingMaxSpeedMps = drive.peakMps,
    credibleDrivingFixes = drive.credibleFixCount,
    anchor = anchorTrust.anchor,
    currentFix = location,
    egressOriginFix = anchorTrust.egressBirth?.originFix,
    stepCount = egress.stepCount,
    sessionSawSteps = egress.sensorAlive,
    vehicleExitConfirmed = egress.vehicleExitHint,
    anchorPinned = isAnchorPinned(config),
    anchorGapMs = anchorTrust.capture.gapMs,
    anchorWalkEntered = isAnchorWalkEntered(config),
    anchorStepEventsAtCapture = anchorTrust.capture.stepEvents,
    anchorWalkInSpanMeters = anchorTrust.capture.walkInSpanMeters,
    egressBornAtAnchor = isEgressBornAtAnchor(config),
    egressExceedsWalkReach = egressExceedsWalkReach(location, config),
    anchorRestMs = restMs,
    humanPoweredRide = humanPoweredRide(now, config),
)

/** The verdict's whole reasoning, in one `parkdiag` line — unchanged, word for word. */
fun DetectionSessionState.unattendedVerdictTrace(
    now: Long,
    waitedMs: Long,
    stoppedDuration: Long,
    verdict: UnattendedParkingSave,
    config: ParkingDetectionConfig,
): String =
    "  ⑊ no user response after ${waitedMs}ms " +
        "(limit=${config.confirmationResponseTimeoutMs}ms) → $verdict " +
        "[maxSpeed=${drive.provenMaxSpeedMps}m/s pinned=${isAnchorPinned(config)} " +
        "walkEntered=${isAnchorWalkEntered(config)} walkFixes=${anchorTrust.capture.walkFixes} " +
        "stepEvents=${anchorTrust.capture.stepEvents} sawSteps=${anchorTrust.capture.sawSteps} " +
        "walkInSpan=${anchorTrust.capture.walkInSpanMeters.toInt()}m carRest=${anchorRestMs(now, config)}ms " +
        "stopped=${stoppedDuration}ms " +
        "gapMs=${anchorTrust.capture.gapMs}] " +
        "[DET-WALK-ENTERED-ANCHOR-ZONE-001][DET-GAP-ANCHOR-ZONE-001]"

/** [DET-BIKE-NOT-A-CAR-001] Whether this session's movement was human-powered — the profile
 *  answer OR the measured one. Thin wrapper so both decision sites and the unattended timeout
 *  ask the same pure evaluator with the same inputs. */
fun DetectionSessionState.humanPoweredRide(
    now: Long,
    config: ParkingDetectionConfig,
): Boolean = isHumanPoweredRide(
    vehicleType = session.attributedVehicleType,
    bicycleRideAtMs = egress.bicycleRideAtMs,
    vehicleRideAtMs = egress.vehicleRideAtMs,
    nowMs = now,
    // [DET-MOTOR-PROOF-001] The kinematic source — pedal cadence measured by this session's
    // own stream, for the short rides AR never classifies.
    fastMotionStepEvents = egress.fastMotionStepEvents,
    fastMotionStepFixes = egress.fastMotionStepFixes,
    // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] …and the measurement that outranks both sources —
    // [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001] in both of its shapes, because the clock alone
    // is unreachable on a batched stream.
    sustainedMotorBandMs = drive.motorBandMs,
    sustainedMotorDisplacementRateMps = drive.motorDisplacementRateMps,
    config = config,
)
