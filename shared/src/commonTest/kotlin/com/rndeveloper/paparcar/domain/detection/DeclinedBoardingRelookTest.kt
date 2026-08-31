package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [DET-A-DECLINED-ARM-IS-NOT-SILENCE-001] */
class DeclinedBoardingRelookTest {

    private val config = ParkingDetectionConfig()

    /** The Oppo's park at Av. Blas Infante, 2026-08-30 20:40. */
    private val parkedCar = UserParking(
        id = "785dabe3",
        userId = "user-1",
        vehicleId = "v-1",
        location = GpsPoint(36.596455, -6.240445, accuracy = 7f, timestamp = 1_000L, speed = 0f),
        isActive = true,
        geofenceId = "785dabe3",
        sizeCategory = VehicleSize.MEDIUM_SUV,
    )

    private fun fixNearCar(speedMps: Float, accuracy: Float = 5f) =
        GpsPoint(36.596455, -6.240445, accuracy = accuracy, timestamp = 2_000L, speed = speedMps)

    /** ~3,6 km away — where the Oppo actually was when the safety net finally noticed. */
    private fun fixAwayFromCar(speedMps: Float, accuracy: Float = 5f) =
        GpsPoint(36.627378, -6.224430, accuracy = accuracy, timestamp = 2_000L, speed = speedMps)

    // ── The case this exists for ──────────────────────────────────────────────────────────────

    /**
     * The re-look 90 s after the declined boarding, in the shape the field trace would have had:
     * away from the car and at credible driving speed. Arming here buys ~5 minutes of the trip that
     * were lost on 2026-08-30.
     */
    @Test
    fun should_arm_when_theRelookMeasuresDrivingAwayFromTheCar() {
        assertTrue(shouldArmAfterDeclinedBoarding(fixAwayFromCar(speedMps = 11f), parkedCar, config))
    }

    // ── What must NOT arm ─────────────────────────────────────────────────────────────────────

    /**
     * ⛔ The load-bearing negative: the ACTUAL boarding fix of 2026-08-30 21:20:42 —
     * `speed=0.22708045m/s acc=8.0m`, 143 m from the car. Still walking, or barely pulling away.
     *
     * This is why the re-look is delayed rather than evaluated at the ENTER: a speed test at that
     * instant answers "no" and is just as blind as the silence it replaces. If this ever goes green,
     * the delay has stopped being load-bearing and the whole design should be re-read.
     */
    @Test
    fun should_notArm_when_judgedAtTheBoardingFixItself() {
        val theRealBoardingFix = GpsPoint(
            latitude = 36.5985133,
            longitude = -6.2424778,
            accuracy = 8.0f,
            timestamp = 2_000L,
            speed = 0.22708045f,
        )
        assertFalse(shouldArmAfterDeclinedBoarding(theRealBoardingFix, parkedCar, config))
    }

    /** Walking pace away from the car is what the decline already covered. */
    @Test
    fun should_notArm_when_theRelookMeasuresWalking() {
        assertFalse(shouldArmAfterDeclinedBoarding(fixAwayFromCar(speedMps = 1.2f), parkedCar, config))
    }

    /**
     * Driving speed AT the parked car is manoeuvring or GPS noise — the case the fence absorbs.
     * Without this the re-look would re-open the false-ENTER churn the decline just avoided.
     */
    @Test
    fun should_notArm_when_drivingSpeedIsMeasuredInsideTheCarsOwnFence() {
        assertFalse(shouldArmAfterDeclinedBoarding(fixNearCar(speedMps = 11f), parkedCar, config))
    }

    /**
     * A phantom speed at 120 m accuracy is the Redmi's GPS all day long — it pisó la banda ambigua
     * 285 times on 2026-08-30. The same credibility predicate the rest of the detector uses keeps
     * those out, so no new calibration enters with this ticket.
     */
    @Test
    fun should_notArm_when_theSpeedIsNotCredibleForItsAccuracy() {
        assertFalse(shouldArmAfterDeclinedBoarding(fixAwayFromCar(speedMps = 11f, accuracy = 120f), parkedCar, config))
    }

    @Test
    fun should_notArm_when_thereIsNoFix() {
        assertFalse(shouldArmAfterDeclinedBoarding(null, parkedCar, config))
    }

    @Test
    fun should_notArm_when_thereIsNoParkedSession() {
        assertFalse(shouldArmAfterDeclinedBoarding(fixAwayFromCar(speedMps = 11f), null, config))
    }

    // ── The arm it produces may never confirm in silence ──────────────────────────────────────

    /**
     * AR fires on any vehicle, so this arm cannot mean "you got into YOUR car". The evidence it
     * carries must not authorise a silent save, or a bus ride becomes a phantom pin — which is the
     * very thing the decline protects against.
     */
    @Test
    fun should_neverLetTheBoardedAwayArmSaveAParkInSilence() {
        val evidence = ArmEvidence.BoardedAwayFromCar
        assertTrue(evidence.driveAuthorization == DriveAuthorization.None)
        assertFalse(evidence.isVerifiedDeparture)
        assertFalse(evidence.confirmsSilentlyWithoutMeasuredDrive)
    }
}
