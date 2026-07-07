package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * Action decided by [EvaluateSafetyNetCheckUseCase] for one parked session. [DET-SAFETY-NET-001]
 */
sealed class SafetyNetAction {

    /** The fix is INSIDE the session's own geofence radius — re-register the geofence so Play
     *  Services' internal INSIDE/OUTSIDE state is rebuilt from the fresh fix. A prior false EXIT
     *  (walking away, WiFi-positioning drift) leaves that state OUTSIDE; without an *observed*
     *  re-entry the next real drive-away never produces an EXIT transition (field incident
     *  2026-07-04, Calle Gavia: the departure after a dismissed walking-EXIT was silent). */
    data class CureGeofence(val geofenceId: String, val radiusMeters: Float) : SafetyNetAction()

    /** Far from the parked car with a fresh position anchor (the phone was seen INSIDE this
     *  car's fence recently) AND proof the movement was a vehicle trip — either live (credible
     *  driving-speed fix, [preconfirmed] = false: the departure worker re-verifies by speed) or
     *  reconstructed after the fact by the step budget / pedestrian-physics verdict
     *  ([preconfirmed] = true: the trip is already over, a speed re-check would wrongly veto it —
     *  the worker must skip straight to processing). [DET-RECONCILE-001] */
    data class DispatchDeparture(
        val geofenceId: String,
        val preconfirmed: Boolean = false,
    ) : SafetyNetAction()

    /** Far from the parked car, MOVING at driving speed, but WITHOUT a fresh anchor — in a vehicle
     *  that was boarded away from the car (bus / taxi / lift, or a real drive-away whose anchor
     *  expired). Distance + speed alone can't tell those apart (BUG-WALK-DEPART-001), so the user
     *  disambiguates via the "still parked?" prompt; ignoring it leaves the session untouched. NB:
     *  far + STATIONARY is [None], not this — being parked-and-away on foot must never prompt. */
    data class PromptStillParked(val geofenceId: String) : SafetyNetAction()

    /** Nothing to act on: no geofence on the session, or the fix sits in the ambiguous ring
     *  between the geofence radius and the far threshold (GPS noise territory). The sampled fix
     *  has already done the passive half of the job — feeding the fused provider so the
     *  geofencing engine's state stays fresh. */
    data object None : SafetyNetAction()
}

/**
 * Pure decision core of the parked-session safety net. [DET-SAFETY-NET-001]
 *
 * Runs on every safety-net wake-up (15-min periodic worker, significant-motion hardware trigger)
 * while a session is parked and detection is idle. The caller samples ONE active fix and this
 * evaluator maps it to the action for each active session:
 *
 *  - inside the fence        → [SafetyNetAction.CureGeofence] (reset a possibly-poisoned fence)
 *  - far + driving + anchor  → [SafetyNetAction.DispatchDeparture] (recover a missed EXIT, live)
 *  - far + driving, no anchor→ [SafetyNetAction.PromptStillParked] (human disambiguates; never auto)
 *  - far + stationary + anchor + steps ≪ distance/stride
 *                            → [SafetyNetAction.DispatchDeparture] preconfirmed (the trip already
 *                              happened while we slept — step budget proves it) [DET-RECONCILE-001]
 *  - far + STATIONARY, steps compatible with walking (or no anchor)
 *                            → [SafetyNetAction.None] (parked-and-away on foot — never nag)
 *  - ambiguous ring          → [SafetyNetAction.None]
 *
 * The whole point of this layer is that it does NOT depend on Play Services delivering anything:
 * the wake-ups are ours (WorkManager + sensor hub) and the decision inputs are ours (fix, step/AR
 * bus). The geofence EXIT stays the fast path; this is the guarantee that a lost EXIT can no
 * longer stall the detection chain. Evidence rules mirror [VerifyDepartureEvidenceUseCase]:
 * speed must carry credible accuracy, and an IN_VEHICLE_ENTER must PRECEDE now within
 * [ParkingDetectionConfig.vehicleEnterWindowMs].
 *
 * **The position anchor.** What makes a geofence EXIT trustworthy is not the evidence — it is
 * WHERE it fires: only when crossing the boundary of *your own car's* fence, so "vehicle motion
 * right after" almost certainly means your car. A periodic wake-up has no such anchoring: at
 * tick time the user can be doing 40 km/h in a bus 2 km from the parked car. Auto-dispatching on
 * evidence alone would re-open exactly the bus/taxi false positive the geofence design killed.
 * So [SafetyNetAction.DispatchDeparture] additionally requires [lastSeenNearCarAtMs]: the caller
 * records when a safety-net fix last landed INSIDE this session's fence, and only a departure
 * whose movement provably started at the car (near-recently + evidence) auto-releases. This is
 * the same risk envelope as the geofence EXIT itself (a bus crossing your own fence fools both —
 * the accepted limitation documented on [DetectParkingDepartureUseCase]); everything weaker
 * degrades to the human prompt.
 */
class EvaluateSafetyNetCheckUseCase(
    private val config: ParkingDetectionConfig,
) {
    /**
     * @param session             An ACTIVE parked session (caller iterates 0..N of them).
     * @param fix                 The freshly sampled fix (caller skips the check when none).
     * @param lastSeenNearCarAtMs Epoch-ms of the last safety-net fix INSIDE this session's fence
     *                            (the position anchor), or null when never observed / lost.
     * @param nowMs               Wall-clock now (epoch-ms).
     * @param stepsSinceAnchor    Cumulative-step-counter delta since the anchor was written, or
     *                            null when unknown (no hardware, read timeout, no counter sample
     *                            stored with the anchor, or a reboot reset the counter). The
     *                            caller must map a NEGATIVE delta to null. [DET-RECONCILE-001]
     */
    operator fun invoke(
        session: UserParking,
        fix: GpsPoint,
        lastSeenNearCarAtMs: Long?,
        nowMs: Long,
        stepsSinceAnchor: Long? = null,
    ): SafetyNetAction {
        val geofenceId = session.geofenceId ?: return SafetyNetAction.None

        val distanceMeters = haversineMeters(
            fix.latitude,
            fix.longitude,
            session.location.latitude,
            session.location.longitude,
        )

        // Same size/accuracy-aware radius the geofence was registered with (SESSION-RESTORE-001),
        // so "inside" here means inside the REAL fence, not a flat default.
        val radiusMeters = config.geofenceRadiusFor(session.sizeCategory, session.location.accuracy)
        if (distanceMeters <= radiusMeters) {
            return SafetyNetAction.CureGeofence(geofenceId, radiusMeters)
        }

        if (distanceMeters <= config.watchdogFarThresholdMeters) {
            return SafetyNetAction.None
        }

        val nearAgeMs = lastSeenNearCarAtMs?.let { nowMs - it }
        val anchoredToCar = nearAgeMs != null && nearAgeMs in 0..config.vehicleEnterWindowMs

        // Departure IN PROGRESS: a credible driving-speed fix far from the car. Position anchor
        // decides auto vs ask: the movement must have STARTED at the car (seen inside its fence
        // within vehicleEnterWindowMs) to auto-release — otherwise it's a vehicle boarded away
        // from the car (bus/taxi/lift) and only the user can disambiguate. Same bus/taxi risk
        // envelope as the geofence EXIT itself.
        val speedKmh = fix.speed * KMH_PER_MPS
        val credibleDrivingSpeed = speedKmh >= config.minimumDepartureSpeedKmh &&
            fix.accuracy <= config.minGpsAccuracyForDriving
        if (credibleDrivingSpeed) {
            return if (anchoredToCar) {
                SafetyNetAction.DispatchDeparture(geofenceId, preconfirmed = false)
            } else {
                SafetyNetAction.PromptStillParked(geofenceId)
            }
        }

        // Far and NOT at driving speed. [DET-RECONCILE-001] The trip may already be OVER — the
        // field-measured failure mode (2026-07-06, Oppo): the geofence EXIT arrives minutes late,
        // a 2-minute hop fits entirely inside the latency, and by the first wake-up the user is
        // parked at the destination. Catching the drive live is therefore not a guarantee anyone
        // can make. What survives the sleep is the hardware step counter: walking the observed
        // displacement MUST have produced ~distance/stride steps. A fresh anchor (was AT the car)
        // plus a step delta far below that proves the user was DRIVEN from their car — release
        // without asking. A step delta compatible with walking is the normal parked-and-left-on-
        // foot state and stays SILENT (no nag — SAFETYNET-STATIONARY-001 preserved).
        if (anchoredToCar && fix.accuracy <= config.minGpsAccuracyForDriving) {
            if (stepsSinceAnchor != null) {
                // Two conditions, not one: RELATIVE (steps ≪ what walking the displacement costs)
                // proves a ride happened; ABSOLUTE (steps ≤ a fence-diameter's worth) proves the
                // ride was boarded AT the car — 500 steps to a bus stop then a 5 km ride passes
                // the relative check alone. [DET-RECONCILE-001]
                val stepsToWalkHere = distanceMeters / config.strideMeters
                if (stepsSinceAnchor < stepsToWalkHere * config.walkedStepFraction &&
                    stepsSinceAnchor <= config.maxBoardingSteps
                ) {
                    return SafetyNetAction.DispatchDeparture(geofenceId, preconfirmed = true)
                }
            } else if (nearAgeMs != null && nearAgeMs > 0) {
                // No step budget available (no hardware / mute counter / reboot): fall back to
                // pedestrian physics — a sustained average speed no walker reaches proves a ride.
                val averageSpeedMps = distanceMeters / (nearAgeMs / 1000.0)
                if (averageSpeedMps > config.maxPedestrianSpeedMps) {
                    return SafetyNetAction.DispatchDeparture(geofenceId, preconfirmed = true)
                }
            }
        }
        return SafetyNetAction.None
    }

    private companion object {
        const val KMH_PER_MPS = 3.6f
    }
}
