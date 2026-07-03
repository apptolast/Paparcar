package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-G-05] The pre-arm verifier that keeps a walking geofence-exit from arming the
 * coordinator as a confirmed departure (BUG-REPARK-WALK-001).
 */
class VerifyDepartureEvidenceUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val exitTimestamp = 1_000_000L

    private fun buildUseCase(bus: FakeDepartureEventBus = FakeDepartureEventBus()) =
        VerifyDepartureEvidenceUseCase(departureEventBus = bus, config = config)

    @Test
    fun `should verify when speed is at departure threshold even without vehicleEnter`() {
        val useCase = buildUseCase()

        assertTrue(useCase(exitTimestamp, currentSpeedKmh = config.minimumDepartureSpeedKmh))
    }

    @Test
    fun `should verify when vehicleEnter is within window even at walking speed`() {
        // Short-hop repark (DET-G-04's motivating case): the user boarded the car — AR ENTER
        // recorded — but the hop was too short for the sampled fix to catch driving speed.
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L)
        val useCase = buildUseCase(bus)

        assertTrue(useCase(exitTimestamp, currentSpeedKmh = 4f))
    }

    @Test
    fun `should not verify when walking speed and no vehicleEnter`() {
        // BUG-REPARK-WALK-001 (2026-07-03 field trace): walking out of the geofence after a
        // real park — pedestrian speed, no AR ENTER. Must NOT arm as a confirmed departure.
        val useCase = buildUseCase(FakeDepartureEventBus(initialTimestamp = null))

        assertFalse(useCase(exitTimestamp, currentSpeedKmh = 4f))
    }

    @Test
    fun `should not verify when no signals at all`() {
        val useCase = buildUseCase(FakeDepartureEventBus(initialTimestamp = null))

        assertFalse(useCase(exitTimestamp, currentSpeedKmh = null))
    }

    @Test
    fun `should not verify when vehicleEnter is older than the window`() {
        val bus = FakeDepartureEventBus(
            initialTimestamp = exitTimestamp - config.vehicleEnterWindowMs - 1L,
        )
        val useCase = buildUseCase(bus)

        assertFalse(useCase(exitTimestamp, currentSpeedKmh = null))
    }
}
