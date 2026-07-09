package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * [DET-G-05][DET-SOLID-001] The pre-arm verifier that keeps a walking geofence-exit from
 * arming the coordinator as a confirmed departure (BUG-REPARK-WALK-001).
 */
class VerifyDepartureEvidenceUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val exitTimestamp = 1_000_000L

    private fun buildUseCase(bus: FakeDepartureEventBus = FakeDepartureEventBus()) =
        VerifyDepartureEvidenceUseCase(departureEventBus = bus, config = config)

    @Test
    fun `should verify by speed when at departure threshold with credible accuracy`() {
        val useCase = buildUseCase()

        val evidence = useCase(exitTimestamp, currentSpeedKmh = config.minimumDepartureSpeedKmh, currentAccuracyM = 20f)

        assertIs<ArmEvidence.VerifiedBySpeed>(evidence)
    }

    @Test
    fun `should not verify by speed from a degraded accuracy fix`() {
        // [DET-SOLID-001] A GPS spike while walking can fake departure speed — accuracy must be
        // credible for the sample to count as driving proof.
        val useCase = buildUseCase(FakeDepartureEventBus(initialTimestamp = null))

        val evidence = useCase(
            exitTimestamp,
            currentSpeedKmh = config.minimumDepartureSpeedKmh + 5f,
            currentAccuracyM = config.minGpsAccuracyForDriving + 50f,
        )

        assertIs<ArmEvidence.Unverified>(evidence)
    }

    @Test
    fun `should verify by vehicleEnter when displacement outruns pedestrian reach`() {
        // Short-hop repark (DET-G-04's motivating case): the user boarded the car — AR ENTER
        // recorded — the sampled fix misses driving speed, but the position is already further
        // from the car than legs could carry in the enter→exit minute. [DET-RIDE-PROOF-001]
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L)
        val useCase = buildUseCase(bus)

        val evidence = useCase(
            exitTimestamp,
            currentSpeedKmh = 4f,
            currentAccuracyM = 10f,
            distanceFromCarMeters = 700.0, // reach at 60 s ≈ 2.5×60 + fence 100 + acc 10 = 260 m
            fenceRadiusMeters = 100f,
        )

        assertIs<ArmEvidence.VerifiedByVehicleEnter>(evidence)
    }

    @Test
    fun `should not verify a vehicleEnter whose displacement is pedestrian reachable`() {
        // Field 2026-07-09 11:53 (Redmi): phantom IN_VEHICLE ENTER 14 s before a WALKING exit
        // at 127 m from the car — the old branch verified it, released the spot and seeded a
        // phantom park at the hairdresser's. Walkable displacement = nomination without proof.
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 14_000L)
        val useCase = buildUseCase(bus)

        val evidence = useCase(
            exitTimestamp,
            currentSpeedKmh = 0f,
            currentAccuracyM = 12f,
            distanceFromCarMeters = 127.0,
            fenceRadiusMeters = 108f,
        )

        assertIs<ArmEvidence.Unverified>(evidence)
    }

    @Test
    fun `should fail closed to unverified when vehicleEnter has no fix to corroborate`() {
        // No fix → no displacement → the ENTER cannot be verified. The arm still happens
        // (Unverified keeps the anti-walking guards on) and the worker upgrades it later.
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L)
        val useCase = buildUseCase(bus)

        val evidence = useCase(exitTimestamp, currentSpeedKmh = 4f, currentAccuracyM = 10f)

        assertIs<ArmEvidence.Unverified>(evidence)
    }

    @Test
    fun `should not verify when walking speed and no vehicleEnter`() {
        // BUG-REPARK-WALK-001 (2026-07-03 field trace): walking out of the geofence after a
        // real park — pedestrian speed, no AR ENTER. Must NOT arm as a confirmed departure.
        val useCase = buildUseCase(FakeDepartureEventBus(initialTimestamp = null))

        assertIs<ArmEvidence.Unverified>(useCase(exitTimestamp, currentSpeedKmh = 4f, currentAccuracyM = 10f))
    }

    @Test
    fun `should not verify when no signals at all`() {
        val useCase = buildUseCase(FakeDepartureEventBus(initialTimestamp = null))

        assertIs<ArmEvidence.Unverified>(useCase(exitTimestamp, currentSpeedKmh = null))
    }

    @Test
    fun `should not verify when vehicleEnter is older than the window`() {
        val bus = FakeDepartureEventBus(
            initialTimestamp = exitTimestamp - config.vehicleEnterWindowMs - 1L,
        )
        val useCase = buildUseCase(bus)

        assertIs<ArmEvidence.Unverified>(useCase(exitTimestamp, currentSpeedKmh = null))
    }

    @Test
    fun `should not verify when vehicleEnter predates the session`() {
        // [DET-SESSION-BIRTH-001] Field replay 2026-07-08 18:52 (Redmi): MIUI re-delivered the
        // INBOUND drive's ENTER (trueTime 17 min old) seconds after the park; the walking exit
        // 23 s later must arm UNVERIFIED, not seed hasEverReachedDrivingSpeed.
        val sessionStart = exitTimestamp - 23_000L
        val bus = FakeDepartureEventBus(initialTimestamp = sessionStart - 17 * 60_000L)
        val useCase = buildUseCase(bus)

        val evidence = useCase(exitTimestamp, currentSpeedKmh = 4f, currentAccuracyM = 10f, sessionStartMs = sessionStart)

        assertIs<ArmEvidence.Unverified>(evidence)
    }

    @Test
    fun `should not verify when vehicleEnter happened AFTER the exit`() {
        // [DET-SOLID-001] With TRUE transition times on the bus, an ENTER after the exit is a
        // vehicle boarded OUTSIDE the radius (bus/taxi after walking out) — never departure
        // evidence. The old abs() window accepted it (S5 hole).
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp + 60_000L)
        val useCase = buildUseCase(bus)

        assertIs<ArmEvidence.Unverified>(useCase(exitTimestamp, currentSpeedKmh = null))
    }
}
