package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.fence.VehicleFenceOwnershipPolicy
import com.rndeveloper.paparcar.domain.detection.physics.SavedParkingShape
import com.rndeveloper.paparcar.domain.detection.physics.SessionOutcome
import com.rndeveloper.paparcar.domain.detection.physics.honestZoneRadius
import com.rndeveloper.paparcar.domain.detection.stages.DetectionEffect
import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingConfidence
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.usecase.parking.PromptReason
import com.rndeveloper.paparcar.domain.usecase.parking.UnattendedSaveReason
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** What running a stage settled: whether this fix's pass is over, and whether the session is. */
data class StagePass(val endsPass: Boolean, val endsSession: Boolean)

/**
 * [09 §4] **The layer between the decision and the I/O**, and the third population that kept the
 * coordinator at two thousand lines after ten stages had already left it.
 *
 * A stage decides and returns [DetectionEffect]s; [DetectionEffectExecutor] performs I/O and REPORTS
 * what it learned; neither of them may touch the session state — that was P3.11's acceptance
 * criterion and it still holds. Somebody has to stand between them, and this is it: it turns a list
 * of requests into calls, and turns what those calls report into session state.
 *
 * ## Why it holds the state flow, and why that is not a second writer
 *
 * It writes the session, so on paper it is a writer. In practice it is the loop's own hands: it only
 * ever runs from inside a fix pass, synchronously, between two stages of the same precedence. Making
 * it return transitions for the coordinator to apply would read better and would be a REWRITE of
 * fifteen arms rather than a move — and one of those arms ([DetectionEffect.DiscardCandidate])
 * exists precisely because its transition must be applied to the LIVE state and not to any snapshot
 * a caller could hold.
 *
 * The boundary that matters is unchanged and still enforceable: **no stage imports a repository, and
 * the executor never reads the session.**
 */
class DetectionEffectDispatcher(
    private val state: MutableStateFlow<DetectionSessionState>,
    private val effects: DetectionEffectExecutor,
    private val diagnostics: DetectionDiagnosticsTap,
    private val vehicleRepository: VehicleRepository,
    private val config: ParkingDetectionConfig,
    private val nowMs: () -> Long,
) {

    /**
     * Apply what an effect settled. The executor never reaches into the session; everything it
     * learned comes back as a value and lands here, in one place.
     *
     * @return whether the session is over.
     */
    fun apply(outcome: EffectOutcome): Boolean {
        outcome.sessionOutcome?.let { ended -> state.update { it.copy(session = it.session.endedWith(ended)) } }
        if (outcome.degradeToPrompt) {
            state.update { it.copy(confirmation = it.confirmation.degradedToPrompt(shownAt = nowMs())) }
        }
        outcome.holdOpened?.let { pending ->
            state.update { it.copy(confirmation = it.confirmation.holding(pending)) }
        }
        return outcome.endsSession
    }

    /** [11 bug #3] An abort names its own ending. */
    private fun setOutcome(outcome: SessionOutcome) {
        state.update { it.copy(session = it.session.endedWith(outcome)) }
    }

    /** [DET-FROZEN-COUNTER-001][DET-USER-YES-IS-NOT-A-COORDINATE-001] The radius an approximate save
     *  claims: never below the floor a zone needs to mean anything, never above the ceiling the
     *  config asserts, and never smaller than either witness of the doubt — the centre's own
     *  accuracy or the bound the caller measured. ONE formula, because two paths now save zones
     *  (the unattended timeout and the user's "Sí" over an untrustworthy anchor) and a second copy
     *  is how a radius gets fixed in one and forgotten in the other. */
    private fun approximateZoneRadius(center: GpsPoint, doubtMeters: Double): Float =
        honestZoneRadius(
            centerAccuracyMeters = center.accuracy,
            doubtMeters = doubtMeters,
            floorMeters = config.honestCloseMinZoneRadiusMeters,
            ceilingMeters = config.unattendedZoneMaxRadiusMeters,
        )

    /** [11 bug #3] The wire label back to its type. The only place a string becomes an outcome —
     *  an effect carries the serialized form because that is what a trace contract is made of. */
    private fun detectionOutcomeOf(serialized: String): SessionOutcome = when (serialized) {
        SessionOutcome.AbortedNoMovement.serialized -> SessionOutcome.AbortedNoMovement
        SessionOutcome.AbortedNoMovementJam.serialized -> SessionOutcome.AbortedNoMovementJam
        SessionOutcome.AbortedFalseEnter.serialized -> SessionOutcome.AbortedFalseEnter
        SessionOutcome.AbortedNoVehicle.serialized -> SessionOutcome.AbortedNoVehicle
        SessionOutcome.AbortedResponseTimeout.serialized -> SessionOutcome.AbortedResponseTimeout
        else -> error("no outcome for $serialized")
    }

    /**
     * Turn what a stage ASKED for into what actually happens: the executor performs it, and whatever
     * it reports back lands via [apply]. This is the dispatcher, not the I/O.
     */
    suspend fun run(asked: List<DetectionEffect>, now: Long): StagePass {
        var sessionCompleted = false
        var passEnded = false
        asked.forEach { effect ->
            when (effect) {
                is DetectionEffect.Confirm -> sessionCompleted = when (val shape = effect.shape) {
                    // [DET-USER-YES-IS-NOT-A-COORDINATE-001] A bounded zone never waits out a hold:
                    // it exists because the position is already known to be doubtful, and a grace
                    // window would only let the doubt grow.
                    is SavedParkingShape.BoundedZone -> apply(
                        effects.confirm(
                            state = state.value,
                            location = shape.center,
                            reliability = config.reliabilityUserConfirmed,
                            vehicleId = effect.vehicleId,
                            pathLabel = effect.pathLabel,
                            zoneRadiusMeters = shape.radiusMeters,
                        ),
                    )
                    is SavedParkingShape.ExactPin -> apply(
                        if (effect.mayHold) {
                            effects.beginConfirm(
                                state = state.value,
                                location = shape.location,
                                reliability = shape.reliability,
                                vehicleId = effect.vehicleId,
                                pathLabel = effect.pathLabel,
                                now = now,
                            )
                        } else {
                            effects.confirm(
                                state = state.value,
                                location = shape.location,
                                reliability = shape.reliability,
                                vehicleId = effect.vehicleId,
                                pathLabel = effect.pathLabel,
                            )
                        },
                    )
                    // The two silent shapes never reach an executor: a stage that decides to say
                    // nothing returns no Confirm effect at all.
                    SavedParkingShape.AskUser, SavedParkingShape.KeepSilent ->
                        error("a silent shape is not a confirm: $shape")
                }
                is DetectionEffect.SaveZone -> {
                    val reason = UnattendedSaveReason.entries.first { it.key == effect.reasonKey }
                    val (zoneOutcome, savedZone) = effects.saveZone(
                        state = state.value,
                        reason = reason,
                        center = effect.center,
                        doubtMeters = effect.doubtMeters,
                        vehicleId = effect.vehicleId,
                        location = effect.at,
                        now = now,
                        radiusMeters = approximateZoneRadius(effect.center, effect.doubtMeters),
                    )
                    apply(zoneOutcome)
                    // The zone save degraded to yet another prompt or failed outright. Fall back to
                    // the ask its own reason names, so the user still gets the offer, not silence.
                    if (!savedZone) apply(effects.nudge(reason, effect.vehicleId, effect.at, now))
                    sessionCompleted = true
                }
                is DetectionEffect.SaveUnattended -> {
                    val pin = effect.shape as SavedParkingShape.ExactPin
                    effects.dismissPrompt()
                    val saved = apply(
                        effects.confirm(
                            state = state.value,
                            location = pin.location,
                            reliability = pin.reliability,
                            vehicleId = effect.vehicleId,
                            pathLabel = "unattended_timeout",
                        ),
                    )
                    if (!saved) {
                        // A guard degraded the save to yet another prompt — but the user already
                        // ignored one for the full window, so ending here is the only non-looping
                        // exit. Dismiss the re-posted prompt so nothing dangles. [BUG-STUCK-SESSION]
                        effects.dismissPrompt()
                        setOutcome(SessionOutcome.AbortedResponseTimeout)
                    }
                    sessionCompleted = true
                }
                is DetectionEffect.AskUser -> {
                    apply(
                        effects.nudge(
                            reason = UnattendedSaveReason.entries.first { it.key == effect.reasonKey },
                            vehicleId = effect.vehicleId,
                            location = effect.at,
                            now = now,
                            distanceMeters = effect.distanceMeters,
                        ),
                    )
                    sessionCompleted = true
                }
                is DetectionEffect.DiscardHold -> {
                    state.update { it.copy(confirmation = it.confirmation.discardingHold()) }
                    effects.logHold(effect.action, effect.heldMs, effect.pathLabel, effect.at)
                }
                is DetectionEffect.RecordHoldSettled ->
                    effects.logHold(HoldAction.SETTLED, effect.heldMs, effect.pathLabel, effect.at)
                is DetectionEffect.DiscardCandidate -> {
                    // Applied to the LIVE state, not to the stage's snapshot: the freshness line is
                    // stamped at wherever the count stands NOW.
                    state.update {
                        it.copy(
                            confirmation = it.confirmation.notified(effect.shownAt),
                            egress = it.egress.candidateDiscarded(),
                        )
                    }
                    diagnostics.emit { sid ->
                        DetectionEvent.Candidate(
                            sid, now, action = "DISCARDED", phase = "Candidate→Notified", location = effect.at,
                        )
                    }
                }
                is DetectionEffect.CloseHumanPowered -> {
                    apply(effects.closeHumanPowered(effect.vehicleId, effect.at, now))
                    sessionCompleted = true
                }
                is DetectionEffect.DegradeToPrompt -> {
                    val posted = effects.degradeToPrompt(
                        alreadyPrompted = state.value.confirmation.promptShownAt != null,
                        pathLabel = effect.pathLabel,
                        reason = PromptReason.entries.first { it.key == effect.reasonKey },
                        location = effect.at,
                        now = now,
                    )
                    // The prompt window moves only when a prompt was actually POSTED: re-posting
                    // over one the user is already inside would restart their response timeout.
                    if (posted) {
                        state.update { it.copy(confirmation = it.confirmation.notified(shownAt = now)) }
                    }
                }
                is DetectionEffect.NotifyPrompt -> effects.notifyPrompt(effect.confidence)
                is DetectionEffect.RecordPromptShown -> diagnostics.emit { sid ->
                    DetectionEvent.Decision(
                        sid, now, outcome = "PROMPT_SHOWN", pathLabel = effect.pathLabel,
                        confidence = when (val c = effect.confidence) {
                            is ParkingConfidence.Medium -> c.score
                            is ParkingConfidence.High -> c.score
                            else -> null
                        },
                        location = state.value.session.previousFix,
                    )
                }
                is DetectionEffect.RecordCandidateOpened -> diagnostics.emit { sid ->
                    DetectionEvent.Candidate(sid, now, action = "OPENED", phase = effect.fromPhase)
                }
                is DetectionEffect.ResolveVehicle -> {
                    // Ask → decide: the policy needs the lookup's answer, so the facts come first
                    // and `VehicleFenceOwnershipPolicy` — pure, and older than this refactor —
                    // settles it. [VEH-ACTIVE-FENCE-001][DET-BT-OWNERSHIP-001]
                    val active = vehicleRepository.observeActiveVehicle().first()
                    val nominator = when {
                        effect.nominatingVehicleId == null -> null
                        effect.nominatingVehicleId == active?.id -> active
                        else -> vehicleRepository.observeVehicles().first()
                            .firstOrNull { it.id == effect.nominatingVehicleId }
                    }
                    val resolvedId = VehicleFenceOwnershipPolicy.resolveSessionVehicleId(
                        nominatingVehicleId = effect.nominatingVehicleId,
                        // A BT-paired nominator is vetoed: that identity belongs to the Bluetooth
                        // strategy alone, and its fence only proves the phone left.
                        nominatingVehicleIsBtPaired = nominator?.bluetoothDeviceId != null,
                        activeVehicleId = active?.id,
                    )
                    val nominatorVetoed = effect.nominatingVehicleId != null &&
                        resolvedId != effect.nominatingVehicleId
                    if (resolvedId == null) {
                        PaparcarLogger.w(
                            DIAG,
                            "  ✗ hasEverReachedDrivingSpeed but no vehicle to attribute — abort session" +
                                if (nominatorVetoed) {
                                    " (nominator=${effect.nominatingVehicleId} vetoed: bt-owned, no active vehicle)"
                                } else {
                                    ""
                                },
                        )
                        setOutcome(SessionOutcome.AbortedNoVehicle)
                        sessionCompleted = true
                        passEnded = true
                    } else {
                        // The resolved vehicle's type. Cheap when it IS the active one; a differing
                        // nominator was already looked up in the user's vehicle list.
                        val resolvedType =
                            if (resolvedId == active?.id) active.vehicleType else nominator?.vehicleType
                        state.update {
                            it.copy(session = it.session.attributeVehicle(resolvedId, resolvedType))
                        }
                        PaparcarLogger.d(
                            DIAG,
                            "  ✓ vehicleId locked: $resolvedId type=$resolvedType " +
                                "(nominator=${effect.nominatingVehicleId}" +
                                (if (nominatorVetoed) " vetoed: bt-owned" else "") + ")",
                        )
                    }
                }
                is DetectionEffect.EndSession -> {
                    setOutcome(detectionOutcomeOf(effect.outcome))
                    sessionCompleted = true
                }
                is DetectionEffect.RecordJamFold -> diagnostics.emit { sid ->
                    DetectionEvent.Decision(
                        sid, now,
                        outcome = "NO_MOVEMENT_JAM_FOLD",
                        pathLabel = "recentCreep=${effect.recentCreepMeters.toInt()}m " +
                            "rawMax=${effect.rawPeakMps}mps",
                        location = effect.at,
                    )
                }
                DetectionEffect.DismissPrompt,
                -> error("effect not reachable until its stage lands: $effect")
            }
        }
        return StagePass(endsPass = passEnded, endsSession = sessionCompleted)
    }
    private companion object {
        const val DIAG = PARKDIAG_COORD
    }
}
