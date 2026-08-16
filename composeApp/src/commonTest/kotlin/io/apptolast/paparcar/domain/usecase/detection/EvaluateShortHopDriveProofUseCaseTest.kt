package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvaluateShortHopDriveProofUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = EvaluateShortHopDriveProofUseCase(config)

    /** The pin the car left. */
    private val pin = GpsPoint(36.6119, -6.2805, accuracy = 8f, timestamp = 0L, speed = 0f)

    /** ~900 m north of [pin] — the field hop (2026-08-14 22:56). 0.0081° lat ≈ 900 m. */
    private fun fixAway(accuracy: Float = 10f, degreesNorth: Double = 0.0081) =
        GpsPoint(pin.latitude + degreesNorth, pin.longitude, accuracy, 0L, 0f)

    private fun proof(
        anchor: GpsPoint? = pin,
        fix: GpsPoint = fixAway(),
        verified: Boolean = true,
        fenceRadius: Float = 80f,
        elapsedMs: Long = 3 * 60_000L,
        run: Int = config.shortHopProofFixes,
    ) = useCase(anchor, fix, verified, fenceRadius, elapsedMs, run)

    @Test
    fun `should prove the drive when the car ended far from its pin faster than legs allow`() {
        // Field 2026-08-14 22:56 (Oppo): verified exit, ~900 m in ~3 min, peak 30 km/h — the
        // speed-window proof never fired (`drive 3/303`) and the park was lost.
        assertTrue(proof(), "900 m in 3 min from the pin is measured driving [DET-SHORT-HOP-PROOF-001]")
    }

    @Test
    fun `should not prove anything without a pin to measure from`() {
        assertFalse(proof(anchor = null), "no origin pin → no displacement reference")
    }

    @Test
    fun `should not prove the drive until the car has proven it left the pin`() {
        // Leaving the pin NOMINATES: with no departure proof at all — neither verified arm
        // evidence, nor a worker upgrade, nor the stream's own measurement
        // [DET-UNVERIFIED-ARM-DRIVE-PROOF-001] — the displacement alone (a long walk, a bus, a
        // passenger ride) must not unlock the drive statistic.
        assertFalse(proof(verified = false))
    }

    @Test
    fun `should not prove the drive when the distance is walkable in the elapsed time`() {
        // Same 900 m but the session has been running 16 min — legs cover that comfortably, so the
        // displacement proves nothing on its own.
        assertFalse(proof(elapsedMs = 16 * 60_000L))
    }

    @Test
    fun `should not prove the drive from a single qualifying fix`() {
        // A cache teleport can produce one far fix; it cannot produce a run of them.
        assertFalse(proof(run = 1))
    }

    @Test
    fun `should not prove the drive from a degraded fix`() {
        val degraded = fixAway(accuracy = config.minGpsAccuracyForDriving + 1f)
        assertFalse(proof(fix = degraded), "a fix too imprecise to trust measures nothing")
    }

    @Test
    fun `should not prove the drive from indoor drift next to the pin`() {
        // The Doppler-mirage class (field 2026-07-27): the phone never left its own pin, whatever
        // speed the chipset claimed. ~55 m of drift.
        val drift = fixAway(degreesNorth = 0.0005)
        assertFalse(proof(fix = drift), "drift within metres of the pin can never prove a drive")
    }

    @Test
    fun `should not prove the drive just below the displacement floor`() {
        // ~330 m — real movement, but under the floor: the proof stays conservative (asymmetric
        // failure), and the speed-based proof still covers ordinary drives.
        val justUnder = fixAway(degreesNorth = 0.003)
        assertFalse(proof(fix = justUnder))
    }

    @Test
    fun `should keep the per-fix geometry test independent of the arm evidence`() {
        // qualifies() feeds the consecutive-run counter, so it must judge geometry only — the
        // verified-arm requirement belongs to the verdict.
        assertTrue(useCase.qualifies(pin, fixAway(), fenceRadiusMeters = 80f, elapsedSinceArmMs = 3 * 60_000L))
    }
}
