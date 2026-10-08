package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.physics.SessionOutcome
import com.rndeveloper.paparcar.domain.detection.physics.inferredPinDoubtRadius
import com.rndeveloper.paparcar.domain.detection.physics.sustainedDriveWitnessed
import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.detection.state.PendingConfirm
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.model.ParkingConfidence
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import com.rndeveloper.paparcar.domain.usecase.notification.ResolveAskedStreetUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.PromptReason
import com.rndeveloper.paparcar.domain.detection.stages.unattendedDecisionEvidence
import com.rndeveloper.paparcar.domain.usecase.parking.UnattendedSaveReason
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * What performing an effect settled, for the caller to APPLY.
 *
 * The executor does I/O and reports; it does not reach into the session. That split is not
 * decoration: `runConfirm` used to read four fields off the live state and write two more, so the
 * only way to ask "what would this confirm do?" was to let it do it. Worse, `saveUnattendedZone`
 * decided whether the zone had been kept by reading the outcome field that the nested `runConfirm`
 * had just written — a coupling through a mutable field, invisible at both ends. Here the nested
 * call RETURNS its outcome and the caller reads it from a value.
 *
 * @property sessionOutcome The terminal label this effect earned, or null when it earned none.
 * @property endsSession Whether the session is over. Distinct from "the save succeeded": a hard
 *   save failure also ends the session, and a degraded confirm does not.
 * @property degradeToPrompt The save was refused as an implausible repark and became a question.
 * @property holdOpened A confirm that is now waiting out its grace window. [DET-C-02]
 */
data class EffectOutcome(
    val sessionOutcome: SessionOutcome? = null,
    val endsSession: Boolean = false,
    val degradeToPrompt: Boolean = false,
    val holdOpened: PendingConfirm? = null,
)

/**
 * [09 §4] **The only place in the detection core that performs I/O.**
 *
 * Stages decide and ask; this executes. Confirm a park, save a zone, nudge, prompt, dismiss, resolve
 * a vehicle, stamp a hold marker — every one of them a side effect, and none of them a decision.
 *
 * ## Why it reports instead of applying
 *
 * The obvious extraction — move the methods, keep them writing the session — was not available: they
 * read the drive proof, the arm evidence and the last fix, and they wrote the outcome and the
 * confirmation phase. A class that both performs I/O and mutates the session is not an executor, it
 * is a second coordinator. So every method takes the state it needs as a value and returns an
 * [EffectOutcome] the caller applies.
 *
 * That also closes a coupling nobody could see: the zone save used to decide whether the zone had
 * been KEPT by reading the outcome field the nested confirm had just written. Two functions
 * communicating through a mutable field, with nothing at either end saying so.
 *
 * ## The one piece of state it owns, and why
 *
 * [savedConfirmPostedAt] crosses sessions BY DESIGN [REFACTOR-300-FIX]: it records when the
 * "Vehículo aparcado · Cancelar" card was posted, so the NEXT session's start can tell a fresh card
 * (preserve it — the user may still want to revert) from a stale one (dismiss it). It is
 * NOTIFICATION state, not session state, which is exactly why it never belonged in the session's
 * own state and why it lives here rather than surviving a reset by accident.
 */
@OptIn(ExperimentalTime::class)
class DetectionEffectExecutor(
    private val confirmParking: ConfirmParkingUseCase,
    private val notifyParkingConfirmation: NotifyParkingConfirmationUseCase,
    private val resolveAskedStreet: ResolveAskedStreetUseCase,
    private val notificationPort: AppNotificationManager,
    private val vehicleRepository: VehicleRepository,
    private val config: ParkingDetectionConfig,
    /** [09 §7] The single emitter. The executor never talks to the logger directly. */
    private val diagnostics: DetectionDiagnosticsTap,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /**
     * When the saved-parking card was last posted. Survives across sessions on purpose — see the
     * class KDoc. Null until one has been posted.
     */
    @Volatile
    var savedConfirmPostedAt: Long? = null
        private set

    /** The card is gone (dismissed as stale, or acknowledged), so its age means nothing now. */
    fun forgetSavedConfirm() {
        savedConfirmPostedAt = null
    }

    /** [DET-C-02] Open the grace window, or confirm now when the window is disabled. */
    suspend fun beginConfirm(
        state: DetectionSessionState,
        location: GpsPoint,
        reliability: Float,
        vehicleId: String?,
        pathLabel: String,
        now: Long,
    ): EffectOutcome {
        if (config.confirmHoldMs <= 0L) {
            return confirm(state, location, reliability, vehicleId, pathLabel)
        }
        PaparcarLogger.d(
            DIAG,
            "  ⏸ tentative confirm ($pathLabel) — holding ${config.confirmHoldMs}ms to rule out an errand stop [DET-C-02]",
        )
        // [DET-HOLD-BRANCHES-MUST-SPEAK-001] The open is the load-bearing marker for testability: a
        // second OPENED in a trace is what distinguishes "the hold swallowed this fix" from "the
        // fast lane re-fired and restarted the clock" — the pair
        // DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001 measured as unobservable.
        logHold(HoldAction.OPENED, heldMs = 0L, pathLabel = pathLabel, location = location)
        return EffectOutcome(
            holdOpened = PendingConfirm(location, reliability, vehicleId, pathLabel, confirmedAt = now),
        )
    }

    /**
     * Save the park.
     *
     * Runs [confirmParking] under `NonCancellable` so the save survives an upstream cancellation,
     * and on success MORPHS the prompt notification into the post-save "Vehículo aparcado ·
     * Confirmar / Cancelar" card [REFACTOR-300]. The old `notificationPort.dismiss(...)` is gone:
     * the morph is what closes BUG-FGS-103 AND gives the user the revert affordance for the cases
     * where an auto-confirm grabbed someone else's car. A `NotAuthenticated` transient error is
     * translated into a warn-level log rather than a failure.
     *
     * @param zoneRadiusMeters Non-null → save an APPROXIMATE ZONE of this radius instead of an exact
     *   point: the fallback that keeps the parking instead of losing it when a guard distrusts the
     *   exact anchor. [DET-FROZEN-COUNTER-001]
     */
    @Suppress("LongParameterList")
    suspend fun confirm(
        state: DetectionSessionState,
        location: GpsPoint,
        reliability: Float,
        vehicleId: String?,
        pathLabel: String,
        zoneRadiusMeters: Float? = null,
        /** Con que numeros decidio la rama que llama. [DET-A-WALK-IS-NOT-AN-EGRESS-001] */
        decisionEvidence: String? = null,
    ): EffectOutcome {
        // [DET-INFERRED-PIN-CARRIES-ITS-DOUBT-001] An INFERRED pin may not claim more precision
        // than the fix it stands on: past the honest-zone floor, the claim is saved as an AREA of
        // the fix's own accuracy. Field 2026-08-28 (Redmi): with the mid-route anchor gone,
        // steps+egress re-anchored on a 92.9 m network fix and saved it as an EXACT pin at
        // reliability 0.9 — the field FP's shape, one street over. Every inferred confirm funnels
        // through here (fast confirm, candidate, hold settle — including `beginConfirm`'s
        // hold-disabled shortcut — and the unattended exact save), so the floor lives here ONCE.
        // Exempt on purpose: user-ASSERTED pins (manual / nudge / in-app confirm) call
        // `ConfirmParkingUseCase` directly — a hand-placed pin is ground truth, whatever the
        // phone's fix claimed — and the user-"Sí" stage arrives either as a zone of its own or
        // with an accuracy already under the floor.
        val honestRadius = zoneRadiusMeters ?: inferredPinDoubtRadius(
            fixAccuracyMeters = location.accuracy,
            floorMeters = config.honestCloseMinZoneRadiusMeters,
            ceilingMeters = config.unattendedZoneMaxRadiusMeters,
        )?.also { radius ->
            PaparcarLogger.d(
                DIAG,
                "  ◯ inferred pin demoted to a ZONE r=${radius}m — fix accuracy " +
                    "${location.accuracy}m cannot carry an exact claim " +
                    "[DET-INFERRED-PIN-CARRIES-ITS-DOUBT-001]",
            )
        }
        var result = EffectOutcome(endsSession = true)
        withContext(NonCancellable) {
            PaparcarLogger.d(
                DIAG,
                "    → confirmParking(reliability=$reliability, path=$pathLabel, zoneRadius=$honestRadius) START",
            )
            // [CONFIRM-NO-NOTIF-CLEANUP] Notification responsibility lives here: the auto-detection
            // path owns the unified state-B "Vehículo aparcado · Cancelar" card so the user has a
            // revert window if AR / steps misfired.
            confirmParking(
                location,
                reliability,
                vehicleId = vehicleId,
                tripMaxSpeedMps = state.drive.provenMaxSpeedMps,
                armEvidence = state.session.armEvidence,
                decisionEvidence = decisionEvidence,
                // [DET-ASSERTION-OUTRANKS-INFERENCE-001] The SUSTAINED figure, not a peak: the
                // guard's job is to tell a real re-park from a walk-away, and one stray sample is
                // not a drive. [DET-MOTOR-PROOF-001]
                // [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] …and "not a drive" is now the one verdict,
                // not this clock read on its own — a band time cannot say whether the position ever
                // went anywhere, and the parafarmacia session cleared none of the three bars.
                sessionSawDriving = state.drivingEvidence(config).mayConfirmSilently,
                // [DET-PIN-PROVENANCE-001] The confirmation path IS the provenance.
                detectionPath = pathLabel,
                zoneRadiusMeters = honestRadius,
                // [DET-STEP-BUDGET-ORIGIN-001] The step baseline seals where the BODY is at confirm
                // — for an egress confirm that is the latest processed fix (already 100+ m from the
                // pin), NOT the anchor. Sealing "at the pin" made the walk home read as a ride
                // (field 2026-07-22, Glorieta).
                sealPoint = state.session.previousFix ?: location,
            )
                .onSuccess { saved ->
                    // [REFACTOR-300] Replace the prompt at the same notification ID with the
                    // post-save card carrying ACK and REVERT, so the user can undo a wrong car.
                    notificationPort.showParkingSavedConfirm(
                        parkingId = saved.id,
                        vehicleName = activeVehicleName(),
                        latitude = saved.location.latitude,
                        longitude = saved.location.longitude,
                        // [NOTIF-ONE-QUESTION-DESERVES-ONE-NOTIFICATION-001] If this confirm came
                        // from the user tapping "Sí" on the ask, the card must not ask again — it
                        // keeps only the undo, and it does not alert. The flag is the session's own
                        // answer, not a guess about how loud the last notification was.
                        userAnswered = state.confirmation.userConfirmed,
                    )
                    // ⚠️ The WALL clock, not the injected one: this timestamp is compared against a
                    // future session's start to age a notification, and a test clock that resets
                    // per session would make a stale card look fresh.
                    savedConfirmPostedAt = Clock.System.now().toEpochMilliseconds()
                    result = EffectOutcome(
                        sessionOutcome = SessionOutcome.Confirmed(pathLabel),
                        endsSession = true,
                    )
                    diagnostics.emit { sid ->
                        DetectionEvent.Decision(
                            sid, nowMs(), outcome = "CONFIRMED", pathLabel = pathLabel,
                            confidence = reliability, location = location,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is PaparcarError.Parking.ImplausibleRepark) {
                        // [DET-SOLID-001] The guard says this auto-confirm would relocate a fresh
                        // nearby park without the session ever seeing driving — likely pedestrian.
                        // Degrade to the prompt rather than saving silently OR failing silently: a
                        // real (rare) ultra-short repark is one tap away, and the response timeout
                        // aborts the session if the prompt is ignored.
                        PaparcarLogger.w(DIAG, "    ⊘ implausible repark → degrading to user prompt ($pathLabel) [DET-SOLID-001]")
                        val askedAt = state.witnessedCarStop(location)
                        val asked = activeVehicle()
                        notificationPort.showParkingConfirmation(
                            IMPLAUSIBLE_REPARK_PROMPT_SCORE,
                            asked.askedName(),
                            asked?.id,
                            // The question is about the car's witnessed stop, not about the
                            // rejected save's own point. [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001]
                            askedAt,
                            resolveAskedStreet(askedAt),
                        )
                        result = EffectOutcome(endsSession = false, degradeToPrompt = true)
                        diagnostics.emit { sid ->
                            DetectionEvent.Decision(
                                sid, nowMs(), outcome = "CONFIRM_DEGRADED_PROMPT", pathLabel = pathLabel,
                                // [DET-PROMPT-STATES-ITS-REASON-001] The SIXTH producer, and the
                                // only one outside the evaluator: it degrades on a rejected save,
                                // not on a doubted proof, and it read identically in the trace
                                // until that ticket.
                                location = location, reason = PromptReason.IMPLAUSIBLE_REPARK.key,
                            )
                        }
                        return@onFailure
                    }
                    if (e is PaparcarError.Auth.NotAuthenticated) {
                        // Transient session loss — not a real crash. Self-heals on next launch.
                        PaparcarLogger.w(TAG, "confirmParking ($pathLabel path) — session temporarily unavailable")
                    } else {
                        PaparcarLogger.e(TAG, "Failed to confirm parking ($pathLabel path)", e)
                    }
                    notificationPort.showConfirmationFailed()
                    // Save failed → no parkingId to revert. Just clean up the prompt.
                    notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
                    result = EffectOutcome(
                        sessionOutcome = SessionOutcome.ConfirmFailed(pathLabel),
                        endsSession = true,
                    )
                    diagnostics.emit { sid ->
                        DetectionEvent.Decision(
                            sid, nowMs(), outcome = "CONFIRM_FAILED", pathLabel = pathLabel, location = location,
                        )
                    }
                }
            PaparcarLogger.d(DIAG, "    ← confirmParking(reliability=$reliability, path=$pathLabel) END")
        }
        return result
    }

    /**
     * [DET-FROZEN-COUNTER-001] Save an approximate AREA instead of losing the park.
     *
     * The unattended-timeout fallback: a guard distrusts the EXACT anchor, but the session measured
     * real driving — a parking happened somewhere near the evidence, and losing it entirely costs
     * the user their car (field 2026-07-25/26, Redmi: 92 driving fixes ended in a nudge nobody saw
     * and no saved parking; the released spot left the vehicle nowhere). So an honest AREA is saved
     * instead: centred on the best witness ([center]), radius wide enough to also cover
     * [doubtMeters] — the guard's own measure of how far the truth may sit from the centre, because
     * **a zone is only honest when that doubt is BOUNDABLE**; guards with unbounded doubt keep the
     * nudge-only exit. Reliability sits at the unattended floor so nothing community-facing trusts
     * it on its own, and the saved-parking card is the correction surface.
     *
     * @return the confirm's outcome plus whether the zone was actually KEPT — false when the save
     *   failed or a guard inside the confirm degraded it, and then the caller falls back to the
     *   nudge-only exit. That second answer used to be read off the session's outcome field, which
     *   the nested confirm had just written — here it comes from the nested call's own return value.
     */
    @Suppress("LongParameterList")
    suspend fun saveZone(
        state: DetectionSessionState,
        reason: UnattendedSaveReason,
        center: GpsPoint,
        doubtMeters: Double,
        vehicleId: String?,
        location: GpsPoint,
        now: Long,
        radiusMeters: Float,
    ): Pair<EffectOutcome, Boolean> {
        PaparcarLogger.d(
            DIAG,
            "  ◯ unattended zone (${reason.key}) — r=${radiusMeters}m (doubt=${doubtMeters.toInt()}m, " +
                "centerAcc=${center.accuracy}) instead of losing the park [DET-FROZEN-COUNTER-001]",
        )
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        // The confirm answers "session should end", not "saved": a hard save failure ends the
        // session too. Only a real confirmed outcome counts as the zone being kept; anything else
        // falls back to the caller's nudge-only exit so the ask still happens.
        val outcome = confirm(
            state = state,
            location = center,
            reliability = config.reliabilityUnattendedSave,
            vehicleId = vehicleId,
            pathLabel = DetectionPath.UnattendedZone(reason.key).label,
            zoneRadiusMeters = radiusMeters,
            decisionEvidence = state.unattendedDecisionEvidence(reason),
        )
        val savedOk = outcome.endsSession && outcome.sessionOutcome?.isConfirmed == true
        diagnostics.emit { sid ->
            DetectionEvent.Decision(
                sid, now,
                outcome = if (savedOk) "UNATTENDED_ZONE_SAVED" else "UNATTENDED_ZONE_SAVE_FAILED",
                pathLabel = DetectionPath.UnattendedZone(reason.key).label,
                distanceMeters = doubtMeters,
                radiusMeters = radiusMeters,
                location = location,
            )
        }
        return outcome to savedOk
    }

    /** Ask the user to mark the spot: no artifact here is honest. */
    suspend fun nudge(
        reason: UnattendedSaveReason,
        vehicleId: String?,
        location: GpsPoint,
        now: Long,
        distanceMeters: Double? = null,
    ): EffectOutcome {
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        notificationPort.showMarkParkingNudge(source = reason.nudgeSource, vehicleId = vehicleId)
        diagnostics.emit { sid ->
            DetectionEvent.Decision(
                sid, now,
                outcome = reason.decisionOutcome,
                pathLabel = DetectionPath.UnattendedTimeout.label,
                distanceMeters = distanceMeters,
                location = location,
            )
        }
        return EffectOutcome(
            sessionOutcome = SessionOutcome.AbortedUnattended(reason.key),
            endsSession = true,
        )
    }

    /**
     * [DET-HUMAN-POWERED-EARLY-CLOSE-001] A muscle-powered ride at a matured rest: the same offer
     * the unattended timeout used to make fifteen minutes later, made the moment the verdict exists.
     *
     * Reuses [UnattendedSaveReason.HUMAN_POWERED] on purpose — one vocabulary in the trace, so a
     * field comparison against every previous bicycle session still lines up.
     */
    suspend fun closeHumanPowered(vehicleId: String?, location: GpsPoint, now: Long): EffectOutcome {
        PaparcarLogger.d(
            DIAG,
            "  ⊘ human-powered ride at a matured stop — closing NOW instead of idling to the " +
                "response timeout [DET-HUMAN-POWERED-EARLY-CLOSE-001]",
        )
        return nudge(UnattendedSaveReason.HUMAN_POWERED, vehicleId, location, now)
    }

    /**
     * [DET-A-TRAIN-IS-NOT-A-CAR-001] The session measured movement no road vehicle makes: end it,
     * record why, and notify NOBODY.
     *
     * Everything [nudge] does except the one line that speaks — deliberately spelled out here rather
     * than added as a `silent: Boolean` parameter to it. A flag on the nudge would mean a future
     * caller could get silence by omission; a separate function means silence has to be chosen, and
     * the trace still gets its `Decision` either way. The pending confirmation IS dismissed: a
     * question already on screen is as wrong as a new one, and more so — it is the one the user
     * might actually answer.
     */
    suspend fun closeNotARoadVehicle(vehicleId: String?, location: GpsPoint, now: Long): EffectOutcome {
        val reason = UnattendedSaveReason.NOT_A_ROAD_VEHICLE
        PaparcarLogger.d(
            DIAG,
            "  ⊘ movement beyond any road vehicle (vehicle=$vehicleId) — ending the session with no " +
                "pin, no prompt and no nudge [DET-A-TRAIN-IS-NOT-A-CAR-001]",
        )
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        diagnostics.emit { sid ->
            DetectionEvent.Decision(
                sid, now,
                outcome = reason.decisionOutcome,
                pathLabel = DetectionPath.UnattendedTimeout.label,
                location = location,
            )
        }
        return EffectOutcome(
            sessionOutcome = SessionOutcome.AbortedUnattended(reason.key),
            endsSession = true,
        )
    }

    /**
     * [DET-A-DEGRADED-PROMPT-MUST-NOT-AUTO-CONFIRM-001] The response window closed on a question
     * that only existed because a confirm had been REFUSED: end the session, take the question off
     * screen, record which refusal outlived the window, and place nothing.
     *
     * Deliberately the same shape as [closeNotARoadVehicle] — end, dismiss, trace, no notification —
     * and deliberately a separate function rather than a `silent` flag on [nudge], for the reason
     * written there: silence has to be chosen, never obtained by omission.
     *
     * The outcome is an [SessionOutcome.AbortedUnattended] whose key carries the degradation
     * (`degraded_weak_evidence`, `degraded_egress_not_at_anchor`, …) rather than a new arm of the
     * type. That arm already answers all four memberships correctly for this ending, and one of
     * them is load-bearing: `resolvesTheArrival = true` keeps the 15-minute safety net's backfill
     * from re-deciding the arrival the coordinator just refused, which would reintroduce the pin
     * through the other door [DET-BACKFILL-TAINT-001].
     */
    suspend fun closeDegradedPrompt(
        reasonKey: String,
        vehicleId: String?,
        location: GpsPoint,
        now: Long,
    ): EffectOutcome {
        PaparcarLogger.d(
            DIAG,
            "  ⊘ the prompt window closed on a DEGRADED question (reason=$reasonKey vehicle=$vehicleId) — " +
                "the refusal that asked it still stands, so no pin, no zone and no second question " +
                "[DET-A-DEGRADED-PROMPT-MUST-NOT-AUTO-CONFIRM-001]",
        )
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        diagnostics.emit { sid ->
            DetectionEvent.Decision(
                sid, now,
                outcome = "UNATTENDED_DEGRADED_PROMPT_DISCARD",
                pathLabel = DetectionPath.UnattendedTimeout.label,
                location = location,
                reason = reasonKey,
            )
        }
        return EffectOutcome(
            sessionOutcome = SessionOutcome.AbortedUnattended("degraded_$reasonKey"),
            endsSession = true,
        )
    }

    /**
     * [DET-SOLID-001] Every confirm condition holds but the evidence is too weak for a silent save —
     * an ENTER-only arm whose session never saw driving, falsifiable by a bus or a taxi. Ask instead:
     * a "Sí" flows through the user-confirm precedence and silence aborts at the response timeout.
     *
     * @return whether the prompt was newly posted, so the caller knows to move the prompt window to
     *   `notified(now)`. An already-visible prompt is left alone: re-posting would restart a
     *   response-timeout window the user is already inside.
     */
    suspend fun degradeToPrompt(
        alreadyPrompted: Boolean,
        pathLabel: String,
        // [DET-PROMPT-STATES-ITS-REASON-001] WHICH of the six causes degraded this confirm. Not
        // defaulted: every caller knows its own reason, and a default would quietly resurrect the
        // anonymous prompt that ticket exists to remove.
        reason: PromptReason,
        location: GpsPoint,
        // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] WHERE the question is about — the
        // witnessed car stop, which is not the same as [location], the fix that triggered the
        // degrade. Resolved by the dispatcher from the live state, for the same reason the
        // scoring stage resolves its own: one place, decided once.
        candidate: GpsPoint,
        now: Long,
    ): Boolean {
        PaparcarLogger.d(
            DIAG,
            "  ？ confirm degraded to user prompt ($pathLabel, reason=${reason.key}) " +
                "[DET-SOLID-001][DET-PROMPT-STATES-ITS-REASON-001]",
        )
        if (alreadyPrompted) return false
        val asked = activeVehicle()
        val vehicleName = asked.askedName()
        notificationPort.showParkingConfirmation(
            WEAK_EVIDENCE_PROMPT_SCORE,
            vehicleName,
            asked?.id,
            candidate,
            resolveAskedStreet(candidate),
        )
        // [DET-AR-FIRST-001 F4] The posting itself must be visible in parkdiag: this path bypasses
        // NotifyParkingConfirmation, and the 2026-07-10 19:19 session read as "prompt never shown"
        // in forensics when it HAD been posted right here.
        PaparcarLogger.d(
            DIAG,
            "  ▶ weak-evidence prompt notification POSTED (score=$WEAK_EVIDENCE_PROMPT_SCORE, " +
                "vehicle=$vehicleName) [DET-AR-FIRST-001]",
        )
        diagnostics.emit { sid ->
            DetectionEvent.Decision(
                sid, now, outcome = "CONFIRM_DEGRADED_PROMPT", pathLabel = pathLabel,
                location = location, reason = reason.key,
            )
        }
        return true
    }

    /** Put a confirmation prompt on screen. */
    suspend fun notifyPrompt(confidence: ParkingConfidence, at: GpsPoint) =
        notifyParkingConfirmation(confidence, at)

    /** Take the confirmation prompt off screen. */
    fun dismissPrompt() =
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)

    /** [DET-HOLD-BRANCHES-MUST-SPEAK-001] The lane's single door: every hold exit says what it did. */
    suspend fun logHold(
        action: HoldAction,
        heldMs: Long? = null,
        pathLabel: String? = null,
        location: GpsPoint? = null,
    ) = diagnostics.hold(action, nowMs(), heldMs, pathLabel, location)

    /**
     * El coche activo, UNA lectura. [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001]
     *
     * Antes aquí sólo se derivaba el nombre y el objeto se tiraba, así que los dos sitios que
     * publican la pregunta sabían cómo se llama el coche y no CUÁL es. Leerlo dos veces para sacar
     * las dos cosas tampoco valía: entre lectura y lectura el coche activo puede cambiar, y la
     * pregunta saldría con el nombre de uno y el id de otro.
     */
    private suspend fun activeVehicle() = runCatching {
        vehicleRepository.observeActiveVehicle().first()
    }.getOrNull()

    private fun Vehicle?.askedName(): String? =
        this?.let { it.displayName(fallback = "").takeIf { name -> name.isNotBlank() } }

    private suspend fun activeVehicleName(): String? = activeVehicle().askedName()

    private companion object {
        const val TAG = "DetectionEffectExecutor"
        const val DIAG = "PARKDIAG/Coord"

        /** Score shown on the prompt when an auto-confirm is degraded by the repark-plausibility
         *  guard — Medium-band so the copy asks rather than asserts. [DET-SOLID-001] */
        const val IMPLAUSIBLE_REPARK_PROMPT_SCORE = 0.6f

        /** Same band, for the weak-evidence degrade. [DET-SOLID-001] */
        const val WEAK_EVIDENCE_PROMPT_SCORE = 0.6f
    }
}
