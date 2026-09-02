package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.usecase.parking.SafetyNetAction

/**
 * [DET-EXPLAINED-RIDE-ASKS-NO-OTHER-CAR-001] Which still-parked prompts of THIS safety-net tick are
 * already answered by a departure dispatched in the SAME tick?
 *
 * A PREDICATE, not a verdict: it produces no `detectionPath` and decides nothing per session — it
 * reads the tick's already-decided actions across sessions. So it lives here with the rest of
 * detection's pure policy functions ([HumanPoweredRide], `SentryWakeCooldown`,
 * `VehicleFenceOwnershipPolicy`…), not as an injected `Evaluate…UseCase`.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * **Why it cannot live in the evaluator.** `evaluateSafetyNetCheck` is pure and per-session: by
 * construction it cannot know what the other sessions of the same tick resolved. The cross-session
 * fact only exists in the worker's loop — the same place `anyPromptActive` already accumulates.
 * (Ruled on when this ticket was crossed with the redesign: no single-session piece absorbs it,
 * `REDESIGN-DETECTION-SYSTEM.md` §9.3.)
 *
 * **Why it has to exist at all.** Field 2026-08-27 12:29:18 (Oppo, fisio): two sessions parked 30 m
 * apart, the user drives one car away. Thirteen milliseconds apart, the SAME 2 km displacement of
 * the SAME body got two opposite readings — `e1cb2b34` dispatched the departure backed by trip
 * proof and 4 trusted steps, while `a786c135` sent a real notification asking "still parked?" about
 * a car untouched for six days. Asymmetric failure worked (no pin was planted); what was wrong is
 * the QUESTION — a prompt whose answer the same tick already holds trains the user to ignore
 * prompts, and the prompt is the instrument the whole asymmetric-failure doctrine leans on.
 *
 * **Scope, deliberately narrow.** Only a `preconfirmed` dispatch mutes: there the trip is already
 * OVER and proven (step budget / AR boarding / exit∧enter / pedestrian physics). A live dispatch
 * (`preconfirmed = false`) is itself still awaiting the departure worker's speed re-check — an
 * unverified exit must not silence a question that might turn out to be the right one. And the mute
 * silences only [SafetyNetAction.PromptStillParked] actions of OTHER sessions; the arrival-owner
 * fallback prompt a dispatched session may raise for ITSELF asks a different question and is
 * untouched.
 *
 * When the body left in a car, "the other cars are still parked" is the NORMAL conclusion of this
 * tick, not a doubt.
 *
 * @return the geofence ids of the tick's still-parked prompts that must stay silent; empty when no
 *   preconfirmed departure was dispatched this tick.
 */
fun stillParkedPromptsExplainedByDeparture(tickActions: List<SafetyNetAction>): Set<String> {
    val departureExplained = tickActions.any {
        it is SafetyNetAction.DispatchDeparture && it.preconfirmed
    }
    if (!departureExplained) return emptySet()
    return tickActions
        .filterIsInstance<SafetyNetAction.PromptStillParked>()
        .mapTo(mutableSetOf()) { it.geofenceId }
}
