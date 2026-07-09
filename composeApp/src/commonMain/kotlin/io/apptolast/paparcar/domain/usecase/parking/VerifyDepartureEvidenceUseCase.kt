package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.util.PaparcarLogger

/**
 * [DET-G-05][DET-SOLID-001] Pre-arm verifier for a GEOFENCE_EXIT: what *vehicle* evidence backs
 * this exit, or did the phone simply leave the radius on foot?
 *
 * A geofence exit fires whenever the PHONE crosses the parked-car radius — and walking away
 * after a real park is what every user does, every time. Arming the coordinator as a
 * "confirmed departure" on that signal alone seeds `hasEverReachedDrivingSpeed` and disarms
 * the anti-walking guards, letting the steps+egress path re-confirm a bogus park at the
 * pedestrian's position (BUG-REPARK-WALK-001, regression introduced by DET-G-04).
 *
 * Evidence, strongest first:
 * 1. [ArmEvidence.VerifiedBySpeed] — a fix at ≥ [ParkingDetectionConfig.minimumDepartureSpeedKmh]
 *    with credible accuracy (≤ [ParkingDetectionConfig.minGpsAccuracyForDriving]; a degraded fix
 *    can fake departure speed while walking). Covers the common mid-drive exit.
 * 2. [ArmEvidence.VerifiedByVehicleEnter] — AR `IN_VEHICLE_ENTER` that PRECEDED the exit within
 *    [ParkingDetectionConfig.vehicleEnterWindowMs] AND whose displacement the physics corroborate
 *    ([ParkingDetectionConfig.isBeyondPedestrianReach]): the position must already have run away
 *    from the car faster than legs allow. AR misfires while walking (field 2026-07-09 11:53,
 *    Redmi) — an event NOMINATES a departure, only measured movement CONFIRMS one.
 *    Strict ordering is safe because the receiver stamps TRUE transition times
 *    (not delivery times) — an ENTER recorded after the exit (boarding a bus once already
 *    outside the radius) is NOT departure evidence. [DET-SOLID-001][DET-RIDE-PROOF-001]
 * 3. [ArmEvidence.Unverified] — walking produces neither signal. The exit must still arm the
 *    coordinator (evidence can arrive late), but WITHOUT the seed; the departure worker
 *    upgrades the live session via `DepartureConfirmationListener` once its verdict lands.
 *
 * Pure synchronous evaluator — the caller supplies the sampled fix.
 */
class VerifyDepartureEvidenceUseCase(
    private val departureEventBus: DepartureEventBus,
    private val config: ParkingDetectionConfig,
) {
    private companion object {
        const val TAG = "VerifyDepartureEvidenceUseCase"
    }

    /**
     * @param exitTimestampMs  Epoch-ms of the geofence exit event.
     * @param currentSpeedKmh  Speed (km/h) sampled when handling the exit, or null if unavailable.
     * @param currentAccuracyM Horizontal accuracy (m) of that sample, or null if unavailable.
     * @param sessionStartMs   Epoch-ms the exiting session began, or null when unknown. A boarding
     *        that PREDATES the session is the inbound trip's boarding (or an OEM re-delivery of
     *        it) — never evidence of leaving it (field 2026-07-08 18:52, Redmi: a 17-min-old
     *        re-delivered ENTER "verified" a walking exit). [DET-SESSION-BIRTH-001]
     * @param distanceFromCarMeters Distance (m) of the sampled fix from the parked car, or null
     *        when no fix (or no session position) is available. The ENTER corroboration input —
     *        without it the ENTER cannot be verified (fail closed). [DET-RIDE-PROOF-001]
     * @param fenceRadiusMeters The session's real geofence radius (size + accuracy aware) — the
     *        positional slack of the pedestrian-reach bound.
     */
    operator fun invoke(
        exitTimestampMs: Long,
        currentSpeedKmh: Float?,
        currentAccuracyM: Float? = null,
        sessionStartMs: Long? = null,
        distanceFromCarMeters: Double? = null,
        fenceRadiusMeters: Float? = null,
    ): ArmEvidence {
        val speedConfirms = config.isCredibleDrivingSpeed(currentSpeedKmh, currentAccuracyM)
        if (speedConfirms) {
            PaparcarLogger.d(TAG, "departure evidence: SPEED (speedKmh=$currentSpeedKmh acc=$currentAccuracyM)")
            return ArmEvidence.VerifiedBySpeed(speedKmh = currentSpeedKmh!!, accuracyM = currentAccuracyM)
        }

        val enteredAt = departureEventBus.lastVehicleEnteredAt
            ?.takeIf { sessionStartMs == null || it >= sessionStartMs }
        val enterToExitMs = enteredAt?.let { exitTimestampMs - it }
        if (enterToExitMs != null && enterToExitMs in 0..config.vehicleEnterWindowMs) {
            // [DET-RIDE-PROOF-001] The ENTER is a nomination; the corroboration is displacement
            // beyond pedestrian reach since the boarding. A phantom ENTER while walking (field
            // 2026-07-09 11:53, Redmi: 14 s before a walking exit at 127 m — released the spot
            // and seeded a phantom park at the hairdresser's) fails this bound; a real
            // drive-away exceeds it within the first blocks. No fix / no distance → fail closed
            // to Unverified: the arm still happens, the anti-walking guards stay on, and the
            // departure worker upgrades the live session when real evidence lands.
            val corroborated = distanceFromCarMeters != null && fenceRadiusMeters != null &&
                config.isBeyondPedestrianReach(
                    distanceMeters = distanceFromCarMeters,
                    elapsedMs = enterToExitMs,
                    fenceRadiusMeters = fenceRadiusMeters,
                    accuracyMeters = currentAccuracyM ?: 0f,
                )
            if (corroborated) {
                PaparcarLogger.d(TAG, "departure evidence: VEHICLE_ENTER corroborated (enterToExitMs=$enterToExitMs d=${distanceFromCarMeters}m)")
                return ArmEvidence.VerifiedByVehicleEnter(enterToExitMs = enterToExitMs)
            }
            PaparcarLogger.d(
                TAG,
                "IN_VEHICLE_ENTER within window but pedestrian-reachable " +
                    "(enterToExitMs=$enterToExitMs d=${distanceFromCarMeters}m acc=$currentAccuracyM) " +
                    "— nomination without movement proof, NOT verified [DET-RIDE-PROOF-001]",
            )
        }

        PaparcarLogger.d(
            TAG,
            "departure evidence: UNVERIFIED (speedKmh=$currentSpeedKmh acc=$currentAccuracyM enteredAt=$enteredAt)",
        )
        return ArmEvidence.Unverified
    }
}
