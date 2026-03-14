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
 * This quickly rules out traffic lights and short stops with a strong signal.
 *
 * **SLOW PATH** — used when no activity-exit event is available. It gates on a
 * minimum stopped duration ([ParkingDetectionConfig.slowPathGateMs]) and then
 * builds a score from time, speed, and GPS accuracy bonuses.
 *
 * @param config Thresholds and scoring weights. Can be overridden in tests or
 *               injected from remote configuration without touching business logic.
 */
class CalculateParkingConfidenceUseCase(private val config: ParkingDetectionConfig) {

    operator fun invoke(signals: ParkingSignals): ParkingConfidence {

        // FAST PATH: activityExit signal present + min stop time → traffic lights discarded.
        // Without STILL: base(0.40) + speed(0.15) + accuracy(0.05) = 0.60 → Medium → user confirmation.
        // With STILL:    base(0.40) + still(0.15) + speed(0.15) + accuracy(0.05) = 0.75 → High → auto-confirm.
        // STILL_ENTER fires after the car has been motionless for ~30-60 s (own-car parked),
        // but typically does NOT fire during a brief taxi/bus drop-off (< 60 s stop).
        if (signals.activityExit && signals.stoppedDurationMs >= config.fastPathMinStoppedMs) {
            var score = config.fastPathBaseScore
            if (signals.activityStill) score += config.fastPathStillBonus
            if (signals.speed < config.maxSpeedMps) score += config.fastPathSpeedBonus
            if (signals.gpsAccuracy < config.minGpsAccuracyMeters) score += config.fastPathAccuracyBonus
            return toConfidence(score)
        }

        // SLOW PATH: gate at slowPathGateMs — discards traffic lights (~30-60s) and brief holds
        if (signals.stoppedDurationMs < config.slowPathGateMs) return ParkingConfidence.NotYet

        var score = when {
            signals.stoppedDurationMs >= config.slowPath5MinMs -> config.slowPath5MinScore
            signals.stoppedDurationMs >= config.slowPath3MinMs -> config.slowPath3MinScore
            else -> config.slowPathBaseScore
        }
        if (signals.activityStill) score += config.stillBonus
        if (signals.speed < config.maxSpeedMps) score += config.speedBonus
        if (signals.gpsAccuracy < config.minGpsAccuracyMeters) score += config.accuracyBonus

        return toConfidence(score)
    }

    private fun toConfidence(score: Float): ParkingConfidence = when {
        score >= config.highConfidenceThreshold -> ParkingConfidence.High(score)
        score >= config.mediumConfidenceThreshold -> ParkingConfidence.Medium(score)
        else -> ParkingConfidence.Low
    }
}
