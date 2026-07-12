package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * First test suite of the Bluetooth path (audit A3): the pure decision core extracted from the
 * Android detector. The two phantom-spot holes it locks shut [DET-AUDIT-002 T2]:
 * a BT drop while DRIVING must never pin a park, and the walk-away distance must be covered
 * on FOOT — the car's own displacement satisfied it before.
 */
class EvaluateBtParkUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = EvaluateBtParkUseCase(config)

    private fun fix(
        meters: Double = 0.0,
        speedMps: Float = 0f,
        accuracy: Float = 10f,
    ) = GpsPoint(
        latitude = BASE_LAT + meters / METERS_PER_DEGREE_LAT,
        longitude = BASE_LON,
        accuracy = accuracy,
        timestamp = 0L,
        speed = speedMps,
    )

    // ── Candidate fix: pin-grade AND stationary, or nothing ──────────────────

    @Test
    fun should_acceptCandidate_when_stationaryAndPinGrade() {
        assertEquals(BtParkVerdict.CandidateAccepted, useCase.evaluateCandidateFix(fix(speedMps = 0.3f, accuracy = 8f)))
    }

    @Test
    fun should_abortCandidate_when_credibleDrivingFix() {
        // The audit-A2 hole: BT drop at 30 km/h used to pin a "park" on the road.
        assertEquals(BtParkVerdict.DrivingAbort, useCase.evaluateCandidateFix(fix(speedMps = 8f, accuracy = 10f)))
    }

    @Test
    fun should_keepSampling_when_fixAccuracyDegraded() {
        // Degraded fixes decide NOTHING — neither candidate nor abort (noise either way).
        assertEquals(BtParkVerdict.KeepWaiting, useCase.evaluateCandidateFix(fix(speedMps = 0f, accuracy = 80f)))
    }

    @Test
    fun should_keepSampling_when_apparentDrivingSpeedWithDegradedAccuracy() {
        // A 120 m-accuracy Doppler spike must not abort a real park (fail-negative both ways).
        assertEquals(BtParkVerdict.KeepWaiting, useCase.evaluateCandidateFix(fix(speedMps = 8f, accuracy = 120f)))
    }

    @Test
    fun should_keepSampling_when_movingAtWalkingPace() {
        // Settling into the spot / user already stepping out: not stopped, not driving.
        assertEquals(BtParkVerdict.KeepWaiting, useCase.evaluateCandidateFix(fix(speedMps = 1.4f, accuracy = 10f)))
    }

    // ── Walk-away: the displacement must be WALKED ────────────────────────────

    @Test
    fun should_confirm_when_walkedDistanceAtPedestrianRate() {
        // 35 m in 25 s = 1.4 m/s — a person on foot.
        val verdict = useCase.evaluateWalkAway(fix(), fix(meters = 35.0, speedMps = 1.3f), elapsedMs = 25_000L)
        assertEquals(BtParkVerdict.WalkAwayConfirmed, verdict)
    }

    @Test
    fun should_abortWalkAway_when_currentFixIsCredibleDriving() {
        // The car (with the phone in it) drove on after a BT drop at a light.
        val verdict = useCase.evaluateWalkAway(fix(), fix(meters = 40.0, speedMps = 9f), elapsedMs = 6_000L)
        assertEquals(BtParkVerdict.DrivingAbort, verdict)
    }

    @Test
    fun should_abortWalkAway_when_displacementOutrunsPedestrianRate() {
        // 200 m in 10 s with speed=0 fixes (sparse cadence hid the drive): 20 m/s is wheels.
        val verdict = useCase.evaluateWalkAway(fix(), fix(meters = 200.0, speedMps = 0f), elapsedMs = 10_000L)
        assertEquals(BtParkVerdict.DrivingAbort, verdict)
    }

    @Test
    fun should_abortWalkAway_when_thresholdCoveredInstantly() {
        // elapsed <= 0 with the distance already covered = teleport/GPS jump — never confirm.
        val verdict = useCase.evaluateWalkAway(fix(), fix(meters = 35.0, speedMps = 0f), elapsedMs = 0L)
        assertEquals(BtParkVerdict.DrivingAbort, verdict)
    }

    @Test
    fun should_keepWaiting_when_underTheWalkThreshold() {
        val verdict = useCase.evaluateWalkAway(fix(), fix(meters = 15.0, speedMps = 1.2f), elapsedMs = 12_000L)
        assertEquals(BtParkVerdict.KeepWaiting, verdict)
    }

    @Test
    fun should_keepWaiting_when_currentWalkFixDegraded() {
        // A 100 m-accuracy fix can fake 30 m of displacement by noise alone.
        val verdict = useCase.evaluateWalkAway(fix(), fix(meters = 35.0, speedMps = 1.2f, accuracy = 100f), elapsedMs = 25_000L)
        assertEquals(BtParkVerdict.KeepWaiting, verdict)
    }

    private companion object {
        const val BASE_LAT = 36.6024
        const val BASE_LON = -6.2766
        const val METERS_PER_DEGREE_LAT = 111_320.0
    }
}
