package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.util.haversineMeters

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
 * What the connect→disconnect engagement with the paired car was SHAPED like.
 * [DET-BT-DISCONNECT-WITHOUT-RIDE-001]
 *
 * A Bluetooth engagement proves the car came within radio range of the phone. That is PRESENCE,
 * not driving — and the two are only the same thing when the phone was inside the car.
 */
sealed interface BtEngagement {
    /** Long enough to be the end of a trip: the detector may go on to place a pin. */
    data class Ride(val durationMs: Long) : BtEngagement

    /** Too short to be a trip — the car came into range and left again (parked beside the phone by
     *  someone else, or simply driven past). Nominates: ask, never place. */
    data class ProximityOnly(val durationMs: Long) : BtEngagement

    /** No connect on record, so the engagement cannot be sized at all. Treated as doubt, not as
     *  permission. */
    data object Unknown : BtEngagement
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

    /**
     * Size the engagement that just ended, BEFORE any fix is sampled.
     * [DET-BT-DISCONNECT-WITHOUT-RIDE-001]
     *
     * This is the gate the BT lane never had. Every other confirmation path in the app demands
     * measured driving; this one demanded only that a paired MAC dropped, and then accepted the
     * candidate fix *because the user was standing still* — the exact state of someone who has not
     * driven anywhere. Field 2026-08-21 (Oppo + Kamiq): an 11.5 s engagement, the car brought home
     * by a family member and switched off next to the phone, produced a 0.85 session with a
     * geofence whose EXIT then armed the coordinator.
     *
     * Deliberately NOT a distance or speed test: at this instant nothing has been measured yet.
     * Duration is the one thing already on disk (the manifest ACL receiver stamps it across OEM
     * process kills), and it separates the two shapes cleanly — no key-turn handshake or drive-by
     * lasts [ParkingDetectionConfig.btMinRideDurationMs], and no real trip lasts less.
     *
     * @param connectedAtMs epoch-ms of the matching `ACL_CONNECTED`, or null when none is on record.
     * @param disconnectedAtMs epoch-ms of the `ACL_DISCONNECTED` being handled.
     */
    fun evaluateEngagement(connectedAtMs: Long?, disconnectedAtMs: Long): BtEngagement {
        if (connectedAtMs == null) return BtEngagement.Unknown
        val durationMs = disconnectedAtMs - connectedAtMs
        // A connect stamped in the FUTURE (clock change between the two edges) sizes nothing, and
        // neither does one so old it can only be a stamp the app missed the replacement for
        // (OEM force-stop swallowing the CONNECT broadcast).
        if (durationMs < 0 || durationMs > config.btMaxRideDurationMs) return BtEngagement.Unknown
        return if (durationMs >= config.btMinRideDurationMs) {
            BtEngagement.Ride(durationMs)
        } else {
            BtEngagement.ProximityOnly(durationMs)
        }
    }

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
