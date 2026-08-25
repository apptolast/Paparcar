package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * [DET-ASSERTION-OUTRANKS-INFERENCE-001] May this session move the pin the USER put there?
 *
 * A PREDICATE, not a verdict: it names no `detectionPath`, produces no `outcome` and nothing the
 * user reads — it is an input several verdicts consume (the honest close, the candidate-phase
 * confirm, the repark guard). So it lives here with detection's other pure policy functions
 * ([SentryWakeCooldown], [SentryLifecycleDecision], [VehicleFenceOwnershipPolicy],
 * [isHumanPoweredRide]) rather than as an injected `Evaluate…UseCase`. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * **The rule.** A pin stamped `reliabilityUserConfirmed` was placed or confirmed BY THE USER — the
 * strongest statement about where the car is that this system can hold. Everything else in the
 * detection stack is inference: step budgets, egress displacement, kinematic freezes, AR labels.
 * **An inference must never depose an assertion.** Only MEASURED driving may release such a pin,
 * because measured driving is not an inference — it is the one thing that proves the car itself
 * moved.
 *
 * **Why it had to become shared.** The rule was already written, correctly, and tested — inside a
 * single `if` in `EvaluateHonestCloseUseCase` ([DET-WALK-FLOOR-001], field 2026-07-26 Glorieta: a
 * hand-placed pin 12 minutes old, sitting exactly on the car, released by a ONE-step budget margin
 * and re-planted 100 m away at the walker's position). Being written in one place, it guarded one
 * of the four lanes that can relocate a pin. Field 2026-08-24 20:51, Oppo/Calle Fragua, went
 * through a different one:
 *
 * ```
 * 20:48:33  DECISION PROMPT_SHOWN  low_medium(timeout=94993ms)  conf=0.55   ← real 99 km/h drive ended
 * 20:48:43  DECISION CONFIRMED     pathLabel=user  conf=1.0
 *           → pin a9709e31  36.613605,-6.2089333  acc 1.25 m  rel 1.0      ← the user's own word
 * 20:50:38  ARM:SIGNIFICANT_MOTION (sentry-wake geof=a9709e31)
 * 20:50:39  session opens: 25 fixes, ONE above the driving bar (5.33 m/s), 57 steps walking away
 * 20:51:22  DECISION CONFIRM_DEGRADED_PROMPT  pathLabel=steps+egress  reason=weak_evidence
 * 20:51:36  user answers "Sí" again → pin 195e72f1, 14 m away, and a9709e31.isActive = false
 * ```
 *
 * The honest close held the line all evening on that very phone (`stayed silent (user_asserted_pin
 * …)`, twelve times). The candidate phase never asked the question, and the repark guard in
 * `ConfirmParkingUseCase` was bypassed by design at reliability 1.0 — which is exactly what a "Sí"
 * carries.
 *
 * **Asking is not free.** The asymmetric-failure doctrine says *when in doubt, ASK* — but there was
 * no doubt here: the user had answered this same question, at this same place, under three minutes
 * earlier. A second prompt cannot resolve uncertainty, it manufactures it. And the answer is
 * ambiguous by construction: the user affirms the FACT ("yes, I'm parked"), while the app reads it
 * as "put the pin HERE" — at a position the MACHINE chose, which on 2026-08-24 was the spot the walk
 * started from, not where the car stood. Hence the verdict this predicate feeds is a REJECTION, not
 * a prompt: discarding the candidate leaves the good pin exactly where the user put it, and the
 * session stays alive — if it later measures real driving, [sessionSawDriving] flips and this
 * predicate stands down on its own.
 *
 * @param pinReliability The active session's `detectionReliability`, or null when unknown (legacy
 *   rows) — null never asserts, so it never blocks.
 * @param pinLocation Where that pin sits; its `timestamp` is when it was asserted.
 * @param candidate The position this session wants to pin instead.
 * @param nowMs Wall clock.
 * @param sessionSawDriving Did THIS session's own stream witness sustained driving? The single
 *   escape hatch: a real re-park is preceded by a real drive, and measured movement outranks every
 *   inference in this file.
 * @param userConfirmedReliability `ParkingDetectionConfig.reliabilityUserConfirmed` — config
 *   invariants keep every automatic path strictly below it, so `>=` means "the user said so".
 * @param freshWindowMs `reparkPlausibilityWindowMs`, or **null for no bound**. Past it the
 *   assertion has aged out of the window where a walk-away could be mistaken for a re-park, and the
 *   normal ladders take over. The honest close passes null on purpose: a session aborting with no
 *   measured driving has produced NOTHING that could move a car, at any age.
 * @param radiusMeters `reparkPlausibilityRadiusMeters`, or **null for no bound**. Beyond it the
 *   candidate is somewhere the user could not have walked to, so it is a genuine new park rather
 *   than a relocation of this one.
 */
fun assertionBlocksRelocation(
    pinReliability: Float?,
    pinLocation: GpsPoint,
    candidate: GpsPoint,
    nowMs: Long,
    sessionSawDriving: Boolean,
    userConfirmedReliability: Float,
    freshWindowMs: Long? = null,
    radiusMeters: Float? = null,
): Boolean {
    // Measured driving is not an inference — it is the one witness allowed to overrule the user.
    if (sessionSawDriving) return false
    // Nothing asserted → nothing to protect. Null (legacy row) is deliberately NOT an assertion.
    if ((pinReliability ?: 0f) < userConfirmedReliability) return false
    // A future-dated pin (clock skew) yields a negative age, which is inside every window — the
    // conservative side, because it keeps the user's pin.
    val ageMs = nowMs - pinLocation.timestamp
    if (freshWindowMs != null && ageMs > freshWindowMs) return false
    if (radiusMeters == null) return true
    val distanceMeters = haversineMeters(
        pinLocation.latitude, pinLocation.longitude,
        candidate.latitude, candidate.longitude,
    )
    return distanceMeters < radiusMeters
}
