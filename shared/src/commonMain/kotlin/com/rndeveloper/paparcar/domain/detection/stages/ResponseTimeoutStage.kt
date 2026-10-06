package com.rndeveloper.paparcar.domain.detection.stages

import com.rndeveloper.paparcar.domain.detection.physics.SavedParkingShape
import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.detection.state.anchorRestMs
import com.rndeveloper.paparcar.domain.detection.state.refinedParkLocation
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateUnattendedParkingSaveUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.UnattendedParkingSave

/**
 * [DET-RECONCILE-001] **The user was asked and never answered — SAVE, do not discard.**
 *
 * The prompt only ever shows after a real trip, a real stop and a vehicle-exit signal, so the
 * parking almost certainly happened and the only missing piece is a human tap. Throwing the session
 * away costs the user their car — field 2026-07-06, Redmi: a real parking lost to a notification
 * nobody noticed — while saving it wrong costs one correction tap. The asymmetry is the whole
 * argument, and it is why this stage exists at all.
 *
 * ⛔ **And that premise has one narrow exception**
 * [DET-A-DEGRADED-PROMPT-MUST-NOT-AUTO-CONFIRM-001]. The sentence above was written when the prompt
 * had ONE producer. It has two: a confirm that is REFUSED degrades into the same question, through
 * the same `Notified` phase, and for that one "only a human tap is missing" is precisely what the
 * app decided was not true. `ConfirmationPhase.degradedFrom` now rides into `UnattendedSaveInput`
 * so the verdict can tell them apart.
 *
 * What it takes away is only the EXACT pin — `DiscardDegraded` replaces `SaveExact` and nothing
 * else. Every zone still saves, degraded question or not, because *a bounded doubt costs precision,
 * never the park*: four replays of real traces (`house_mirage_001`, `parafarmacia_2908`,
 * `gondola_2608`, `an_inherited_drive…`) reach this stage with a degraded question and a measured
 * drive, and losing them would be a far worse bug than the one being fixed.
 *
 * ## What this stage is NOT
 *
 * It is not the seven-way precedence. That used to live inline here — no-drive → unpinned →
 * egress-mismatch → gap → walk-entered → vehicular-egress → exact save — and it is now ONE pure
 * verdict in `EvaluateUnattendedParkingSaveUseCase`, so the rule *a bounded doubt costs precision,
 * never the park* exists in a single place instead of being re-derived per branch. Re-deriving it is
 * how the Redmi's fully measured 25.6-minute drive ended with no pin at all (field 2026-08-16).
 * [DET-WALK-ENTERED-ANCHOR-ZONE-001]
 *
 * This stage keeps only what is left: the timeout gate, and turning the verdict into effects.
 *
 * ## The rest clock is the CAR's
 *
 * [DET-CAR-REST-CLOCK-001] The sustained rest the save licence needs belongs to the vehicle, and its
 * witness is the pinned anchor's own stop — opened when the car halted, cleared only by re-measured
 * driving. The phone's stop tracker is the wrong clock: after egress it follows the WALKER, and
 * indoor GPS noise resets it with no accuracy gate (field 2026-08-18, Góndola: ~15 s accumulated
 * across 15 minutes of anchored rest).
 */
class ResponseTimeoutStage(
    private val evaluateUnattendedParkingSave: EvaluateUnattendedParkingSaveUseCase,
) : SessionStage {

    override val stage = DetectionStage.RESPONSE_TIMEOUT

    override fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        stoppedDurationMs: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict {
        val promptShownAt = state.confirmation.promptShownAt ?: return StageVerdict.Skip()
        val waited = now - promptShownAt
        if (waited <= config.confirmationResponseTimeoutMs) return StageVerdict.Skip()

        val rest = state.anchorRestMs(now, config)
        val verdict = evaluateUnattendedParkingSave(state.unattendedSaveInput(fix, now, rest, config))
        var notes = notes(state.unattendedVerdictTrace(now, waited, stoppedDurationMs, verdict, config))
        val vehicleId = state.session.attributedVehicleId

        val effect = when (verdict) {
            is UnattendedParkingSave.SaveZone -> DetectionEffect.SaveZone(
                reasonKey = verdict.reason.key,
                center = verdict.center,
                doubtMeters = verdict.doubtMeters,
                vehicleId = vehicleId,
                at = fix,
            )

            // [DET-A-TRAIN-IS-NOT-A-CAR-001] The only branch that produces no effect at all. The
            // session still ends below with every other one — what it does not do is speak.
            is UnattendedParkingSave.Discard -> null

            // [DET-A-DEGRADED-PROMPT-MUST-NOT-AUTO-CONFIRM-001] The question outlived its window and
            // the refusal that produced it still stands — so the EXACT pin it would have earned is
            // withheld (every zone verdict above still saves). Unlike the branch above this one
            // DOES emit an effect: there is a prompt on screen to take down, and an outcome to
            // stamp so the 15-minute safety net does not re-decide the arrival just resolved.
            is UnattendedParkingSave.DiscardDegraded -> DetectionEffect.CloseDegradedPrompt(
                reasonKey = verdict.promptReason.key,
                vehicleId = vehicleId,
                at = fix,
            )

            is UnattendedParkingSave.Ask -> DetectionEffect.AskUser(
                reasonKey = verdict.reason.key,
                vehicleId = vehicleId,
                at = fix,
                distanceMeters = verdict.distanceMeters,
            )

            UnattendedParkingSave.SaveExact -> {
                val pin = state.refinedParkLocation(fix, config)
                // Prepended: the refinement line was logged from inside the helper, so it printed
                // ahead of the verdict trace even though the trace was composed first.
                notes = listOfNotNull(pin.note) + notes
                DetectionEffect.SaveUnattended(
                    shape = SavedParkingShape.ExactPin(
                        location = pin.location,
                        reliability = config.reliabilityUnattendedSave,
                    ),
                    vehicleId = vehicleId,
                )
            }
        }

        // Every branch ends the session: the window is over either way, and the only non-looping
        // exit is to stop asking. [BUG-STUCK-SESSION]
        // [DET-A-TRAIN-IS-NOT-A-CAR-001] `listOfNotNull`, because a verdict may now be silent —
        // ending the session IS the action. The ending itself is unchanged and unconditional, which
        // is what keeps BUG-STUCK-SESSION closed for the new branch too.
        return StageVerdict.Handled(
            newState = state,
            effects = listOfNotNull(effect),
            stopsIteration = true,
            notes = notes,
        )
    }
}
