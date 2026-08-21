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
        sustainedMotorBandMs: Long = 0L,
    ) = isHumanPoweredRide(
        vehicleType, bicycleRideAtMs, vehicleRideAtMs, nowMs,
        fastMotionStepEvents, fastMotionStepFixes, sustainedMotorBandMs,
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

    // ── [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] measured motor refutes every claim ──────────

    @Test
    fun should_notVeto_when_theSessionHeldASpeedNoBicycleSustains() {
        // Field 2026-08-20, Redmi session 1787242874932: 361 s held above 40 km/h, 131,4 km/h peak
        // at 4,6 m accuracy — and one late AR `ON_BICYCLE` stamp took the session anyway. No pin,
        // 102 minutes hung, and the car lost the geofence that would have caught the next trip.
        assertFalse(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = now - 60_000L,
                vehicleRideAtMs = null,
                nowMs = now,
                sustainedMotorBandMs = 361_000L,
            ),
            "the event nominates, the measurement decides — a label cannot outvote 40+ km/h held",
        )
    }

    @Test
    fun should_notVeto_when_measuredMotorContradictsAMeasuredCadenceLatch() {
        // The refutation covers the KINEMATIC source too, not just the AR stamp: whatever the
        // step counter thought it saw, muscle does not hold this band.
        assertFalse(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = null,
                vehicleRideAtMs = null,
                nowMs = now,
                fastMotionStepEvents = 40,
                fastMotionStepFixes = 9,
                sustainedMotorBandMs = 120_000L,
            ),
        )
    }

    @Test
    fun should_stillVeto_when_aRealBicycleOnlyTouchesTheMotorBandInBursts() {
        // The real rides of 2026-08-19 measured ZERO ms above 40 km/h on both phones (the Redmi's
        // summary said `vmax 40 km/h`, but its best CREDIBLE fix was 21,3 — the peak is a rumour).
        // A downhill sprint that brushes the band for a few seconds must change nothing.
        assertTrue(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = now - 60_000L,
                vehicleRideAtMs = null,
                nowMs = now,
                sustainedMotorBandMs = config.sustainedDriveProofMs - 1,
            ),
            "brushing the band is not holding it",
        )
    }

    @Test
    fun should_notVeto_when_arWitnessedGettingOutOfAVehicleAfterTheCyclingStamp() {
        // Nobody gets out of a vehicle they never got into. On a geofence-armed session the EXIT
        // is often the only IN_VEHICLE transition AR delivers — the 2026-08-20 session had two and
        // neither counted, because only ENTER was wired to supersede.
        assertFalse(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = now - 300_000L,
                vehicleRideAtMs = now - 60_000L, // the EXIT, stamped with its true transition time
                nowMs = now,
            ),
        )
    }
}
