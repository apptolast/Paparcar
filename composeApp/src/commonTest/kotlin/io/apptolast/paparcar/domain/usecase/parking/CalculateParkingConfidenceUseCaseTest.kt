package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [DET-SOLID-001][C1] Rewritten after the STILL purge: the old suite exercised branches gated on
 * `activityStill = true` — a signal hardwired `false` in production for months — so its "High via
 * fast path" coverage was fake. Every case below is REACHABLE in the real system.
 */
class CalculateParkingConfidenceUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = CalculateParkingConfidenceUseCase(config)

    // ── Fast path (activityExit + min stop) — tops out at Medium, never High ──

    @Test
    fun `fast path with speed bonus returns Medium - opens the prompt, never auto-confirms`() {
        val signals = ParkingSignals(
            activityExit = true,
            stoppedDurationMs = config.fastPathMinStoppedMs,
            speed = 0.1f,     // below maxSpeedMps → bonus
            gpsAccuracy = 10f, // good GPS — irrelevant: no accuracy bonus exists in the fast path
        )
        // 0.50 + 0.15 = 0.65 → Medium: the fast path's ceiling by design [BUG-DETECT-310503]
        assertIs<ParkingConfidence.Medium>(useCase(signals))
    }

    @Test
    fun `fast path without bonuses returns Low`() {
        val signals = ParkingSignals(
            activityExit = true,
            stoppedDurationMs = config.fastPathMinStoppedMs,
            speed = 1.0f,      // above maxSpeedMps → no bonus
            gpsAccuracy = 20f,
        )
        // 0.50 → Low (below mediumConfidenceThreshold=0.55)
        assertIs<ParkingConfidence.Low>(useCase(signals))
    }

    @Test
    fun `fast path is skipped when stoppedDuration is below minimum`() {
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
    fun `slow path returns NotYet below the gate`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPathGateMs - 1,
            speed = 0f,
            gpsAccuracy = 5f,
        )
        assertIs<ParkingConfidence.NotYet>(useCase(signals))
    }

    // ── Slow path — time tiers ────────────────────────────────────────────────

    @Test
    fun `slow path at the gate with no bonuses returns Low`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPathGateMs,
            speed = 1.0f,
            gpsAccuracy = 20f,
        )
        // 0.40 → Low
        assertIs<ParkingConfidence.Low>(useCase(signals))
    }

    @Test
    fun `slow path at the gate with all bonuses returns Low`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPathGateMs,
            speed = 0.1f,
            gpsAccuracy = 10f,
        )
        // 0.40 + 0.05 (speed) + 0.05 (accuracy) = 0.50 → Low (still below 0.55)
        assertIs<ParkingConfidence.Low>(useCase(signals))
    }

    @Test
    fun `slow path at 3 minutes with all bonuses returns Medium`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPath3MinMs,
            speed = 0.1f,
            gpsAccuracy = 10f,
        )
        // 0.45 + 0.05 + 0.05 = 0.55 → Medium (never High — auto-Candidate needs ≥ 5 min)
        assertIs<ParkingConfidence.Medium>(useCase(signals))
    }

    @Test
    fun `slow path at 5 minutes with no bonuses returns Medium`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPath5MinMs,
            speed = 1.0f,
            gpsAccuracy = 20f,
        )
        // 0.70 → Medium (below High threshold of 0.75)
        assertIs<ParkingConfidence.Medium>(useCase(signals))
    }

    @Test
    fun `slow path at 5 minutes with speed bonus returns High - the only route to Candidate`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.slowPath5MinMs,
            speed = 0.1f,
            gpsAccuracy = 20f,
        )
        // 0.70 + 0.05 = 0.75 → High: ≥5 min continuously stopped + near-zero speed is the single
        // path to the CANDIDATE phase in the real system.
        assertIs<ParkingConfidence.High>(useCase(signals))
    }

    // ── [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] evidence is additive ──────

    @Test
    fun `adding an AR vehicle exit can never lower the confidence - the property, not a case`() {
        // The bug this ticket exists for, stated as an invariant instead of an anecdote: the same
        // stop scored 0,80 without an AR exit and 0,65 with one, and High is the only route into
        // the CANDIDATE phase. Swept across every tier and both bonus states, because the old bug
        // was invisible in exactly the corner nobody had a case for.
        val rank = { c: ParkingConfidence ->
            when (c) {
                is ParkingConfidence.NotYet -> 0
                is ParkingConfidence.Low -> 1
                is ParkingConfidence.Medium -> 2
                is ParkingConfidence.High -> 3
            }
        }
        val durations = listOf(
            0L, config.fastPathMinStoppedMs, config.slowPathGateMs, config.slowPath3MinMs,
            config.slowPath5MinMs, config.slowPath5MinMs * 10,
        )
        for (stopped in durations) {
            for (speed in listOf(0.1f, 1.0f)) {
                for (accuracy in listOf(5f, 40f)) {
                    val without = useCase(ParkingSignals(speed, stopped, accuracy, activityExit = false))
                    val with = useCase(ParkingSignals(speed, stopped, accuracy, activityExit = true))
                    assertTrue(
                        rank(with) >= rank(without),
                        "an AR vehicle exit LOWERED confidence at stopped=${stopped}ms " +
                            "speed=$speed acc=$accuracy: $without → $with",
                    )
                }
            }
        }
    }

    @Test
    fun `a matured rest with an AR vehicle exit reaches High - the field bug of 2026-08-20`() {
        // Pre-fix this was capped at Medium 0,65 forever, so the candidate phase never opened, the
        // prompt never fired and the session could not end: 102 minutes, two trips, no pin.
        val signals = ParkingSignals(
            activityExit = true,
            stoppedDurationMs = config.slowPath5MinMs,
            speed = 1.0f,        // GPS drift indoors: no speed bonus
            gpsAccuracy = 40f,   // parked between buildings: no accuracy bonus
        )
        // 0.70 + 0.15 = 0.85 → High on the rest and the exit alone, no 0,05 bonus required.
        assertIs<ParkingConfidence.High>(useCase(signals))
    }

    @Test
    fun `a brief stop with an AR vehicle exit still cannot auto-confirm`() {
        // The behaviour the old fast path protected, kept: a hospital-entrance stop opens the
        // prompt early and must never reach the candidate phase on its own.
        val signals = ParkingSignals(
            activityExit = true,
            stoppedDurationMs = config.fastPathMinStoppedMs,
            speed = 0.1f,
            gpsAccuracy = 5f,
        )
        // 0.35 + 0.05 + 0.05 + 0.15 = 0.60 → Medium, and `require` in the config keeps it there.
        assertIs<ParkingConfidence.Medium>(useCase(signals))
    }

    @Test
    fun `silence is preserved - a short stop without an AR exit still scores nothing`() {
        val signals = ParkingSignals(
            activityExit = false,
            stoppedDurationMs = config.fastPathMinStoppedMs,
            speed = 0.1f,
            gpsAccuracy = 5f,
        )
        assertIs<ParkingConfidence.NotYet>(useCase(signals))
    }
}
