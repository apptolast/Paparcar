package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals

/**
 * Computes a [ParkingConfidence] level from the current sensor [ParkingSignals].
 *
 * **ONE score, no branches. [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001]**
 *
 * The scorer used to be two mutually exclusive paths — a "fast" one when Activity Recognition had
 * reported a vehicle exit, a "slow" one when it had not — and the fast path `return`ed before the
 * slow one was ever computed. The consequence was absurd once written down: **the same 5-minute
 * stop scored 0,80 without an AR vehicle exit and 0,65 with one**. The strongest evidence we own
 * that the user got out of the car LOWERED the confidence that they parked. And since High is the
 * only route into the CANDIDATE phase, an AR exit silently made that phase — and the
 * `vehicleExit + window + egress` confirm lane behind it — unreachable for the whole session.
 * Field 2026-08-20 is what that cost: a session that could not score, could not prompt and
 * therefore could not end, running 102 minutes across two trips and losing the park.
 *
 * So evidence is now **additive** and the shape of the function guarantees the property that was
 * violated: every term is ≥ 0, so adding a signal can never lower the result.
 *
 *  - **Rest tier** — how long the vehicle has been stopped. The dominant term: parking is mostly
 *    about not moving for a while.
 *  - **Bonuses** — near-zero speed, credible GPS accuracy, and an AR vehicle exit. Each one only
 *    ever adds.
 *  - **Gate** — below it, [ParkingConfidence.NotYet] and nothing is scored at all. This is where
 *    the AR exit still earns its keep: it SHORTENS the gate (`fastPathMinStoppedMs` instead of
 *    `slowPathGateMs`), so a brief hospital-entrance / drop-off stop reaches the prompt early, as
 *    it always did. Shortening a gate is monotone too — more evidence can only make the score
 *    arrive sooner, never later.
 *
 * **What did NOT change.** Low and Medium are handled identically by the coordinator, so only two
 * boundaries carry behaviour: NotYet (silence) and High (candidate phase). Silence is preserved
 * exactly — the gate rule is the old fast/slow split expressed as a threshold. High gains exactly
 * one case, the one this ticket exists for: a matured 5-minute rest that ALSO has an AR vehicle
 * exit now reaches it (0,70 + 0,15) instead of being capped at 0,65 forever. A 3-minute stop and a
 * brief drop-off still cannot reach High no matter what else is true — asserted by `require` in
 * [ParkingDetectionConfig], because that ceiling used to hold only by arithmetic accident and a
 * weight tweak could have turned the brief-stop lane into a silent auto-confirm.
 *
 * **Score range.** Clamped to `[0f, 1f]` before classification [FIX BUG-COORD-106]. With the
 * default config the maximum is 0.95 (5-min tier + all three bonuses) and the clamp is a no-op.
 *
 * @param config Thresholds and scoring weights. Can be overridden in tests or
 *               injected from remote configuration without touching business logic.
 */
class CalculateParkingConfidenceUseCase(private val config: ParkingDetectionConfig) {

    operator fun invoke(signals: ParkingSignals): ParkingConfidence {
        // The gate: an AR vehicle exit shortens it, nothing lengthens it.
        val gateMs = if (signals.activityExit) config.fastPathMinStoppedMs else config.slowPathGateMs
        if (signals.stoppedDurationMs < gateMs) return ParkingConfidence.NotYet

        var score = when {
            signals.stoppedDurationMs >= config.slowPath5MinMs -> config.slowPath5MinScore
            signals.stoppedDurationMs >= config.slowPath3MinMs -> config.slowPath3MinScore
            signals.stoppedDurationMs >= config.slowPathGateMs -> config.slowPathBaseScore
            // Only reachable through the shortened gate, i.e. with an AR vehicle exit in hand.
            else -> config.briefRestScore
        }
        if (signals.speed < config.maxSpeedMps) score += config.speedBonus
        if (signals.gpsAccuracy < config.minGpsAccuracyMeters) score += config.accuracyBonus
        if (signals.activityExit) score += config.activityExitBonus

        return toConfidence(score)
    }

    // [FIX BUG-COORD-106: clamp to [0, 1] before classification.]
    // The sum above can in theory be pushed above 1.0 by future config drift — the data-class
    // `init` block in ParkingDetectionConfig validates the ceilings that carry behaviour, not the
    // absolute total. Clamping here makes downstream consumers safe.
    private fun toConfidence(score: Float): ParkingConfidence {
        val clamped = score.coerceIn(0f, 1f)
        return when {
            clamped >= config.highConfidenceThreshold -> ParkingConfidence.High(clamped)
            clamped >= config.mediumConfidenceThreshold -> ParkingConfidence.Medium(clamped)
            else -> ParkingConfidence.Low
        }
    }
}
