package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvaluateMeasuredDepartureUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = EvaluateMeasuredDepartureUseCase(config)

    /** The pin the car left — home, field 2026-08-15. */
    private val pin = GpsPoint(36.60811, -6.27771, accuracy = 8f, timestamp = 0L, speed = 0f)

    /** A fix [degreesNorth] from [pin] at [speedMps]. 0.0098° lat ≈ 1.09 km (the field distance). */
    private fun fix(
        degreesNorth: Double = 0.0098,
        speedMps: Float = 7.1f, // 25.6 km/h — the field fix at 21:26:30Z
        accuracy: Float = 8.9f,
    ) = GpsPoint(pin.latitude + degreesNorth, pin.longitude, accuracy, 0L, speedMps)

    private fun departed(
        anchor: GpsPoint? = pin,
        fix: GpsPoint = fix(),
        fenceRadius: Float = 80f,
        elapsedMs: Long = 25_000L, // the field arm-to-fix gap
    ) = useCase(anchor, fix, fenceRadius, elapsedMs)

    @Test
    fun `should prove the departure when the stream measures driving speed unwalkably far from the pin`() {
        // Field 2026-08-15 21:26 (Redmi, session 1786821963745): MIUI never delivered the EXIT, a
        // sentry-wake armed the session `self_observed`, and this fix — 25.6 km/h at 8.9 m accuracy,
        // 1.09 km from home, 25 s after arming — proved nothing. Two parks were lost behind it.
        assertTrue(departed(), "the same fix a punctual EXIT would have called VerifiedBySpeed")
    }

    @Test
    fun `should not prove the departure without a pin to measure from`() {
        assertFalse(departed(anchor = null), "no origin pin → no departure to measure")
    }

    @Test
    fun `should not prove the departure from indoor drift beside the pin`() {
        // The Doppler-mirage class (field 2026-07-27): the chipset claimed 45 m/s at a credible
        // 5 m accuracy while the phone sat indoors at its own pin. Anchoring to the PIN is what
        // makes it impossible — ~55 m of drift explains itself.
        val mirage = fix(degreesNorth = 0.0005, speedMps = 45f, accuracy = 5f)
        assertFalse(departed(fix = mirage), "a phone that never left its pin cannot have departed")
    }

    @Test
    fun `should not prove the departure from a walking-speed fix`() {
        // Below the departure speed the pre-arm verifier uses: walking out of the radius is what
        // every user does after parking, and it is not a departure.
        assertFalse(departed(fix = fix(speedMps = 1.4f)))
    }

    @Test
    fun `should not prove the departure from a degraded fix`() {
        // The field session's own 28.8 km/h opener carried 56.2 m of accuracy — above the driving
        // credibility gate, so it measures nothing. A degraded fix can fake speed while walking.
        val degraded = fix(speedMps = 8f, accuracy = config.minGpsAccuracyForDriving + 1f)
        assertFalse(departed(fix = degraded))
    }

    @Test
    fun `should not prove the departure while still inside the fence it left`() {
        // ~11 m out with a 80 m fence: the car has not left anything yet, whatever the Doppler says.
        assertFalse(departed(fix = fix(degreesNorth = 0.0001)))
    }

    @Test
    fun `should not prove the departure when legs already explain the distance`() {
        // A bus boarded 200 m from the car: by the time it reaches driving speed, the walk to the
        // stop has been long enough that pedestrian reach covers the displacement. This is the
        // bound that ties the drive to the user's OWN car.
        assertFalse(
            departed(fix = fix(degreesNorth = 0.0018), elapsedMs = 4 * 60_000L),
            "200 m in 4 min is a walk, not a departure",
        )
    }

    @Test
    fun `should not prove the departure from a negative elapsed time`() {
        // Clock skew between the arm stamp and the fix must fail closed, never open.
        assertFalse(departed(elapsedMs = -1L))
    }
}
