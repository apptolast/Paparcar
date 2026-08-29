package com.rndeveloper.paparcar.domain.detection.stages

import com.rndeveloper.paparcar.domain.detection.physics.SessionOutcome
import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig

/**
 * [BUG-FALSE-ENTER-WALKING] **Feet before wheels: the arm was wrong.** Activity Recognition fires an
 * `IN_VEHICLE` ENTER while the user is walking — the classic case is having just got out of the car
 * carrying bags, at a brisk pace — and the session that opens has no car in it at all.
 *
 * Counted steps BEFORE any driving speed are the refutation. Without this the same session would run
 * the full no-movement budget (~4 min) with the foreground-service notification glued on, and could
 * repeat every time AR misfires again.
 *
 * ## Why it outranks the user's own tap
 *
 * `should_abort_the_false_enter_even_when_the_user_already_said_yes` (P0.1) pins this adjacency, and
 * the reason is worth stating plainly because it is the one place the user is deliberately overruled:
 * **a tap cannot make a trip have happened.** The user answering "sí, he aparcado" to a prompt from a
 * session that never had a car is answering about a different session than the one that asked. Saving
 * it would plant a pin where the person is standing, which is the exact failure mode the whole
 * asymmetric-failure doctrine exists to avoid — better a false negative than a phantom spot.
 *
 * That is not the user being distrusted. It is the session admitting it had nothing to ask about.
 *
 * ## The one thing this stage must not become
 *
 * It reads `driveAuthorized`, the NOMINATION — so a session whose arm SEEDED the authorization on
 * trust never reaches here, by design. That is not an oversight: the seed means a departure worker
 * said the drive already happened, and steps after a real drive are the egress walk, not a
 * refutation. If a dismissed departure later retracts that seed [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001],
 * this guard re-arms with the steps already counted — which is precisely what the 2026-08-22 session
 * needed, having counted nine pedestrian steps indoors before the verdict landed.
 */
class FalseEnterAbortStage : SessionStage {

    override val stage = DetectionStage.FALSE_ENTER_ABORT

    override fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        stoppedDurationMs: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict {
        if (state.session.driveAuthorized) return StageVerdict.Skip()
        if (state.egress.stepCount < config.falseEnterAbortSteps) return StageVerdict.Skip()

        return StageVerdict.Handled(
            newState = state,
            effects = listOf(DetectionEffect.EndSession(SessionOutcome.AbortedFalseEnter.serialized)),
            stopsIteration = true,
            notes = notes(
                "  ⊘ false-ENTER abort — ${state.egress.stepCount} steps before driving speed " +
                    "[BUG-FALSE-ENTER-WALKING]",
            ),
        )
    }
}
