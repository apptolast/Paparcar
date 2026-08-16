package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * Does THIS session's own stream prove the car left its pin? [DET-UNVERIFIED-ARM-DRIVE-PROOF-001]
 *
 * "Verified departure" was modelled as a property of the EVENT that armed the session — an
 * immutable `invoke` parameter. It is not: it is a fact about the car, and evidence for it can
 * arrive at any time. Field 2026-08-15 21:26 (Redmi, session `1786821963745`): MIUI never delivered
 * the geofence EXIT, so a sentry-wake armed the session `self_observed` with the car already 1.1 km
 * from home. The stream then measured 25.6 km/h at 8.9 m accuracy — the very fix that, had the EXIT
 * sampled it, `VerifyDepartureEvidenceUseCase` would have called
 * [io.apptolast.paparcar.domain.detection.ArmEvidence.VerifiedBySpeed]. Arriving 25 s later through
 * the stream, it counted as evidence of nothing: the displacement drive proof
 * ([EvaluateShortHopDriveProofUseCase], gated on a verified arm) never ran, `maxSpeedMps` stayed 0,
 * and a real park died as `aborted_unattended_no_drive` + a nudge nobody answered. The same trip was
 * confirmed by the other phone, whose EXIT arrived on time.
 *
 * This evaluator answers the question by MEASUREMENT, requiring the same two facts a verified arm
 * carries — one from the sampled fix, one from the EXIT event itself:
 *
 *  - **Driving speed, credibly measured** — [ParkingDetectionConfig.isCredibleDrivingSpeed], the
 *    exact predicate the pre-arm verifier applies. A degraded fix can fake speed while walking.
 *  - **Ground the car covered from the pin** — beyond the fence, both accuracy envelopes and
 *    [ParkingDetectionConfig.isBeyondPedestrianReach] since the arm. This is what the EXIT event
 *    proved for free (the phone crossed the radius); without an EXIT the position must prove it.
 *
 * Anchored to the PIN, never to the session's own first fix — the same construction that makes the
 * mirage class impossible in [EvaluateShortHopDriveProofUseCase]: a phone drifting indoors beside
 * its own pin measures ~0 m of displacement from it, whatever speed the chipset claims (field
 * 2026-07-27: 45 m/s at a claimed 5 m accuracy, parked at home). The pedestrian bound is what ties
 * the drive to the car rather than to a bus: boarding one 200 m away takes long enough on foot that
 * legs already explain the distance by the time the bus reaches driving speed.
 *
 * Doctrine: this does not let an EVENT confirm anything — it lets MEASUREMENT answer a question
 * only an event used to be allowed to answer. Asymmetric failure: every bound errs toward
 * "not proven".
 */
class EvaluateMeasuredDepartureUseCase(
    private val config: ParkingDetectionConfig,
) {

    private companion object {
        const val KMH_PER_MPS = 3.6f
    }

    /**
     * @param departureAnchor The pin the car left (the nominating fence's parked position). Null for
     *   manual / AR arms with no origin pin → nothing to measure a departure from.
     * @param fix The fix being processed.
     * @param fenceRadiusMeters Radius of the fence the car left — the user could already have been
     *   anywhere inside it when the clock started, so it counts in favour of "walkable".
     * @param elapsedSinceArmMs Wall-clock since the session armed.
     */
    operator fun invoke(
        departureAnchor: GpsPoint?,
        fix: GpsPoint,
        fenceRadiusMeters: Float,
        elapsedSinceArmMs: Long,
    ): Boolean {
        val anchor = departureAnchor ?: return false
        if (!config.isCredibleDrivingSpeed(fix.speed * KMH_PER_MPS, fix.accuracy)) return false
        val distance = haversineMeters(
            anchor.latitude, anchor.longitude,
            fix.latitude, fix.longitude,
        )
        // Unambiguously outside the fence it left — what the EXIT event asserted by existing.
        if (distance <= anchor.accuracy + fix.accuracy + fenceRadiusMeters) return false
        return config.isBeyondPedestrianReach(
            distanceMeters = distance,
            elapsedMs = elapsedSinceArmMs,
            fenceRadiusMeters = fenceRadiusMeters,
            accuracyMeters = anchor.accuracy + fix.accuracy,
        )
    }
}
