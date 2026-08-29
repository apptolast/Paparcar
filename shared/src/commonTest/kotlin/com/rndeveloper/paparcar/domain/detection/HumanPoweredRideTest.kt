package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.VehicleType
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
        sustainedMotorDisplacementRateMps: Float = 0f,
    ) = isHumanPoweredRide(
        vehicleType, bicycleRideAtMs, vehicleRideAtMs, nowMs,
        fastMotionStepEvents, fastMotionStepFixes, sustainedMotorBandMs,
        sustainedMotorDisplacementRateMps,
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

    // ── [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001] a witnessed boarding outranks the cadence ──

    /**
     * ⚠️ **BEHAVIOUR CHANGE, and this assertion is the record of it.** It used to assert `true` under
     * the name `should_stillVeto_when_aLaterBoardingSupersededArButCadenceWasMeasured`, with the
     * reasoning *"the AR supersession rule frees the AR stamp, not the measurement… known cost, the
     * direction asymmetric failure allows"*.
     *
     * Field 2026-08-26 priced that known cost and it was not a prompt: the Redmi's Valdés→Góndola
     * trip degraded, nobody answered in 15 minutes, the park was LOST. The cadence's own charter is
     * the short ride AR never classifies; when AR HAS witnessed a boarding and no cycling stamp
     * supersedes it, the classifier has answered and a podometer reading taken at 11-17 km/h does not
     * overrule it.
     */
    @Test
    fun should_notVeto_when_aWitnessedBoardingOutranksTheMeasuredCadence() {
        assertFalse(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = now - 600_000L,
                vehicleRideAtMs = now - 120_000L,
                nowMs = now,
                fastMotionStepEvents = 16,
                fastMotionStepFixes = 4,
            ),
            "AR witnessed the boarding after the cycling stamp; the cadence has no standing left",
        )
    }

    @Test
    fun should_notVeto_when_cadenceFiredOnACityDriveArHadAlreadyWitnessed() {
        // THE 2026-08-26 SESSION, in one line. Redmi WZB7oft…, Valdés 19 → Góndola 1 through the
        // centre of Cádiz: `IN_VEHICLE ENTER` stamped at its true transition 20:20:12, `pedal
        // cadence` at 20:22:11 off six fixes reading 11-17 km/h, and NOT ONE `ON_BICYCLE` label in
        // the whole window. Nothing here reaches 40 km/h — this is the "what if I had never left the
        // centre?" case, and it must confirm without any high-speed refutation at all.
        assertFalse(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = null,
                vehicleRideAtMs = now - 109_000L, // 1 min 49 s before the cadence, as measured
                nowMs = now,
                fastMotionStepEvents = 12,
                fastMotionStepFixes = 3,
            ),
            "a car is not a bicycle merely because a city street kept it under 18 km/h",
        )
    }

    @Test
    fun should_stillVeto_when_cyclingWasStampedAfterTheBoardingAndCadenceAgrees() {
        // Drove to the park, took the bike out of the boot, and the pedalling was MEASURED. The
        // arbitration is unchanged and it is still the last boarding that wins — this is the case
        // the change above must not swallow.
        assertTrue(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = now - 120_000L,
                vehicleRideAtMs = now - 600_000L,
                nowMs = now,
                fastMotionStepEvents = 16,
                fastMotionStepFixes = 4,
            ),
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

    // ── [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001] the motor measured as a RATE, not a clock ──

    @Test
    fun should_notVeto_when_theMotorWasProvenByDisplacementAcrossAStarvedStream() {
        // THE OTHER HALF of 2026-08-26. The band CLOCK read ~1 s of the 30 s it needs, because OEM
        // batching left the three in-band fixes 163 s and 200 s apart and `creditSpeedBand` credits
        // nothing beyond a 60 s gap — while the session had actually measured 26,2 m/s (94,3 km/h) at
        // 15,5 m of accuracy and run 640 m from the anchor at 24,6 m/s average. Note the clock is
        // handed its real starved value: the refutation must come from the rate alone.
        assertFalse(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = null,
                vehicleRideAtMs = null,
                nowMs = now,
                fastMotionStepEvents = 12,
                fastMotionStepFixes = 3,
                sustainedMotorBandMs = 1_060L,
                sustainedMotorDisplacementRateMps = 24.6f,
            ),
            "a hole in the stream cannot erase ground the vehicle provably covered",
        )
    }

    @Test
    fun should_notVeto_when_displacementRefutesAnArCyclingStampToo() {
        // Same precedence as the band clock: the refutation is measured, so it outranks the label
        // lane as well as the kinematic one. [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001]
        assertFalse(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = now - 60_000L,
                vehicleRideAtMs = null,
                nowMs = now,
                sustainedMotorDisplacementRateMps = config.motorProofSpeedMps,
            ),
        )
    }

    @Test
    fun should_stillVeto_when_theSustainedRateStaysUnderTheMotorBar() {
        // A fast cyclist averages well over the 5 m/s the departure floor needs, so the ONLY thing
        // separating this from the case above is the bar itself. Just under it changes nothing —
        // otherwise the descent to Los Toruños buys a silent pin.
        assertTrue(
            evaluate(
                VehicleType.CAR,
                bicycleRideAtMs = now - 60_000L,
                vehicleRideAtMs = null,
                nowMs = now,
                sustainedMotorDisplacementRateMps = config.motorProofSpeedMps - 0.1f,
            ),
            "brushing the bar is not clearing it",
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
