package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * Verdicts of the Bluetooth park decision. [DET-AUDIT-002 T2/T3]
 */
sealed interface BtParkVerdict {
    /** Nothing decidable from this fix — keep sampling. */
    data object KeepWaiting : BtParkVerdict

    /** Credible driving movement observed: the BT drop happened MID-DRIVE (head-unit battery cut,
     *  interference) — the car is still moving, there is no parking. Abort, save nothing. */
    data object DrivingAbort : BtParkVerdict

    /** Stationary, pin-grade fix — accept as the parked-car candidate position. */
    data object CandidateAccepted : BtParkVerdict

    /** The user provably WALKED away from the candidate — the park is real, confirm at the
     *  candidate fix. */
    data object WalkAwayConfirmed : BtParkVerdict
}

/**
 * Pure decision core of the Bluetooth detection path. [DET-AUDIT-002 T2/T3]
 *
 * Extracted from [the Android `BluetoothParkingDetector`] so the deterministic-path rules are
 * unit-testable in commonTest and reusable on iOS — the platform detector keeps only the
 * plumbing (debounce, sampling loop, timeouts, telemetry).
 *
 * The two audit holes this closes (2026-07-04 findings A2, both PHANTOM-SPOT class):
 *  - **Candidate gate**: the old detector accepted the first fix with good ACCURACY, never
 *    checking speed. A BT drop while driving pinned a "park" on the road, and the car's own
 *    displacement satisfied the walk-away check → phantom park → phantom community spot.
 *    Now a candidate must be pin-grade AND stationary; a credible driving fix aborts outright.
 *  - **Walk-away gate**: distance alone cannot tell the walker from the car (the coordinator
 *    learned this as BUG-WALK-DEPART-001's mirror image). The displacement must be at pedestrian
 *    rate; covering it faster than [ParkingDetectionConfig.maxPedestrianSpeedMps] means wheels,
 *    not feet — abort.
 *
 * Asymmetric-error rule as everywhere: every ambiguous reading is a false NEGATIVE (KeepWaiting
 * or abort) — the BT tier's authority ("deterministic, 0.95") must be earned, never assumed.
 * Degraded-accuracy fixes can neither confirm nor abort: they are noise either way.
 */
class EvaluateBtParkUseCase(private val config: ParkingDetectionConfig) {

    /** Classify one sampled fix while hunting for the parked-car candidate position. */
    fun evaluateCandidateFix(fix: GpsPoint): BtParkVerdict = when {
        config.isCredibleDrivingSpeed(fix.speed * KMH_PER_MPS, fix.accuracy) ->
            BtParkVerdict.DrivingAbort
        fix.accuracy <= config.minGpsAccuracyForDriving &&
            fix.speed < config.stoppedSpeedThresholdMps ->
            BtParkVerdict.CandidateAccepted
        else -> BtParkVerdict.KeepWaiting
    }

    /**
     * Classify one fix of the walk-away phase.
     *
     * @param elapsedMs wall-clock ms since the walk-away watch started — the base for the
     *   pedestrian-rate check. `<= 0` (first fix raced the clock, or a position teleport) is
     *   treated as non-pedestrian: physically a jump, so it must not confirm.
     */
    fun evaluateWalkAway(candidate: GpsPoint, current: GpsPoint, elapsedMs: Long): BtParkVerdict {
        if (config.isCredibleDrivingSpeed(current.speed * KMH_PER_MPS, current.accuracy)) {
            return BtParkVerdict.DrivingAbort
        }
        // A degraded fix can fake a 30 m displacement by noise alone — never decide on it.
        if (current.accuracy > config.minGpsAccuracyForDriving) return BtParkVerdict.KeepWaiting

        val distanceMeters = haversineMeters(
            candidate.latitude, candidate.longitude,
            current.latitude, current.longitude,
        )
        if (distanceMeters < config.btWalkAwayDistanceMeters) return BtParkVerdict.KeepWaiting

        val averageSpeedMps =
            if (elapsedMs > 0) distanceMeters / (elapsedMs / 1000.0) else Double.MAX_VALUE
        if (averageSpeedMps > config.maxPedestrianSpeedMps) return BtParkVerdict.DrivingAbort

        return BtParkVerdict.WalkAwayConfirmed
    }

    private companion object {
        const val KMH_PER_MPS = 3.6f
    }
}
