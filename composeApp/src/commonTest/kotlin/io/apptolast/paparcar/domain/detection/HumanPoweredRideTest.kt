package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.VehicleType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [DET-BIKE-NOT-A-CAR-001] "What are you on right now?" — the axis the vehicle profile cannot answer.
 *  Folded from an `Evaluate…UseCase` class into a pure policy function [DET-VERDICT-NOT-PREDICATE-001]. */
class HumanPoweredRideTest {

    private val config = ParkingDetectionConfig()

    private fun evaluate(
        vehicleType: VehicleType?,
        bicycleRideAtMs: Long?,
        vehicleRideAtMs: Long?,
        nowMs: Long,
        fastMotionStepEvents: Int = 0,
        fastMotionStepFixes: Int = 0,
    ) = isHumanPoweredRide(
        vehicleType, bicycleRideAtMs, vehicleRideAtMs, nowMs,
        fastMotionStepEvents, fastMotionStepFixes,
        config = config,
    )

    private val now = 10_000_000L

    @Test
    fun should_vetoTheRide_when_arSawCyclingAndTheProfileSaysCar() {
        // Field 2026-08-16 11:08Z (Samsung, session 1786878499475): a Mercedes profile, a bicycle
        // under the rider, 38 km/h, and a pin planted 4,8 km from the untouched car.
        assertTrue(
            evaluate(VehicleType.CAR, bicycleRideAtMs = now - 60_000L, vehicleRideAtMs = null, nowMs = now),
            "the profile says what you own, not what you are riding",
        )
    }

    @Test
    fun should_notVeto_when_arNeverReportedCycling() {
        assertFalse(evaluate(VehicleType.CAR, bicycleRideAtMs = null, vehicleRideAtMs = null, nowMs = now))
    }

    @Test
    fun should_notVeto_when_aLaterBoardingSupersededTheRide() {
        // Cycling to the station and then driving is a trip made BY CAR.
        assertFalse(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = now - 600_000L,
                vehicleRideAtMs = now - 120_000L,
                nowMs = now,
            )
        )
    }

    @Test
    fun should_stillVeto_when_theBoardingCameBeforeTheRide() {
        // Drove to the park, then took the bike out of the boot: the LAST boarding wins.
        assertTrue(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = now - 120_000L,
                vehicleRideAtMs = now - 600_000L,
                nowMs = now,
            )
        )
    }

    @Test
    fun should_notVeto_when_theCyclingEvidenceIsStale() {
        // This morning's ride must not silence this evening's drive.
        assertFalse(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = now - config.humanPoweredRideMemoryMs - 1,
                vehicleRideAtMs = null,
                nowMs = now,
            )
        )
    }

    @Test
    fun should_vetoOnProfileAlone_when_theVehicleIsRegisteredHumanPowered() {
        // [DET-SOLID-001][C2] unchanged: the profile answer stands with no AR involved at all.
        assertTrue(evaluate(VehicleType.BIKE, bicycleRideAtMs = null, vehicleRideAtMs = null, nowMs = now))
        assertTrue(evaluate(VehicleType.SCOOTER, bicycleRideAtMs = null, vehicleRideAtMs = null, nowMs = now))
    }

    @Test
    fun should_notVeto_when_theProfileIsAMotorcycle() {
        // A motorcycle is a real motor vehicle with its own geofence — it keeps auto-confirm.
        assertFalse(evaluate(VehicleType.MOTORCYCLE, bicycleRideAtMs = null, vehicleRideAtMs = null, nowMs = now))
    }

    // ── Pedal cadence — the kinematic second source [DET-MOTOR-PROOF-001] ──────────────────────

    @Test
    fun should_veto_when_pedalCadenceSpokeAndArStayedSilent() {
        // Field 2026-08-18 20:32 (Oppo, session 1787077943062): a 6-minute ride produced ZERO AR
        // events — and 16-20 steps concurrent with fixes at 3,3-4,1 m/s. Feet in rhythm while the
        // position outruns any walk is pedalling, whatever AR failed to say.
        assertTrue(
            evaluate(
                VehicleType.CAR, bicycleRideAtMs = null, vehicleRideAtMs = null, nowMs = now,
                fastMotionStepEvents = 16, fastMotionStepFixes = 6,
            ),
            "the session's own stream measured the pedalling; AR silence must not undo it",
        )
    }

    @Test
    fun should_notVeto_when_cadenceStepsStayAtBurstScale() {
        // A car's phantom steps arrive as bursts of 1-3 (pothole, pocket bounce) — under threshold.
        assertFalse(
            evaluate(
                VehicleType.CAR, bicycleRideAtMs = null, vehicleRideAtMs = null, nowMs = now,
                fastMotionStepEvents = config.pedalCadenceMinStepEvents - 1, fastMotionStepFixes = 6,
            )
        )
    }

    @Test
    fun should_notVeto_when_allCadenceStepsLandedOnASingleFix() {
        // One fix's burst can be one pothole; a rhythm needs the same signature on distinct fixes.
        assertFalse(
            evaluate(
                VehicleType.CAR, bicycleRideAtMs = null, vehicleRideAtMs = null, nowMs = now,
                fastMotionStepEvents = 20, fastMotionStepFixes = 1,
            )
        )
    }

    @Test
    fun should_stillVeto_when_aLaterBoardingSupersededArButCadenceWasMeasured() {
        // The AR supersession rule frees the AR stamp, not the measurement: cadence is this
        // session's own stream. Known cost (bike→car in one session degrades to a prompt) — the
        // direction asymmetric failure allows.
        assertTrue(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = now - 600_000L,
                vehicleRideAtMs = now - 120_000L,
                nowMs = now,
                fastMotionStepEvents = 16,
                fastMotionStepFixes = 4,
            )
        )
    }
}
