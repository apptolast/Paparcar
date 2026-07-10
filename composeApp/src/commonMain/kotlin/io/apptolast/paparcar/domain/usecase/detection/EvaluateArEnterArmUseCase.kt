package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * Decision for one FRESH AR `IN_VEHICLE_ENTER` delivered on the privileged service lane.
 * [DET-AR-FIRST-001]
 */
sealed interface ArEnterDecision {

    /** No active parked session — there is no car to leave and no fence to tie a bus apart
     *  from the user's own vehicle. Never arm (the historic AR-proximity false-positive class). */
    data object NoSession : ArEnterDecision

    /** The ENTER's true transition time is older than the pairing window — an OEM re-delivery
     *  with minutes-to-hours of lag. Its exemption moment is gone and its data is history:
     *  evaluator tick only, never an arm. */
    data object StaleEnter : ArEnterDecision

    /** No fresh fix could be sampled — without measured position there is nothing to tie the
     *  boarding to the car. Evaluator tick only. */
    data object NoFix : ArEnterDecision

    /** The boarding happened INSIDE the parked car's own fence: the trip-start moment, caught
     *  before any driving exists. Arm the coordinator "waiting for ride proof"
     *  ([io.apptolast.paparcar.domain.detection.ArmEvidence.BoardingAtCar] — no seed, aborts
     *  armed). Same bus envelope as the geofence EXIT itself (a bus boarded inside your own
     *  fence fools both — documented accepted limitation). */
    data class ArmAtCar(val geofenceId: String) : ArEnterDecision

    /** The boarding is far from the car BUT the OS already delivered a (far/late) EXIT for this
     *  session's fence recently — two independent OS events agreeing that a vehicle trip broke
     *  this fence. Arm the coordinator mid-trip AND re-run the speed-gated departure check.
     *  Replaces the receiver-side escalation (which needed an app-side FGS start — the
     *  BUG-FGS-001 crash class); here the service is ALREADY running on the privileged start. */
    data class ArmMidTrip(val geofenceId: String) : ArEnterDecision

    /** Fresh ENTER, fix in walking range of nothing relevant, no broken-fence record: a bus,
     *  taxi, or AR misfire. The safety-net evaluator (anchor + step budget) is the right judge;
     *  arming here would resurrect the bus false positive that killed the legacy AR-arm. */
    data object TickOnly : ArEnterDecision
}

/**
 * Pure decision ladder for arming detection from a FRESH AR `IN_VEHICLE_ENTER`. [DET-AR-FIRST-001]
 *
 * AR is the LOW-latency signal (field 2026-07-09/10: receivers fired all day on both devices
 * while every geofence EXIT was delivered 951–2 192 m late), but it fires on ANY vehicle — a
 * bus, a taxi, a friend's car. The ladder therefore only arms when the boarding is tied to the
 * user's OWN car: positionally (inside its fence → [ArEnterDecision.ArmAtCar]) or by conjunction
 * with the fence's own broken-EXIT record ([ArEnterDecision.ArmMidTrip]). Everything else is a
 * nomination for the evaluator, never an arm — "an OS event nominates, only measured movement
 * confirms" [DET-RIDE-PROOF-001].
 */
class EvaluateArEnterArmUseCase(private val config: ParkingDetectionConfig) {

    /**
     * @param session          The active parked session (active-vehicle preferred), or null.
     * @param fix              Fresh one-shot fix sampled on delivery, or null when unavailable.
     * @param enterTrueTimeMs  TRUE transition time of the ENTER (from `elapsedRealTimeNanos`).
     * @param nowMs            Wall-clock now (epoch-ms).
     * @param recentStaleExitRecorded Whether a far-delivered geofence EXIT was recorded recently
     *                         (the trust triage's disk record) — the other half of the mid-trip
     *                         conjunction. [DET-CONJUNCTION-001]
     */
    operator fun invoke(
        session: UserParking?,
        fix: GpsPoint?,
        enterTrueTimeMs: Long,
        nowMs: Long,
        recentStaleExitRecorded: Boolean,
    ): ArEnterDecision {
        val geofenceId = session?.geofenceId ?: return ArEnterDecision.NoSession

        val lagMs = nowMs - enterTrueTimeMs
        if (lagMs !in 0..config.exitEnterPairWindowMs) return ArEnterDecision.StaleEnter

        // A boarding that predates the session belongs to the trip that CREATED this parking
        // (or an OEM re-delivery of it) — never evidence of leaving it. [DET-SESSION-BIRTH-001]
        if (enterTrueTimeMs < session.location.timestamp) return ArEnterDecision.StaleEnter

        if (fix == null) return ArEnterDecision.NoFix

        val distanceMeters = haversineMeters(
            fix.latitude, fix.longitude,
            session.location.latitude, session.location.longitude,
        )
        val radiusMeters = config.geofenceRadiusFor(session.sizeCategory, session.location.accuracy)

        return when {
            distanceMeters <= radiusMeters + fix.accuracy -> ArEnterDecision.ArmAtCar(geofenceId)
            recentStaleExitRecorded -> ArEnterDecision.ArmMidTrip(geofenceId)
            else -> ArEnterDecision.TickOnly
        }
    }
}
