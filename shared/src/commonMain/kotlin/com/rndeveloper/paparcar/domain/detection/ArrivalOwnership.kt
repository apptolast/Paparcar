package com.rndeveloper.paparcar.domain.detection

/**
 * [DET-ARRIVAL-HANDOFF-001] Who takes the NEW parking after the safety net dispatched a departure.
 *
 * A dispatched departure must end in exactly one of these three. NEVER neither — that orphans the
 * arrival: the evaluator detected the Oppo's return trip mid-drive (2026-07-08 20:41), cleared the
 * session, and nobody was listening when the user parked 5 min later.
 */
enum class ArrivalOwner {
    /** The coordinator follows the rest of the trip and captures the park at full quality. */
    LiveDetection,

    /** The 15-min net reconstructs a pin from the wake-up fix. Backstop only. */
    Backfill,

    /** Nobody can take it — ask the user. A notification beats silence. */
    UserPrompt,
}

/**
 * [DET-BACKFILL-MUST-NOT-PIN-A-MOVING-CAR-001] **Live detection MEASURES; the backfill GUESSES.
 * When both are on offer, the measurement wins — always.**
 *
 * This ordering used to be backwards: a bounded backfill pre-empted the handoff, and live detection
 * was only started when the net had nothing to place. That reads as "place whenever you can", which
 * is the opposite of the doctrine — *the event NOMINATES, only measured movement CONFIRMS.*
 *
 * Field 2026-08-30 21:27:34 (Oppo, Calle del Verdugo 24). The net woke 3 652 m from the car with the
 * user still driving, on a wake-up fix declaring `speed=0.0` while doing ~37 km/h. Every
 * `backfillBounded` clause passed — including the one that exists to ask *"is the car at rest
 * HERE?"*, because that clause reads the fix's declared speed field and the field lied. The pin
 * landed on the road, published a phantom space, and lived 52 s. Seven seconds after it was placed
 * the ARRIVAL_HANDOFF coordinator refuted the same fix from its own track — 134 m in 5 s while
 * reporting 0.0 m/s [DET-STOP-MUST-BE-STILL-IN-SPACE-001]. The measurement was already on its way;
 * the guess simply got there first.
 *
 * Ceding costs nothing when the handoff runs: that same live session confirmed the REAL park by
 * `steps+egress` at 21:34. And it needs no new calibration — no threshold, no second fix, no clock.
 *
 * ⚠️ **The backfill is not deleted.** It remains the backstop for the case that justified it: live
 * detection cannot start (background FGS-start denied on Android 12+/OEM), so there is nobody left
 * to measure. Accepted residual: in that branch a lying wake-up fix can still misplace a pin. It is
 * bounded by being the last option before the prompt — and by then the alternative is losing the
 * arrival entirely, which the asymmetric-failure doctrine ranks worse than a doubted pin.
 *
 * Predicted verbatim by [DET-BACKFILL-TAINT-001], which closed a different face of the same defect:
 * *"it landed right by luck (short hole); over a 2 km hole it lands 2 km wrong with the same
 * confidence."* On 2026-08-30 the hole was 101 minutes and 3 652 m.
 *
 * @param handoffStarted Live detection accepted the arrival (the FGS actually started).
 * @param departurePreconfirmed The trip already ENDED per the evaluator — the precondition for the
 *   net to reconstruct anything at all.
 * @param backfillBounded The evaluator judged the wake-up fix's position boundable
 *   ([com.rndeveloper.paparcar.domain.usecase.parking.SafetyNetAction.DispatchDeparture]).
 */
fun arrivalOwner(
    handoffStarted: Boolean,
    departurePreconfirmed: Boolean,
    backfillBounded: Boolean,
): ArrivalOwner = when {
    handoffStarted -> ArrivalOwner.LiveDetection
    departurePreconfirmed && backfillBounded -> ArrivalOwner.Backfill
    else -> ArrivalOwner.UserPrompt
}
