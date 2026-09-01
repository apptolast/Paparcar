package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.detection.state.ConfirmationPhase
import com.rndeveloper.paparcar.domain.detection.physics.effectiveDriving
import com.rndeveloper.paparcar.domain.detection.physics.isCorroboratedVehicleHop
import com.rndeveloper.paparcar.domain.detection.physics.isCredibleFixAccuracy
import com.rndeveloper.paparcar.domain.detection.physics.isCredibleMovingFix
import com.rndeveloper.paparcar.domain.detection.physics.SustainedDeparture
import com.rndeveloper.paparcar.domain.detection.provenanceLabel
import com.rndeveloper.paparcar.domain.detection.stages.DiagnosticNote
import com.rndeveloper.paparcar.domain.detection.stages.plusAssign
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig

/**
 * [09 §5] **The fix reduction that runs before the precedence** — the last block of the coordinator
 * that was neither a stage nor a predicate, and the reason the "finish P3.12 and the old file is
 * empty" estimate was wrong by three hundred lines.
 *
 * It is not a stage: it runs BEFORE the stage list, on every fix, and it decides nothing about
 * parking. What it does is answer *is the car stopped, and if so where does that put the anchor* —
 * which is why every stage below it reads an anchor that this function has already settled.
 *
 * ## What the move actually bought
 *
 * The whole body used to sit inside two `MutableStateFlow.update { }` lambdas. An `update` lambda is
 * RETRYABLE by contract — it re-runs on CAS contention — and this one performed I/O: **ten**
 * `PaparcarLogger` calls [07 §4.2]. Under contention the trace could repeat a line describing a
 * transition that happened once, which is the duplication class 07 §4.2 names. The ten lines are
 * now returned as [StopTracking.notes] and said by the caller ONCE, after the winning reduction —
 * so a retry costs arithmetic and nothing else.
 *
 * The note text itself was **moved, never retyped**: the call wrapper changed, the string
 * expressions did not. Transcribing multi-line diagnostic strings from memory is exactly the
 * operation that produced two silent behaviour errors in P3.11, and the suite does not read
 * `parkdiag`.
 */

/** What one fix's stop tracking settled: the reduced state, how long the CURRENT stop has lasted
 *  (0 while moving), and the trace lines the reduction produced. The duration is returned rather
 *  than derived by the caller because the caller would have to know which of the two branches ran
 *  to derive it.
 *
 *  [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001] [sustainedDeparture] rides out for the same reason
 *  the duration does: this reduction ALREADY measured it against the pre-fix anchor, and `DriveProof`
 *  needs the number a few lines later. Recomputing it there would be a second call site of a pure
 *  function agreeing by luck — the failure shape `DetectionSessionState.onFix` already refuses for
 *  the drive proof. Null while stopped, and null while moving when there is no such departure. */
data class StopTracking(
    val state: DetectionSessionState,
    val stoppedDurationMs: Long,
    val notes: List<DiagnosticNote> = emptyList(),
    val sustainedDeparture: SustainedDeparture? = null,
    /**
     * [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] This fix ended a stop that had an OPEN "did you
     * park?" question: the reduction reset the conversation to `Idle`, so the question is already
     * dead internally and the caller must now take it off the tray and off Home.
     *
     * It rides out for the same reason [sustainedDeparture] does — the reduction already evaluated
     * `effectiveDriving` against the pre-fix anchor, and a second evaluation in the collector would
     * be two call sites of one rule agreeing by luck. It is a REPORT, not a request: what the state
     * does is decided here, what the user sees is executed there.
     */
    val promptRetracted: Boolean = false,
)

/**
 * Updates `stoppedSince` / `stoppedFixes` when the vehicle is stopped, or resets
 * them when it starts moving again. Returns the total stopped duration in ms.
 *
 * At driving speed ([ParkingDetectionConfig.clearBestStopSpeedMps]) the following are
 * also cleared to prevent stale signals from polluting the next genuine stop:
 * the anchor, the vehicle-exit hint, and the phase (back to [ConfirmationPhase.Idle]).
 * With a LOCKED anchor [ANCHOR-LOCK-001] the clear bar rises to real driving
 * ([ParkingDetectionConfig.minimumTripSpeedMps]).
 */
fun DetectionSessionState.updateStopTracking(
    location: GpsPoint,
    now: Long,
    config: ParkingDetectionConfig,
): StopTracking {
    val notes = mutableListOf<DiagnosticNote>()
    // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Set by the moving branch when this fix ends a
    // stop that had an open question. Collected like `notes`: measured here, acted on by the caller.
    var promptRetracted = false
    return if (location.speed < config.stoppedSpeedThresholdMps) {
        val next = this.let { s ->
            // [DET-STOP-MUST-BE-STILL-IN-SPACE-001] A stop is a claim about POSITION, and the
            // declared `speed` field is not position. Field 2026-08-22 Góndola: three fixes
            // reporting 0.0 m/s, 122 m apart over 9.6 s (12.8 m/s of measured ground), matured
            // the stop and froze the anchor in the side-street mouth — 70 m short of the spot,
            // while the car was still rolling in. The same reasoning [DET-CREDIBLE-DRIVE-001]
            // already refuses to trust declared Doppler for the mute band; a stop may not
            // trust it either. Judged against the stop's OWN origin fix (never against the
            // last driving fix — the fix that OPENS a stop is a hop by construction, and
            // discarding it would starve a sparse stream of its only anchor), and it takes
            // car-grade ground rate, so a 55-m GPS drift across a 60-s wait stays a stop.
            val stopOrigin = s.anchorTrust.stopWindowFixes.firstOrNull()
            val stillnessRefuted = stopOrigin != null && isCorroboratedVehicleHop(stopOrigin, location, config)
            if (stillnessRefuted) {
                val moved = com.rndeveloper.paparcar.domain.util.haversineMeters(
                    stopOrigin.latitude, stopOrigin.longitude,
                    location.latitude, location.longitude,
                )
                notes +=
                    "  ⚓✗ stop REFUTED by its own track — ${moved.toInt()}m from the stop origin " +
                        "in ${(location.timestamp - stopOrigin.timestamp) / 1000}s while reporting " +
                        "${location.speed} m/s (envelopes ${stopOrigin.accuracy}+${location.accuracy}m); " +
                        "the car was still moving — not evidence of rest " +
                        "[DET-STOP-MUST-BE-STILL-IN-SPACE-001]"
            }
            val startedAt = s.anchorTrust.stopStartedAt ?: now
            // [DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001] A refutation revokes the stop's
            // EVIDENCE, not its clock. The stop keeps `stoppedSince` — scoring and prompts may
            // still read the full duration, because asking is the cheap side of the asymmetric
            // doctrine — but everything that PROVES rest restarts at the refuting fix: the
            // maturity credit, the capture window, and the anchor captured from fixes the track
            // has now contradicted. Field 2026-08-28 (Redmi): a stop refuted 4× while the car
            // drove home on network fixes matured by TIME at 00:59:42, froze the anchor 3.5 km
            // from the park, and the cascade ended with the pin inside the house.
            val evidenceSince = if (stillnessRefuted) {
                location.timestamp
            } else {
                s.anchorTrust.stopEvidenceSince ?: startedAt
            }
            val anchorFromRefutedFixes = stillnessRefuted && !s.isAnchorPinned(config) &&
                s.anchorTrust.capturedAtStop == startedAt
            if (anchorFromRefutedFixes) {
                notes +=
                    "  ⚓✗ anchor DISOWNED with its refuted stop — captured from fixes the track " +
                        "proved were motion, not rest; the capture contest restarts at this fix " +
                        "[DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001]"
            }
            val anchorTrust = if (anchorFromRefutedFixes) s.anchorTrust.disownedByRefutation() else s.anchorTrust
            val withinInitialWindow = (now - evidenceSince) < config.initialStopWindowMs
            // [DET-GAP-ANCHOR-001] A stop OPENING on the far side of a GPS hole: the previous
            // processed fix was still at REAL driving speed and this one arrived more than
            // anchorGapMaxFixGapMs later — the car's deceleration to rest happened entirely
            // inside the hole, so this position may be a drive-past point (a light, a stale
            // OEM fix), not the park. Speed-only on the pre-gap fix on purpose: Doppler speed
            // stays credible at accuracies that would fail the driving-accuracy bar (the
            // field fix: 17 m/s at 44 m), and requiring accuracy would exempt exactly the
            // degraded streams that produce the hole.
            // [DET-GAP-ANCHOR-ZONE-001] Keep the hole's SIZE, not just its existence: it is the
            // only bound on how far the phone could have walked from the car before the stream
            // came back, and throwing it away is what made every gap-born anchor look
            // unboundable.
            // [DET-A-HOLE-THE-SPEED-FIELD-DENIES-IS-STILL-A-HOLE-001] …and the car was DRIVING is a
            // claim about the ground, not about the `speed` field. Reading only the declared value
            // means a stream that reports 0.0 while covering 879 m in 76 s opens its stop with
            // `gapMs = 0` — no doubt at all, which is worse than a doubt that is too small. Same
            // lesson `DET-STOP-MUST-BE-STILL-IN-SPACE-001` applied one function away, and the same
            // already-calibrated predicate: a hop beyond both accuracy envelopes at a rate no walker
            // sustains. Declared speed stays FIRST and unchanged — Doppler is credible at accuracies
            // the hop test would never clear, which is exactly why this is an `||` and not a swap.
            val newStopGapMs = if (s.anchorTrust.stopStartedAt == null) {
                val previous = s.session.previousFix
                val holeMs = previous?.let { location.timestamp - it.timestamp } ?: 0L
                val cameFromDriving = previous != null &&
                    (
                        previous.speed >= config.minimumTripSpeedMps ||
                            isCorroboratedVehicleHop(previous, location, config)
                        )
                if (cameFromDriving && holeMs > config.anchorGapMaxFixGapMs) holeMs else 0L
            } else {
                s.anchorTrust.stopEnteredAfterGapMs
            }
            if (newStopGapMs > 0L && s.anchorTrust.stopStartedAt == null) {
                notes +=
                    "  ⚓⚠ stop opened after a ${newStopGapMs}ms GPS hole " +
                        "with the car last seen DRIVING (${s.session.previousFix?.speed} m/s) — any anchor bound to this " +
                        "stop is GAP-ENTERED: rest unwitnessed, no silent pin; the hole bounds the doubt to " +
                        "${(newStopGapMs / 1000.0 * config.maxPedestrianSpeedMps).toInt()}m on foot " +
                        "[DET-GAP-ANCHOR-001][DET-GAP-ANCHOR-ZONE-001]"
            }
            // Freeze bestStopLocation after the initial-stop window (default 30 s). [LOC-001]
            // A PINNED anchor (locked by steps OR frozen by a matured end-of-drive stop) is
            // never re-captured at a LATER stop: the car provably rests at the anchor, so a
            // new stop is the pedestrian standing still, never the car. Same-stop refinement
            // (better fixes arriving right after the door slam) stays allowed.
            // [ANCHOR-LOCK-001][DET-ANCHOR-FREEZE-001]
            val pinnedToOtherStop = s.isAnchorPinned(config) && anchorTrust.capturedAtStop != startedAt
            // [DET-ANCHOR-FREEZE-001] While no step has been counted, every fix of the SAME
            // continuous stop is still the parked car — accuracy refinement stays open for
            // the whole stop, not just the initial window. The 30-s cutoff kept a 260-m
            // approach-drift fix as the anchor while the real-spot 9.8-m fix arrived at
            // second 71 of the same stop (field 2026-07-11, Avenida Sanlúcar). The first
            // counted step ends the privilege: from there the better fix may be the walking
            // user, and the lock machinery takes over.
            val sameStopPreEgress = anchorTrust.capturedAtStop == startedAt && s.egress.stepCount == 0
            // [DET-STOP-MUST-BE-STILL-IN-SPACE-001] …and a fix the stop's own track refutes may
            // not become the anchor either. This is the load-bearing half: the anchor is read
            // from the raw fix here, NOT from `stoppedFixes`, so filtering that list alone would
            // have left the Góndola side-street mouth as the pin (its 6.0 m beat the 10.8 m of
            // the fix that opened the stop). Withholding capture is strictly SUBTRACTIVE — a
            // refuted fix can only fail to become the anchor, never displace a good one.
            val mayCapture = !pinnedToOtherStop && !stillnessRefuted &&
                (withinInitialWindow || sameStopPreEgress)
            val newBestStop = when {
                !mayCapture -> anchorTrust.anchor
                anchorTrust.anchor == null || location.accuracy < anchorTrust.anchor.accuracy -> location
                else -> anchorTrust.anchor
            }
            // [DET-STOP-MUST-BE-STILL-IN-SPACE-001] The refuted fix becomes the sole spatial
            // ORIGIN the next fixes are measured against, and the quorum starts over from it.
            // It is not evidence of REST: it cannot be the anchor this beat, and the freeze
            // still needs a full quorum of fixes that agree with it.
            //
            // Only the origin advances — `stoppedSince` deliberately does NOT. Restarting the
            // stop clock reopened `initialStopWindowMs` mid-stop, which let a re-capture happen
            // where master would never have allowed one; both Enamorados replays (the starved
            // MIUI stream whose anchor must stay disowned so the ceiling can prompt) caught it.
            // A creeping stop keeps its clock; what it loses is the right to call itself proven.
            val stoppedFixesNow = when {
                stillnessRefuted -> listOf(location)
                withinInitialWindow && s.anchorTrust.stopWindowFixes.size < config.maxStoppedFixes ->
                    s.anchorTrust.stopWindowFixes + location
                else -> s.anchorTrust.stopWindowFixes
            }
            // [DET-ANCHOR-FREEZE-001] End-of-drive maturation. Three conditions, each load-
            // bearing: measured driving happened; the ANCHOR belongs to THIS stop (freezing
            // an anchor from an earlier stop would assert the car rests somewhere it left);
            // and the stop was DRIVE-ENTERED (walking-range fixes since the last resolved CAR
            // movement stayed within budget — the front-door stand arrives after a stretch of
            // them, the real park after none). Once frozen, only re-measured real driving
            // moves the anchor; a traffic light that matures unfreezes harmlessly when
            // driving resumes.
            val anchorStopOfRecord = if (newBestStop !== anchorTrust.anchor) startedAt else anchorTrust.capturedAtStop
            // [DET-SHORT-TRIP-FREEZE-001] Rest is proven by TIME (≥ anchorFreezeStopMs) OR by
            // EVIDENCE (≥ anchorFreezeStableFixes stopped fixes) — a short trip's destination
            // stop rarely lasts 60 s before the user walks off, but N dense stopped fixes prove
            // the car came to rest here. The other guards (drive-entered, this-stop) are unchanged.
            // [DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001] Proof of rest by TIME measures
            // the UNREFUTED run, never the stop's full clock: a stop refuted mid-life spent part
            // of that clock provably moving, and motion is not credit toward rest.
            val restProvenByTime = (now - evidenceSince) >= config.anchorFreezeStopMs
            // The PRIOR count, not the one including this fix: the freeze fires on the fix whose
            // predecessors already reached the quorum. Counting this one too moves the freeze a
            // beat earlier and pins the Calle Gavia traffic stop (replay caught it).
            val restProvenByFixes = s.anchorTrust.stopWindowFixes.size >= config.anchorFreezeStableFixes
            val matured = !anchorTrust.frozenByRest && s.session.driveAuthorized &&
                newBestStop != null && anchorStopOfRecord == startedAt &&
                anchorTrust.walkIn.fixesSinceDriving <= config.anchorFreezeMaxWalkFixes &&
                // [DET-STOP-MUST-BE-STILL-IN-SPACE-001] Not on the very beat the track refutes —
                // and the TIME credit itself restarts at every refutation (`evidenceSince`), so a
                // stop that opened 60 s ago and has been creeping ever since cannot mature on the
                // clock alone. [DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001]
                !stillnessRefuted &&
                (restProvenByTime || restProvenByFixes)
            if (matured) {
                val how = if (restProvenByTime) "time=${now - evidenceSince}ms" else "stableFixes=${s.anchorTrust.stopWindowFixes.size}"
                notes +=
                    "  ⚓ anchor FROZEN — drive-entered stop matured ($how, " +
                        "walkFixes=${s.anchorTrust.walkIn.fixesSinceDriving}); only real driving " +
                        "(≥${config.minimumTripSpeedMps} m/s) can move it [DET-ANCHOR-FREEZE-001][DET-SHORT-TRIP-FREEZE-001]"
            }
            s.copy(
                // [DET-CREDIBLE-DRIVE-001][DET-CONFIRM-FRESHNESS-001]
                // [DET-WALK-ENTERED-ANCHOR-ZONE-001][DET-GAP-ANCHOR-001] The anchor binds to its
                // stop and its FIVE witnesses are sealed at that same instant — one transition
                // where the same condition used to be written out five times. The step counters
                // are PRESENTED, never copied into the anchor [07 §2.4].
                anchorTrust = anchorTrust.onStoppedFix(
                    stopStartedAt = startedAt,
                    stopWindowFixes = stoppedFixesNow,
                    newAnchor = newBestStop,
                    stopGapMs = newStopGapMs,
                    frozen = matured,
                    stepEventsSinceDriving = s.egress.stepEventsSinceDriving,
                    sensorAlive = s.egress.sensorAlive,
                    stopEvidenceSince = evidenceSince,
                ).withEgressBirth(
                    fix = location,
                    anchorCleared = false,
                    stepCount = s.egress.stepCount,
                    kinematicEgressFixes = anchorTrust.kinematicEgressFixes,
                    // [DET-ANCHOR-EGRESS-001] STOPPED flavour: only a counted step opens a
                    // birth here. [DET-A-DISOWNED-ANCHOR-TAKES-ITS-WALK-WITH-IT-001] Filed as
                    // "bug #6, preserved not fixed"; measured and SETTLED as the rule. A
                    // kinematic count is only earned on a MOVING fix, which opens the birth
                    // there, so a non-zero count here is one earned earlier against a position
                    // this anchor may no longer be — and accepting it would record the birth AT
                    // the anchor, manufacturing the "no doubt" answer.
                    acceptsKinematicWitness = false,
                    birthWindowMs = config.egressBirthWindowMs,
                    refineMaxExtraSteps = config.egressBirthRefineMaxExtraSteps,
                ),
                session = s.session.observed(location),
            )
        }
        StopTracking(next, now - (next.anchorTrust.stopStartedAt ?: 0L), notes)
    } else {
        val isDriving = isCredibleMovingFix(
            location, config.clearBestStopSpeedMps, config.minGpsAccuracyForDriving,
        )
        // [ANCHOR-LOCK-001] Real driving — unambiguous even for a phone on a pedestrian.
        // Field incident 2026-07-04: brisk walking away from the parked car produced Doppler
        // 2.5–3.6 m/s fixes (above clearBestStopSpeedMps) that wiped the true anchor; the park
        // then re-anchored where the user next stood still, 55 m away. Once egress steps are
        // observed, only THIS bar clears the anchor.
        val isRealDrive = isCredibleMovingFix(
            location, config.minimumTripSpeedMps, config.minGpsAccuracyForDriving,
        )
        val isRepositionCandidate = location.speed >= config.repositionSpeedMps &&
                location.accuracy <= config.repositionMaxAccuracyMeters
        // [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001] Hoisted out of the reduction below so it can
        // also leave this function — same value, same instant, same receiver (`this` is the `it` of
        // the `let`), computed ONCE. Its note is still emitted from where it always was, so the
        // ordering of `parkdiag` is untouched.
        val departure = sustainedDepartureFrom(location, now, config)
        if (location.speed >= config.clearBestStopSpeedMps && !isDriving) {
            notes +=
                // [DET-A-FIX-MUST-SAY-WHERE-IT-CAME-FROM-001] `src` is what turns a wall of these
                // into an answer: 38 of them in eleven minutes (field 29→30-08, Redmi) could not be
                // told apart as bad GNSS geometry or a network fix carrying a speed it never
                // measured, and the parking degraded to a 250 m zone with the cause unattributable.
                "  ⊘ ignoring driving-speed fix with poor accuracy " +
                        "(speed=${location.speed} acc=${location.accuracy} " +
                        "src=${location.provenanceLabel()} > " +
                        "minGpsAccuracyForDriving=${config.minGpsAccuracyForDriving})"
        }
        val next = this.let {
            val anchorPinned = it.isAnchorPinned(config)
            // [DET-AR-FIRST-001 F3] Person/car discriminator in the ambiguous band (above
            // clearBestStopSpeedMps, below real driving). The old rule cleared the anchor on
            // ANY credible ambiguous fix unless 8 steps had already locked it — a race the
            // anchor lost whenever the user exited the car immediately (field 2026-07-10,
            // Camelias: 3 steps at the kerb, the walk cleared the true anchor, the pin
            // re-anchored inside the house). Now the physics decide:
            //  - real driving speed → CAR, always wins (clears anchor + flushes steps);
            //  - sustained departure (the position provably RAN from the anchor at vehicle
            //    pace) → CAR even when no single fix is credible [DET-CREDIBLE-DRIVE-001];
            //  - pinned anchor (step lock OR end-of-drive freeze) → PERSON below real driving;
            //  - MUTE counter (zero steps) → the ambiguous band alone can NEVER prove CAR
            //    by its DECLARED speed: "outruns zero steps" is how the walk back from a
            //    reposition laundered the walk odometer and froze the anchor at the house
            //    door (field 2026-07-15, Camelias-Oppo) [DET-CREDIBLE-DRIVE-001]. But a hop
            //    the position PROVABLY made (beyond both accuracy envelopes, at vehicle
            //    ground rate) is independent evidence — without it, the car's own
            //    deceleration to the kerb reads as a walk-in and falsely taints the true
            //    anchor (field 2026-07-16, Galeote: 23.7 m in 5 s against 9.9 m of noise,
            //    counted as pedestrian). A recovery swing never escapes its own ballooning
            //    envelopes, so the Camelias laundering stays impossible;
            //  - displacement outruns the counted steps → CAR (jam creep with jiggle steps);
            //  - steps cover the displacement → PERSON: the anchor holds.
            val outruns = it.movementOutrunsSteps(location, config)
            // The geometry is pure now; the LINE stays here, firing at the same instant it always
            // did and still carrying its numbers — the physics returns the MEASUREMENT rather
            // than a boolean precisely so it does not have to be reworded or moved. `parkdiag`
            // is byte-identical. [DET-CREDIBLE-DRIVE-001]
            if (departure != null) {
                notes +=
                    "  ⇢ SUSTAINED DEPARTURE — position ran ${departure.distanceMeters.toInt()} m from the anchor at " +
                        "${(departure.rateMps * 10).toInt() / 10.0} m/s avg — credible drive by displacement [DET-CREDIBLE-DRIVE-001]"
            }
            val sustainedDeparture = departure != null
            val corroboratedMuteHop = it.egress.stepCount == 0 && isDriving &&
                isCorroboratedVehicleHop(it.session.previousFix, location, config)
            // [DET-CONFIRM-FRESHNESS-001] Stepless departure from a PINNED anchor: the position
            // keeps escaping the anchor's accuracy envelopes at ≥ clearBestStopSpeedMps while a
            // step counter PROVEN alive this session has counted NOTHING for this stop. A person
            // covering that ground feeds steps within a couple of fixes — a live counter's
            // silence is evidence of the CAR (the sub-real-driving traffic-light / parking-search
            // creep, field 2026-07-23 "Bodegas Osborne": 160 m at 6–16 km/h never moved the
            // frozen anchor and the egress walk then confirmed AT the light). A MUTE counter
            // (no step event all session) never trips this — the Camelias-Oppo walk-back
            // laundering stays impossible. Any step event resets the run.
            val steplessQualifies = anchorPinned && it.egress.sensorAlive && it.egress.stepCount == 0 &&
                location.speed >= config.clearBestStopSpeedMps &&
                it.escapesAnchorEnvelope(location, config)
            val newPinnedStepless =
                if (steplessQualifies) it.egress.pinnedSteplessMovingFixes + 1 else it.egress.pinnedSteplessMovingFixes
            val steplessDeparture = newPinnedStepless >= config.frozenAnchorSteplessDepartureFixes
            // The precedence itself lives in `physics/EffectiveDriving.kt`, verbatim — its ORDER
            // is the content, and every row there won an argument with a real trip. The signals
            // are computed here because they read this session's state; the ranking between them
            // is physics and is now directly testable [07 §3.2].
            // [DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001] The run of consecutive real-drive
            // fixes. Any fix that is not one breaks it; a stopped fix resets it in `onStoppedFix`.
            // A PINNED anchor is a rest this session witnessed, so overturning it asks for a run
            // rather than a sample — see the ranking's rows 1a/1b.
            val newRealDriveStreak = if (isRealDrive) it.anchorTrust.realDriveStreak + 1 else 0
            val effectiveDriving = effectiveDriving(
                isRealDrive = isRealDrive,
                realDriveCorroborated = newRealDriveStreak >= config.pinnedAnchorRealDriveFixes,
                sustainedDeparture = sustainedDeparture,
                steplessDeparture = steplessDeparture,
                anchorPinned = anchorPinned,
                corroboratedMuteHop = corroboratedMuteHop,
                stepsCounted = it.egress.stepCount,
                hasAnchor = it.anchorTrust.anchor != null,
                displacementOutrunsSteps = outruns,
                isDriving = isDriving,
            )
            if (steplessDeparture && !isRealDrive && !sustainedDeparture) {
                notes +=
                    "  ⚓⤒ anchor UNPINNED — $newPinnedStepless stepless moving fixes beyond the " +
                        "anchor envelope with a LIVE counter silent → CAR creep, re-anchoring at " +
                        "the next stop [DET-CONFIRM-FRESHNESS-001]"
            }
            if (corroboratedMuteHop && !isRealDrive) {
                notes +=
                    "  ⤳ mute ambiguous fix corroborated as CAR by displacement " +
                        "(speed=${location.speed} acc=${location.accuracy}) [DET-CREDIBLE-DRIVE-001]"
            }
            if (anchorPinned && isRealDrive && newRealDriveStreak < config.pinnedAnchorRealDriveFixes) {
                // [DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001] Without this line the refusal is
                // invisible: the trace would show a trip-speed fix and an anchor that did not move,
                // with nothing saying why. It must also read as PROVISIONAL — the next fix either
                // corroborates the drive and the anchor goes, or breaks the run and it stays.
                notes +=
                    "  ⚓⏸ anchor HELD against a lone trip-speed fix — ${location.speed} m/s " +
                        "acc=${location.accuracy} is run $newRealDriveStreak of " +
                        "${config.pinnedAnchorRealDriveFixes}; a witnessed rest needs corroboration " +
                        "to be overturned [DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001]"
            }
            if (anchorPinned && isDriving && !isRealDrive) {
                val proof = if (it.isAnchorLocked(config)) "LOCKED (steps=${it.egress.stepCount})" else "FROZEN (end-of-drive stop)"
                notes +=
                    "  🔒 anchor $proof — ignoring walking-range speed " +
                            "${location.speed} m/s (< ${config.minimumTripSpeedMps}) [ANCHOR-LOCK-001][DET-ANCHOR-FREEZE-001]"
            } else if (!anchorPinned && it.anchorTrust.anchor != null && isDriving && !isRealDrive && !outruns) {
                notes +=
                    "  ♟ anchor HELD — steps=${it.egress.stepCount} cover the displacement " +
                            "(speed=${location.speed} m/s ambiguous band) [DET-AR-FIRST-001]"
            }
            val newConsecutive = if (isRepositionCandidate) it.anchorTrust.repositionStreak + 1 else 0
            // Reposition burst = slow CAR maneuver. Steps veto it — and so does a FROZEN
            // anchor: a brisk mute-counter walk (≥1.7 m/s, good accuracy) matches the burst's
            // signature exactly and outruns its zero steps, which is how the walk home would
            // clear the end-of-drive anchor. The frozen bar is real driving, nothing less.
            // [DET-AR-FIRST-001][DET-ANCHOR-FREEZE-001]
            val isRepositionBurst = newConsecutive >= config.repositionFixCount && !anchorPinned &&
                (it.anchorTrust.anchor == null || outruns)
            val shouldClearBestStop = effectiveDriving || isRepositionBurst
            if (isRepositionBurst && !effectiveDriving) {
                notes +=
                    "  ⟲ reposition-burst detected " +
                            "(consecutive=$newConsecutive speed=${location.speed} acc=${location.accuracy}) " +
                            "— clearing bestStopLocation [PARKING-001]"
            }
            // [REFACTOR-200] the conversation restarts on driving. Walking pace preserves
            // the current phase so the response-timeout from a prior prompt still ticks
            // — that's how BUG-STUCK-SESSION's "walked home" abort fires.
            val nextConfirmation =
                if (effectiveDriving) it.confirmation.stopEnded() else it.confirmation
            // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The line above has always killed the
            // question INTERNALLY — past it `ResponseTimeoutStage` has no `promptShownAt` and the
            // unattended save never fires. What it could not do is take the question off the two
            // surfaces that show it, so the tray card and the Home row kept asking "did you park?"
            // for the rest of the 15-minute window while the car was demonstrably driving. The
            // retraction is reported here, next to the transition that causes it, for the same
            // reason `sustainedDeparture` rides out of this reduction: recomputing it in the
            // collector would be a second call site of the same rule agreeing by luck.
            if (effectiveDriving && it.confirmation.promptShownAt != null) {
                promptRetracted = true
                notes +=
                    "  ？⊘ open prompt RETRACTED — the car is driving again " +
                        "(speed=${location.speed} acc=${location.accuracy}), so the question no " +
                        "longer applies [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001]"
            }
            // [DET-KINEMATIC-EGRESS-001] The egress walk, measured by GPS: quality
            // pedestrian-band fixes while the anchor is frozen. Cleared with the anchor.
            val newKinematicEgressFixes = when {
                shouldClearBestStop -> 0
                // [DET-KINEMATIC-EGRESS-001] The PEDESTRIAN band — speed BELOW the trip bar
                // with the same accuracy gate. It shares the gate, not the question.
                it.anchorTrust.frozenByRest &&
                    location.speed < config.minimumTripSpeedMps &&
                    isCredibleFixAccuracy(location, config.minGpsAccuracyForDriving) ->
                    it.anchorTrust.kinematicEgressFixes + 1
                else -> it.anchorTrust.kinematicEgressFixes
            }
            it.copy(
                confirmation = nextConfirmation,
                // The stop is over. The anchor survives unless the movement resolved as CAR;
                // the walk-in odometer measures "since the last car movement", which a
                // reposition maneuver also ends. [DET-ANCHOR-FREEZE-001]
                anchorTrust = it.anchorTrust.onMovingFix(
                    anchorCleared = shouldClearBestStop,
                    carMovement = effectiveDriving || isRepositionBurst,
                    fix = location,
                    repositionStreak = newConsecutive,
                    realDriveStreak = newRealDriveStreak,
                    kinematicEgressFixes = newKinematicEgressFixes,
                ).withEgressBirth(
                    fix = location,
                    anchorCleared = shouldClearBestStop,
                    stepCount = it.egress.stepCount,
                    kinematicEgressFixes = newKinematicEgressFixes,
                    // [DET-ANCHOR-EGRESS-001] MOVING flavour: a kinematic walk fix opens a birth
                    // too — the mute-counter user's way to get one, and it works: what has no
                    // witness is a walk whose fixes report BELOW stoppedSpeedThresholdMps, which
                    // never reaches this branch at all.
                    // [DET-A-DISOWNED-ANCHOR-TAKES-ITS-WALK-WITH-IT-001]
                    acceptsKinematicWitness = true,
                    birthWindowMs = config.egressBirthWindowMs,
                    refineMaxExtraSteps = config.egressBirthRefineMaxExtraSteps,
                ),
                // The three reset rules that are NOT the same rule — see [EgressEvidence.onFix].
                egress = it.egress.onFix(
                    effectiveDriving = effectiveDriving,
                    repositionBurst = isRepositionBurst,
                    anchorCleared = shouldClearBestStop,
                    steplessMovingFixes = newPinnedStepless,
                ),
                session = it.session.observed(location),
            )
        }
        StopTracking(next, 0L, notes, sustainedDeparture = departure, promptRetracted = promptRetracted)
    }
}

/** [DET-CREDIBLE-DRIVE-001] Displacement corroboration for a MUTE-counter ambiguous-band
 *  fix: the position provably hopped from the previous fix — beyond BOTH accuracy envelopes
 *  plus [ParkingDetectionConfig.credibleDriveHopMarginMeters] — at a ground rate no walker
 *  sustains (≥ [ParkingDetectionConfig.clearBestStopSpeedMps]). Declared Doppler speed is
 *  what the mute band may not trust; a measured hop is independent evidence. Field-calibrated
 *  on both sides: the Galeote deceleration passes (23.7 m / 5 s against 9.9 m of joint
 *  accuracy — the car rolling to the kerb), the Camelias walk-back recovery swing fails every
 *  hop (its envelopes balloon exactly when it "moves": best case 11.9 m against 14.1 m of
 *  noise) — so the drag-to-home laundering stays impossible. */
private fun isCorroboratedVehicleHop(
    prev: GpsPoint?,
    curr: GpsPoint,
    config: ParkingDetectionConfig,
): Boolean =
    isCorroboratedVehicleHop(
        prev = prev,
        curr = curr,
        hopMarginMeters = config.credibleDriveHopMarginMeters,
        minRateMps = config.clearBestStopSpeedMps,
    )
