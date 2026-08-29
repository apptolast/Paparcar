package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.detection.physics.SustainedDeparture
import com.rndeveloper.paparcar.domain.detection.stages.DiagnosticNote
import com.rndeveloper.paparcar.domain.detection.physics.outrunsPedestrianReach
import com.rndeveloper.paparcar.domain.detection.physics.sustainedDepartureFromAnchor
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * [09 §6] **The questions the anchor answers**, and the last population of the coordinator to get an
 * owner.
 *
 * Every function here was a `private fun` of `CoordinatorParkingDetector` — twelve of them, reading
 * the session state and the config and returning a boolean, a distance or a point. None of them was
 * a verdict: not one appears in `detectionPath`, `outcome`, `armEvidence` or `sessionOutcome`, so by
 * [DET-VERDICT-NOT-PREDICATE-001] none of them earned a use case. But the doctrine's other half is
 * the one that had been ignored: *a predicate does not get to stay a private method of the
 * coordinator just because that is the file you happened to be editing*. That reflex is what produced
 * a 2 600-line coordinator with eleven pure predicates inside it, and this file is where they stop
 * being invisible.
 *
 * ## The boundary is preserved
 *
 * `AnchorTrust` owns the anchor and its taints; **the steps are PRESENTED to it, never copied into
 * it** [07 §2.4]. These are extensions on the whole [DetectionSessionState] precisely so that rule
 * survives: the presentation happens HERE, in one place, instead of inside `AnchorTrust` reaching
 * across into `EgressEvidence`.
 *
 * ## The two that used to log
 *
 * [sustainedDepartureFrom] and [refinedParkLocation] each carried a `PaparcarLogger` call inside
 * what was otherwise a pure helper (07 §2.5). They now return their MEASUREMENT and their NOTE, and
 * the caller says it — which is what §7 asks for and, more practically, what lets them be called
 * from inside a `MutableStateFlow.update` lambda without a side effect that a CAS retry would
 * repeat.
 */

/**
 * [DET-A] True when the current fix is at least [ParkingDetectionConfig.minEgressDisplacementMeters]
 * away from [DetectionSessionState.anchorTrust]'s anchor (the lowest-accuracy fix recorded at the
 * parked-car position).
 *
 * The displacement gate is ANDed with the pedestrian-step proof on both confirm paths so that
 * steps counted while the car never moved (phone bouncing in stop-and-go traffic) cannot
 * confirm a phantom spot. Returns false when no anchor has been captured yet — fail-negative,
 * which is the safe direction under the asymmetric-error principle.
 */
fun DetectionSessionState.hasEgressDisplacement(
    current: GpsPoint,
    config: ParkingDetectionConfig,
): Boolean {
    val anchor = anchorTrust.anchor ?: return false
    val d = haversineMeters(
        anchor.latitude, anchor.longitude,
        current.latitude, current.longitude,
    )
    return d >= config.minEgressDisplacementMeters
}

/** [ANCHOR-LOCK-001] Whether the park anchor is LOCKED: pedestrian steps were counted while
 *  stopped, so the user provably exited the car — later Doppler speed on the phone belongs
 *  to the PEDESTRIAN, not the car. A locked anchor is neither re-captured at later stops nor
 *  cleared by walking-range speed; only a REAL drive (≥ minimumTripSpeedMps, credible
 *  accuracy — the errand case: user came back and drove off) unlocks. */
fun DetectionSessionState.isAnchorLocked(config: ParkingDetectionConfig): Boolean =
    anchorTrust.anchor != null && egress.stepCount >= config.anchorLockEgressSteps

/** [DET-ANCHOR-FREEZE-001] LOCKED (step proof) or FROZEN (matured end-of-drive stop) — either
 *  way the anchor is pinned to the car: later stops never re-capture it and only re-measured
 *  real driving clears it. Locked and frozen are independent proofs of the same fact ("the
 *  car rests HERE"), so every consumer treats them identically. */
fun DetectionSessionState.isAnchorPinned(config: ParkingDetectionConfig): Boolean =
    isAnchorLocked(config) || (anchorTrust.anchor != null && anchorTrust.frozenByRest)

/** [DET-CREDIBLE-DRIVE-001][DET-CONFIRM-FRESHNESS-001] The anchor belongs to a stop the user
 *  WALKED into — the pedestrian's standing spot, never the car's rest. The walk-fix budget
 *  alone over-triggers: the car's own final parking maneuver (decelerating 13.5→1.4 m/s into
 *  the spot, mediocre accuracy) spends it too (field 2026-07-23, Vista Hermosa: a perfect
 *  anchor tainted walk-entered → silent confirm degraded to a 1 AM prompt AND the unattended
 *  save refused — the FN's root). A real walk-in FIRES STEP EVENTS on a live counter, a
 *  maneuver fires none — so the MANEUVER EXEMPTION requires ALL of: zero step events since
 *  driving, a counter proven alive by capture, and an entry stretch short enough to be a glide
 *  ([ParkingDetectionConfig.maneuverEntryMaxWalkFixes]). The length cap is load-bearing: a
 *  counter alive EARLIER can go mute for a long walk (Camelias-Oppo: 73 steps counted, then
 *  ZERO events for the ~13-fix walk back to the house — step silence cannot be trusted across
 *  a long stretch, so that taint stands). */
fun DetectionSessionState.isAnchorWalkEntered(config: ParkingDetectionConfig): Boolean {
    if (anchorTrust.capture.walkFixes <= config.anchorFreezeMaxWalkFixes) return false
    val maneuverEntry = anchorTrust.capture.stepEvents == 0 &&
        anchorTrust.capture.sawSteps &&
        anchorTrust.capture.walkFixes <= config.maneuverEntryMaxWalkFixes
    return !maneuverEntry
}

/** [DET-KINEMATIC-EGRESS-001] The GPS-measured egress walk: the anchor froze at the end of
 *  the drive and enough quality pedestrian-band fixes followed. Fed into the pure decision as
 *  `ParkingDecisionInput.hasKinematicEgress`; the decision itself still demands egress
 *  displacement and measured in-session driving. */
fun DetectionSessionState.hasKinematicEgressSignal(config: ParkingDetectionConfig): Boolean =
    anchorTrust.frozenByRest && anchorTrust.anchor != null &&
        anchorTrust.kinematicEgressFixes >= config.kinematicEgressMinWalkFixes

/** [DET-AR-FIRST-001 F3] Person/car discriminator for movement away from the park anchor:
 *  TRUE when the displacement from the anchor has OUTRUN what the steps counted since that
 *  stop could walk (`steps × anchorStrideMeters` + both accuracy envelopes + the egress noise
 *  floor) — physics says a vehicle moved, whatever the Doppler band says. FALSE while the
 *  steps cover the displacement (a person on foot — or not decidable yet: with a generous
 *  stride the pro-person bias is deliberate, see [ParkingDetectionConfig.anchorStrideMeters]).
 *  A phantom-step jam creep outruns its 1–3 jiggle steps within a couple of fixes; a real
 *  walk-away keeps pace with its own count (the counting gate feeds steps during the walk). */
fun DetectionSessionState.movementOutrunsSteps(
    current: GpsPoint,
    config: ParkingDetectionConfig,
): Boolean {
    val anchor = anchorTrust.anchor ?: return false
    return outrunsPedestrianReach(
        base = anchor,
        fix = current,
        steps = egress.stepCount,
        strideMeters = config.anchorStrideMeters,
        floorMeters = config.minEgressDisplacementMeters,
    )
}

/** [DET-CONFIRM-FRESHNESS-001] The fix sits provably OUTSIDE the anchor's accuracy envelopes
 *  (plus the egress noise floor): the position has measurably left the anchor. A Doppler blip
 *  while standing AT the anchor can never qualify, whatever its declared speed — its distance
 *  never escapes its own accuracy. */
fun DetectionSessionState.escapesAnchorEnvelope(
    current: GpsPoint,
    config: ParkingDetectionConfig,
): Boolean {
    val anchor = anchorTrust.anchor ?: return false
    // The same envelope with NO step credit: this asks whether the position has measurably
    // left the anchor at all, not whether a walker could have covered the gap.
    return outrunsPedestrianReach(
        base = anchor,
        fix = current,
        steps = 0,
        strideMeters = config.anchorStrideMeters,
        floorMeters = config.minEgressDisplacementMeters,
    )
}

/** [DET-EGRESS-PEDESTRIAN-CEILING-001] The CONFIRM counterpart of [movementOutrunsSteps]: TRUE
 *  when the current fix sits farther from the park anchor than a pedestrian egress could reach.
 *  Same physics — `steps × anchorStrideMeters` + both accuracy envelopes — but on a much more
 *  GENEROUS floor ([ParkingDetectionConfig.egressBirthFloorMeters], not the tight per-fix
 *  `minEgressDisplacementMeters`): a genuine egress under-logs steps and loses GPS (field trace
 *  Calle Gavia: 68 m walked on 8 logged steps), so a tight ceiling would strand real parks. Its
 *  only job is to rule out VEHICLE-scale displacement — the car driving ~500 m off a drop-off /
 *  pick-up stop while the frozen anchor's steps+egress try to pin a phantom park (field
 *  2026-07-18, Calle Abeto). [hasEgressDisplacement] is the floor on the egress; this is the
 *  ceiling. */
fun DetectionSessionState.egressExceedsWalkReach(
    current: GpsPoint,
    config: ParkingDetectionConfig,
): Boolean {
    val anchor = anchorTrust.anchor ?: return false
    return outrunsPedestrianReach(
        base = anchor,
        fix = current,
        steps = egress.stepCount,
        strideMeters = config.anchorStrideMeters,
        floorMeters = config.egressBirthFloorMeters,
    )
}

/** [DET-CREDIBLE-DRIVE-001] Displacement-corroborated driving: the position has RUN from the
 *  anchor at sustained vehicle pace since the anchor's stop began. Believes no single fix —
 *  not its speed field, not its accuracy: the corroboration is the track itself. Floor sits
 *  beyond both accuracy envelopes plus a pathology margin (GPS recovery swings reach ~68 m
 *  and double back — field 2026-07-15, Camelias-Oppo); the rate window
 *  [minimumTripSpeedMps, sustainedDepartureMaxRateMps] excludes the walk home (≤2 m/s
 *  average) and the cache teleport (absurd rates). The current fix must itself be moving
 *  above walking pace — a pedestrian-band fix never carries the verdict, however far the
 *  anchor sits (that judgment belongs to the egress/ceiling machinery). This is what
 *  unfreezes the anchor when MIUI starves every individual fix of credible accuracy
 *  (field 2026-07-15, Enamorados: 10.12 m/s @ acc 52.4 — the 1.11 km FP's root).
 *
 *  Returns the MEASUREMENT rather than a boolean precisely so the caller's line does not have to
 *  be reworded or moved: `parkdiag` stays byte-identical. Null when there is no such departure. */
fun DetectionSessionState.sustainedDepartureFrom(
    current: GpsPoint,
    now: Long,
    config: ParkingDetectionConfig,
): SustainedDeparture? {
    val anchor = anchorTrust.anchor ?: return null
    val sinceMs = anchorTrust.capturedAtStop ?: return null
    return sustainedDepartureFromAnchor(
        anchor = anchor,
        anchorStoppedSinceMs = sinceMs,
        fix = current,
        nowMs = now,
        movingBarMps = config.clearBestStopSpeedMps,
        floorMeters = config.sustainedDepartureFloorMeters,
        minRateMps = config.minimumTripSpeedMps,
        maxRateMps = config.sustainedDepartureMaxRateMps,
    )
}

/** [DET-ANCHOR-EGRESS-001] The egress must be BORN at the anchor — the ceiling the
 *  displacement gate never had (it only checks a floor, and at 1.11 km from the anchor it is
 *  trivially satisfied). TRUE while the recorded egress birth ([AnchorTrust.egressBirth]'s
 *  `originFix`) sits within walking-consistency of the pinned anchor: both accuracy envelopes,
 *  the steps already counted at birth, a fixed margin — or the hard floor, whichever is larger
 *  (sparse streams can put an honest birth ~100 m out; a wrong-stop anchor sits hundreds of meters
 *  to kilometers away — field 2026-07-15, Camino de los Enamorados). No anchor or no recorded
 *  egress → nothing to judge → true. */
fun DetectionSessionState.isEgressBornAtAnchor(config: ParkingDetectionConfig): Boolean {
    val anchor = anchorTrust.anchor ?: return true
    val birth = anchorTrust.egressBirth ?: return true
    val origin = birth.originFix
    val d = haversineMeters(
        anchor.latitude, anchor.longitude,
        origin.latitude, origin.longitude,
    )
    val allowance = anchor.accuracy + origin.accuracy +
        birth.stepCountAtBirth * config.anchorStrideMeters +
        config.egressBirthMarginMeters
    return d <= maxOf(allowance, config.egressBirthFloorMeters)
}

/**
 * Where an auto confirm should pin, and the one line that explains it if the pin moved.
 *
 * The note is a return value rather than a log inside the function for the reason §7 gives: a pure
 * predicate does not get to talk. The caller prepends it to its own notes, which is exactly where
 * the line printed before — during the branch's evaluation, ahead of everything the branch says
 * afterwards.
 */
data class RefinedPark(val location: GpsPoint, val note: DiagnosticNote? = null)

/** [DET-ANCHOR-EGRESS-001 · Rule A] The position an AUTO confirm should pin. The stop anchor
 *  is measured sitting IN the car (roof multipath with optimistic claimed accuracy — field
 *  2026-07-15, Camelias: a 75-s in-car cluster converged at acc 3 m inside the house); the
 *  egress birth is measured seconds after the first step, phone in open air at the car door.
 *  When the birth carries pin-grade accuracy AND sits within the accuracy envelopes of the
 *  anchor, it is the better witness of "where the car is" — bounded, so it can never move
 *  the pin beyond GPS-noise scale. Anything weaker keeps today's anchor. */
fun DetectionSessionState.refinedParkLocation(
    fallback: GpsPoint,
    config: ParkingDetectionConfig,
): RefinedPark {
    val anchor = anchorTrust.anchor ?: return RefinedPark(bestFix(fallback))
    val birth = anchorTrust.egressBirth ?: return RefinedPark(anchor)
    val origin = birth.originFix
    // Steps are the witness that the birth is the DOOR and not mid-walk. A kinematic
    // (mute-counter) birth is recorded off a fix that is already moving — it feeds the
    // consistency ceiling but must never move the pin ("pin at the frozen anchor, not
    // along the walk" is exactly what the freeze promises mute hardware).
    if (birth.stepCountAtBirth == 0) return RefinedPark(anchor)
    if (origin.accuracy > config.egressBirthRefineMaxAccuracyMeters) return RefinedPark(anchor)
    val d = haversineMeters(
        anchor.latitude, anchor.longitude,
        origin.latitude, origin.longitude,
    )
    // The anchor↔birth gap must be EXPLAINED by the steps taken at birth plus fix noise —
    // that is the physical claim "this is still the car, seen from outside it". Anything
    // larger means one of the two is off in a way walking does not account for: keep the
    // anchor (conservative — on a sparse stream the first stepped fix can already be meters
    // into the walk, and a pin must never follow the pedestrian).
    val maxMove = birth.stepCountAtBirth * config.anchorStrideMeters +
        anchor.accuracy + origin.accuracy
    if (d > maxMove) return RefinedPark(anchor)
    val note = if (origin !== anchor) {
        DiagnosticNote(
            "  ⚓→⚑ pin refined to egress birth (${d.toInt()} m from stop anchor, " +
                "birthAcc=${origin.accuracy} anchorAcc=${anchor.accuracy}) [DET-ANCHOR-EGRESS-001 Rule A]",
        )
    } else {
        null
    }
    return RefinedPark(origin, note)
}

/**
 * [DET-CAR-REST-CLOCK-001] How long the CAR has rested: the pinned anchor's own stop, opened when
 * it halted and cleared only by re-measured driving. Zero while the anchor is unpinned, because
 * then nothing witnessed a rest at all.
 */
fun DetectionSessionState.anchorRestMs(now: Long, config: ParkingDetectionConfig): Long =
    if (isAnchorPinned(config)) anchorTrust.restMsAt(now) else 0L
