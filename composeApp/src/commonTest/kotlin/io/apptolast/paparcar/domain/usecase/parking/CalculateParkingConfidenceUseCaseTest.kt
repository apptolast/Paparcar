package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CalculateParkingConfidenceUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = CalculateParkingConfidenceUseCase(config)

    // ── Fast path ─────────────────────────────────────────────────────────────

    @Test
    fun `should return High when activityExit and speed and accuracy bonuses all present`() {
        val signals = ParkingSignals(
            activityExit = true,
            stoppedDurationMs = config.fastPathMinStoppedMs,
            speed = 0.1f,                        // below maxSpeedMps → bonus
            gpsAccuracy = 10f,                   // below minGpsAccuracyMeters → bonus
        )
        // 0.50 + 0.15 + 0.10 = 0.75 → High
        assertIs<ParkingConfidence.High>(useCase(signals))
    }

    @Test
    fun `should return Medium when activityExit present but no bonuses`() {
        val signals = ParkingSignals(
            activityExit = true,
            stoppedDurationMs = config.fastPathMinStoppedMs,
            speed = 1.0f,                        // above maxSpeedMps → no bonus
            gpsAccuracy = 20f,                   // above minGpsAccuracyMeters → no bonus
        )
        // 0.50 → Low (below mediumConfidenceThreshold=0.55)
        assertIs<ParkingConfidence.Low>(useCase(signals))
    }

    @Test
    fun `should return Medium when activityExit present with only speed bonus`() {
        val signals = ParkingSignals(
            activityExit = true,
            stoppedDurationMs = config.fastPathMinStoppedMs,
            speed = 0.1f,                        // bonus
            gpsAccuracy = 20f,                   // no bonus
        )
        // 0.50 + 0.15 = 0.65 → Medium
        assertIs<ParkingConfidence.Medium>(useCase(signals))
    }

    @Test
    fun `should not enter fast path when stoppedDuration is below minimum`() {
        val signals = ParkingSignals(
            activityExit = true,
            stoppedDurationMs = config.fastPathMinStoppedMs - 1,
            speed = 0.1f,
            gpsAccuracy = 10f,
        )
        // Fast path skipped → slow path → below slowPathGateMs → NotYet
        assertIs<ParkingConfidence.NotYet>(useCase(signals))
    }

    // ── Slow path — gating ────────────────────────────────────────────────────

    @Test
    fun `should return NotYet when stopped duration is below slow path gate`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPathGateMs - 1,
            speed = 0f,
            gpsAccuracy = 5f,
            activityStill = true,
        )
        assertIs<ParkingConfidence.NotYet>(useCase(signals))
    }

    // ── Slow path — time tiers ────────────────────────────────────────────────

    @Test
    fun `should return Low when stopped at gate with no bonuses`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPathGateMs,
            speed = 1.0f,
            gpsAccuracy = 20f,
            activityStill = false,
        )
        // 0.40 → Low (below 0.55)
        assertIs<ParkingConfidence.Low>(useCase(signals))
    }

    @Test
    fun `should return Medium when stopped at gate with all bonuses`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPathGateMs,
            speed = 0.1f,
            gpsAccuracy = 10f,
            activityStill = true,
        )
        // 0.40 + 0.10 (still) + 0.05 (speed) + 0.05 (accuracy) = 0.60 → Medium
        assertIs<ParkingConfidence.Medium>(useCase(signals))
    }

    @Test
    fun `should return Low when stopped 3 minutes with no bonuses`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPath3MinMs,
            speed = 1.0f,
            gpsAccuracy = 20f,
            activityStill = false,
        )
        // slowPath3MinScore=0.45 < mediumConfidenceThreshold=0.55 → Low
        assertIs<ParkingConfidence.Low>(useCase(signals))
    }

    @Test
    fun `should return Medium when stopped 3 minutes with all bonuses`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPath3MinMs,
            speed = 0.1f,
            gpsAccuracy = 10f,
            activityStill = true,
        )
        // 0.45 + 0.10 + 0.05 + 0.05 = 0.65 → Medium (below highConfidenceThreshold=0.75)
        assertIs<ParkingConfidence.Medium>(useCase(signals))
    }

    @Test
    fun `should return Medium when stopped 5 minutes with no bonuses`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPath5MinMs,
            speed = 1.0f,
            gpsAccuracy = 20f,
            activityStill = false,
        )
        // 0.70 → Medium (below High threshold of 0.75)
        assertIs<ParkingConfidence.Medium>(useCase(signals))
    }

    @Test
    fun `should return High when stopped 5 minutes with speed bonus`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPath5MinMs,
            speed = 0.1f,
            gpsAccuracy = 20f,
            activityStill = false,
        )
        // 0.70 + 0.05 = 0.75 → High
        assertIs<ParkingConfidence.High>(useCase(signals))
    }

    // ── Still bonus ───────────────────────────────────────────────────────────

    @Test
    fun `should add still bonus only in slow path`() {
        val withStill = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPath5MinMs,
            speed = 1.0f,
            gpsAccuracy = 20f,
            activityStill = true,
        )
        val withoutStill = withStill.copy(activityStill = false)

        val scoreWith = (useCase(withStill) as? ParkingConfidence.Medium)?.score
            ?: (useCase(withStill) as? ParkingConfidence.High)?.score
        val scoreWithout = (useCase(withoutStill) as? ParkingConfidence.Medium)?.score
            ?: (useCase(withoutStill) as? ParkingConfidence.High)?.score

        assertEquals(config.stillBonus, (scoreWith ?: 0f) - (scoreWithout ?: 0f), absoluteTolerance = 0.001f)
    }
}
