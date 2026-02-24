package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingSignals

class CalculateParkingConfidenceUseCase {

    operator fun invoke(signals: ParkingSignals): ParkingConfidence {

        // FAST PATH: activityExit signal present + 30s stopped → semáforos descartados
        if (signals.activityExit && signals.stoppedDurationMs >= 30_000L) {
            var score = 0.45f // activityExit base
            if (signals.speed < 0.3f) score += 0.15f
            if (signals.gpsAccuracy < 15f) score += 0.10f
            return when {
                score >= 0.75f -> ParkingConfidence.High(score)
                score >= 0.55f -> ParkingConfidence.Medium(score)
                else -> ParkingConfidence.Low
            }
        }

        // SLOW PATH: gate at 90s — descarta semáforos (~30-60s) y retenciones breves
        if (signals.stoppedDurationMs < 90_000L) return ParkingConfidence.NotYet

        var score = when {
            signals.stoppedDurationMs >= 300_000L -> 0.70f // 5 min → casi certeza
            signals.stoppedDurationMs >= 180_000L -> 0.55f // 3 min → probable
            else -> 0.40f                                   // 1.5 min → posible
        }
        if (signals.activityStill) score += 0.10f
        if (signals.speed < 0.3f) score += 0.05f
        if (signals.gpsAccuracy < 15f) score += 0.05f

        return when {
            score >= 0.75f -> ParkingConfidence.High(score)
            score >= 0.55f -> ParkingConfidence.Medium(score)
            else -> ParkingConfidence.Low
        }
    }
}
