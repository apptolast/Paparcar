package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals

/**
 * Computes a [ParkingConfidence] level from the current sensor [ParkingSignals].
 *
 * The algorithm has two paths:
 *
 * **FAST PATH** — triggered when an activity-exit event is present AND the vehicle
 * has been stopped for at least [ParkingDetectionConfig.fastPathMinStoppedMs].
 * Tops out at Medium (0.65): with STILL removed as a signal [DET-SOLID-001][C1] the
 * fast path never auto-confirms — it opens the user prompt, which is the intended
 * behaviour for brief hospital-entrance / drop-off stops. [BUG-DETECT-310503]
 *
 * **SLOW PATH** — used when no activity-exit event is available. It gates on a
 * minimum stopped duration ([ParkingDetectionConfig.slowPathGateMs]) and then
 * builds a score from time, speed, and GPS accuracy bonuses. High (≥ 0.75) requires
 * the 5-min tier + the speed bonus — the only route to the CANDIDATE phase.
 *
 * **Mutual exclusion.** Fast and slow paths are mutually exclusive: the fast path
 * returns inside the early `if`. Only one branch contributes to a single scoring call.
 *
 * **Score range.** Output scores are clamped to `[0f, 1f]` before classification
 * [FIX BUG-COORD-106] so a future config tuning that pushes the sum above 1.0 cannot
 * leak an invalid probability into [ParkingConfidence.Medium]/[ParkingConfidence.High].
 * With the default config the current maximum is 0.90 (slow path, all bonuses) and
 * the clamp is a no-op.
 *
 * @param config Thresholds and scoring weights. Can be overridden in tests or
 *               injected from remote configuration without touching business logic.
 */
class CalculateParkingConfidenceUseCase(private val config: ParkingDetectionConfig) {

    operator fun invoke(signals: ParkingSignals): ParkingConfidence {

        // FAST PATH: activityExit signal present + min stop time → traffic lights discarded.
        // Tops out at Medium (base 0.50 + speed 0.15) → opens the prompt, never auto-confirms.
        // [BUG-DETECT-310503][DET-SOLID-001 C1: the STILL-gated accuracy bonus was unreachable]
        if (signals.activityExit && signals.stoppedDurationMs >= config.fastPathMinStoppedMs) {
            var score = config.fastPathBaseScore
            if (signals.speed < config.maxSpeedMps) score += config.fastPathSpeedBonus
            return toConfidence(score)
        }

        // SLOW PATH: gate at slowPathGateMs — discards traffic lights (~30-60s) and brief holds.
        if (signals.stoppedDurationMs < config.slowPathGateMs) return ParkingConfidence.NotYet

        var score = when {
            signals.stoppedDurationMs >= config.slowPath5MinMs -> config.slowPath5MinScore
            signals.stoppedDurationMs >= config.slowPath3MinMs -> config.slowPath3MinScore
            else -> config.slowPathBaseScore
        }
        if (signals.speed < config.maxSpeedMps) score += config.speedBonus
        if (signals.gpsAccuracy < config.minGpsAccuracyMeters) score += config.accuracyBonus

        return toConfidence(score)
    }

    // [FIX BUG-COORD-106: clamp to [0, 1] before classification.]
    // Both branches above can in theory be pushed above 1.0 by future config drift —
    // the data-class `init` block in ParkingDetectionConfig validates thresholds but
    // not their summed score. Clamping here makes downstream consumers safe.
    private fun toConfidence(score: Float): ParkingConfidence {
        val clamped = score.coerceIn(0f, 1f)
        return when {
            clamped >= config.highConfidenceThreshold -> ParkingConfidence.High(clamped)
            clamped >= config.mediumConfidenceThreshold -> ParkingConfidence.Medium(clamped)
            else -> ParkingConfidence.Low
        }
    }
}
