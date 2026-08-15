package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * Second, INDEPENDENT way for a session to prove it measured a drive — the one a SHORT HOP can
 * satisfy. [DET-SHORT-HOP-PROOF-001]
 *
 * `corroboratesDrive` ([DET-DRIVE-PROOF-001]) proves a drive from SPEED: a credible driving-speed
 * fix corroborated by a 20–60 s look-back window covering `minimumTripDistanceMeters`. That shape
 * is unreachable on a short, slow urban hop: field 2026-08-14 22:56 (Oppo, session
 * 1786740987649) armed punctually on a VERIFIED geofence exit, recorded 303 fixes, peaked at
 * 30 km/h, ended 900 m from the car's pin with 104 egress steps — and still logged `drive 3/303`,
 * so `maxSpeedMps` stayed 0, the unattended timeout read "no measured driving" and the park was
 * LOST (a nudge nobody answered). The stop-and-go night hop simply never held ~150 m inside any
 * single 60 s window.
 *
 * This evaluator proves the same fact from DISPLACEMENT instead, and it is deliberately anchored to
 * the PIN THE CAR LEFT — never to the session's own first fix:
 *
 *  - The pin is a position the car provably occupied. Distance from it is the physical question
 *    "did this vehicle actually go somewhere?", which is what the drive statistic means.
 *  - It is what makes the Doppler-mirage class impossible by construction (field 2026-07-27): a
 *    phone sitting indoors next to its own pin measures ~0 m of displacement from it no matter how
 *    many phantom 45 m/s bursts the chipset reports. Anchoring to the session's first fix would
 *    have handed the mirage its own burst as the origin and called the return home a "drive".
 *
 * Doctrine: the EXIT event only NOMINATES (that is why [verifiedDeparture] is required but never
 * sufficient); what CONFIRMS is measured ground covered — sustained across
 * [ParkingDetectionConfig.shortHopProofFixes] consecutive credible fixes, beyond
 * [ParkingDetectionConfig.shortHopProofFloorMeters], past the fence radius and both accuracy
 * envelopes, and beyond what legs could have covered in the elapsed time. Asymmetric failure: every
 * bound errs toward "not proven".
 */
class EvaluateShortHopDriveProofUseCase(
    private val config: ParkingDetectionConfig,
) {

    /**
     * @param departureAnchor The pin the car left (the nominating fence's parked position). Null
     *   for manual / AR-armed trips with no origin pin → never proves anything.
     * @param fix The fix being processed.
     * @param verifiedDeparture Whether the arm carried VERIFIED departure evidence.
     * @param fenceRadiusMeters Radius of the fence the car left — the user could already have been
     *   anywhere inside it when the clock started, so it counts in favour of "walkable".
     * @param elapsedSinceArmMs Wall-clock since the session armed.
     * @param consecutiveQualifyingFixes How many consecutive fixes (INCLUDING this one) have
     *   satisfied the geometric part of the test.
     */
    operator fun invoke(
        departureAnchor: GpsPoint?,
        fix: GpsPoint,
        verifiedDeparture: Boolean,
        fenceRadiusMeters: Float,
        elapsedSinceArmMs: Long,
        consecutiveQualifyingFixes: Int,
    ): Boolean {
        if (!verifiedDeparture) return false
        if (consecutiveQualifyingFixes < config.shortHopProofFixes) return false
        if (!qualifies(departureAnchor, fix, fenceRadiusMeters, elapsedSinceArmMs)) return false
        return true
    }

    /**
     * The per-fix geometric test, exposed so the caller can keep the consecutive-fix run without
     * duplicating the rule: this fix sits credibly and unambiguously away from the pin.
     */
    fun qualifies(
        departureAnchor: GpsPoint?,
        fix: GpsPoint,
        fenceRadiusMeters: Float,
        elapsedSinceArmMs: Long,
    ): Boolean {
        val anchor = departureAnchor ?: return false
        // A degraded fix measures nothing — the same credibility gate every drive decision uses.
        if (fix.accuracy > config.minGpsAccuracyForDriving) return false
        val distance = haversineMeters(
            anchor.latitude, anchor.longitude,
            fix.latitude, fix.longitude,
        )
        // Floor first: well beyond any observed GPS pathology, the fence, and both envelopes.
        if (distance < config.shortHopProofFloorMeters) return false
        if (distance <= anchor.accuracy + fix.accuracy + fenceRadiusMeters) return false
        // …and beyond what legs explain over the elapsed time — the same physics as the ride proof.
        return config.isBeyondPedestrianReach(
            distanceMeters = distance,
            elapsedMs = elapsedSinceArmMs,
            fenceRadiusMeters = fenceRadiusMeters,
            accuracyMeters = anchor.accuracy + fix.accuracy,
        )
    }
}
