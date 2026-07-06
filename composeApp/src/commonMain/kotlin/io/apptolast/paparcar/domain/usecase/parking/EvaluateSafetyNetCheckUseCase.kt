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

    /** Far from the parked car, MOVING at credible driving speed, AND a fresh position anchor (the
     *  phone was seen INSIDE this car's fence recently) — a missed EXIT is in progress and provably
     *  started at the car; dispatch the normal departure pipeline (worker retries, verdict, publish,
     *  session clear). */
    data class DispatchDeparture(val geofenceId: String) : SafetyNetAction()

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
 *  - far + driving + anchor  → [SafetyNetAction.DispatchDeparture] (recover a missed EXIT)
 *  - far + driving, no anchor→ [SafetyNetAction.PromptStillParked] (human disambiguates; never auto)
 *  - far + STATIONARY        → [SafetyNetAction.None] (parked-and-away on foot — never nag)
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
     */
    operator fun invoke(
        session: UserParking,
        fix: GpsPoint,
        lastSeenNearCarAtMs: Long?,
        nowMs: Long,
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

        // The safety net only acts on a departure IN PROGRESS: a credible driving-speed fix far
        // from the car. Being far and STATIONARY (or walking) is the normal "parked and left on
        // foot / by bus / to dinner" state — indistinguishable, after the fact, from a missed
        // drive-away — so it stays SILENT (field incident 2026-07-05, Oppo: every speed-0 tick
        // while at dinner nagged "still parked?"). A real drive-away that the geofence missed is
        // caught while the car is still MOVING here, or self-heals when the vehicle re-parks
        // (replaceActiveSession clears the old session). [SAFETYNET-STATIONARY-001]
        val speedKmh = fix.speed * KMH_PER_MPS
        val credibleDrivingSpeed = speedKmh >= config.minimumDepartureSpeedKmh &&
            fix.accuracy <= config.minGpsAccuracyForDriving
        if (!credibleDrivingSpeed) {
            return SafetyNetAction.None
        }

        // Moving at driving speed, far from the car. Position anchor decides auto vs ask: the
        // movement must have STARTED at the car (seen inside its fence within vehicleEnterWindowMs)
        // to auto-release — otherwise it's a vehicle boarded away from the car (bus/taxi/lift) and
        // only the user can disambiguate. Same bus/taxi risk envelope as the geofence EXIT itself.
        val nearAgeMs = lastSeenNearCarAtMs?.let { nowMs - it }
        val anchoredToCar = nearAgeMs != null && nearAgeMs in 0..config.vehicleEnterWindowMs

        return if (anchoredToCar) {
            SafetyNetAction.DispatchDeparture(geofenceId)
        } else {
            SafetyNetAction.PromptStillParked(geofenceId)
        }
    }

    private companion object {
        const val KMH_PER_MPS = 3.6f
    }
}
