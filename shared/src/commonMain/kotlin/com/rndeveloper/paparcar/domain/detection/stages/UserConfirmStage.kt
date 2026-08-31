package com.rndeveloper.paparcar.domain.detection.stages

import com.rndeveloper.paparcar.domain.detection.physics.SavedParkingShape
import com.rndeveloper.paparcar.domain.detection.physics.honestZoneRadius
import com.rndeveloper.paparcar.domain.detection.physics.walkableInsideGapMeters
import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.detection.state.EgressBirthJudgement
import com.rndeveloper.paparcar.domain.detection.state.judgeEgressBirth
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * [BUG-COORD-115] **A tap outranks every inference below it.** The user is the highest authority in
 * the system, so this stage wins over the response timeout, the candidate, the fast lane and the
 * scorer — and loses only to the three things a tap cannot make true: a hold already resolving, a
 * session that never drove, a budget already folded.
 *
 * ## The answer settles WHETHER, never WHERE
 *
 * `reliabilityUserConfirmed` (1.0) is right and stays: the user said they parked. But a "Sí" answers
 * *did you park?*, not *is the anchor right?*, and this path spends most of its length working out
 * where the car actually is.
 *
 *  - [DET-ANCHOR-EGRESS-001][DET-GAP-ANCHOR-001] If the egress was born away from the pinned anchor,
 *    or the anchor's stop opened through a GPS hole (rest unwitnessed — possibly a drive-past point
 *    hundreds of metres out), the anchor is NOT the car.
 *  - [DET-CONFIRM-ANCHOR-001] …but "they answer near the car" is an assumption, not a fact. A late
 *    "Sí" arrives from wherever the walk ended, and the current fix is then the PEDESTRIAN's
 *    destination (field 2026-08-11 16:08: 32 driving fixes came to rest, mute step counter, the user
 *    answered after walking away, and the pin planted at the destination). So when the stop was
 *    WITNESSED and the answer arrives far from both the anchor and the egress birth, the save
 *    re-anchors at the witnessed end of driving. Answering near the BIRTH keeps today's behaviour on
 *    purpose: a born-away egress means the birth, not the anchor, is where the car is (field
 *    2026-07-15, Enamorados: frozen at a light 1.11 km back — the user's own stop is the right pin).
 *
 * ## [DET-USER-YES-IS-NOT-A-COORDINATE-001] The bug this stage closes STRUCTURALLY
 *
 * One branch here used to pin an exact point immediately after concluding it did not know where the
 * car was: a gap-born anchor is discarded as "possibly a drive-past point", and the fallback fix was
 * then saved as an exact coordinate **with the doubt recorded nowhere**.
 *
 * The unattended path already bounds that same doubt by what a person could walk inside the hole
 * [DET-GAP-ANCHOR-ZONE-001], and the user path inherits the bound rather than forming a second
 * opinion of it. The doubt hangs on the HOLE, not on which fallback the cascade above happened to
 * pick: when the stop was entered through one, every candidate position here is downstream of the
 * same unwitnessed arrival.
 *
 * Below the zone FLOOR an area says less than the point does, so the point stands. This only stops
 * the exact claim where it was already known to be unsupportable, and leaves every well-located pin
 * exactly as it was.
 *
 * **And this is the one place in the plan where the refactor closes an omission bug by
 * construction**: the stage returns a [SavedParkingShape], so a path added later cannot save
 * anything without saying which shape it is. Forgetting is no longer expressible.
 */
class UserConfirmStage : SessionStage {

    override val stage = DetectionStage.USER_CONFIRM

    override fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        stoppedDurationMs: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict {
        if (!state.confirmation.userConfirmed) return StageVerdict.Skip()

        val notes = mutableListOf(DiagnosticNote("  ▶ USER-CONFIRMED path — entering confirmParking"))
        val where = whereTheCarIs(state, fix, config, notes)
        val shape = shapeFor(state, where, config, notes)

        return StageVerdict.Handled(
            newState = state,
            effects = listOf(
                DetectionEffect.Confirm(
                    shape = shape,
                    vehicleId = state.session.attributedVehicleId,
                    pathLabel = "user",
                    // [DET-C-02] Answered, not inferred: nothing a grace window could learn
                    // outranks the user having told us.
                    mayHold = false,
                ),
            ),
            stopsIteration = true,
            notes = notes + DiagnosticNote("  ◀ USER-CONFIRMED path done — returning from collect"),
        )
    }

    /** The anchor cascade: whose position does this save believe? */
    private fun whereTheCarIs(
        state: DetectionSessionState,
        fix: GpsPoint,
        config: ParkingDetectionConfig,
        notes: MutableList<DiagnosticNote>,
    ): GpsPoint {
        // [DET-NOTHING-TO-JUDGE-IS-NOT-NO-DOUBT-001] Reads `!= BORN_AWAY`, so NOT_RECORDED keeps
        // trusting the anchor — the only one of the three consumers of this judgement that does NOT
        // change, and deliberately. The cascade below exists for an anchor the session has REASON to
        // doubt (born away, or entered through a GPS hole); "we never saw the walk begin" is not
        // such a reason, and demoting on it would move a pin the USER just confirmed off the
        // anchor and onto whatever fix they happened to answer from — a door 40 m away is a worse
        // guess than the stop the session measured. The user's answer proves the park; this branch
        // only picks coordinates. [DET-CONFIRM-ANCHOR-001]
        if (state.judgeEgressBirth(config) != EgressBirthJudgement.BORN_AWAY &&
            !state.anchorGapEnteredAtCapture
        ) {
            return state.anchorTrust.anchor ?: state.bestFix(fix)
        }
        // A gap-born anchor may be a drive-past point hundreds of metres out with unboundable
        // forward error, so it never wins here.
        val witnessedStop = state.anchorTrust.anchor?.takeIf { !state.anchorGapEnteredAtCapture }
        val currentFix = state.bestFix(fix)
        val stopDistanceMeters = witnessedStop?.let { metresBetween(it, currentFix) }
        val birthDistanceMeters = state.anchorTrust.egressBirth?.originFix?.let { metresBetween(it, currentFix) }
        val answeredFarFromCar = stopDistanceMeters != null &&
            stopDistanceMeters > NEAR_CAR_MAX_METERS &&
            (birthDistanceMeters == null || birthDistanceMeters > NEAR_CAR_MAX_METERS)
        notes += "  ⚓ user-confirm anchor: stopDistance=${stopDistanceMeters?.toInt()}m " +
            "birthDistance=${birthDistanceMeters?.toInt()}m " +
            "gapEntered=${state.anchorGapEnteredAtCapture} " +
            "→ ${if (answeredFarFromCar) "witnessed stop" else "current fix"} [DET-CONFIRM-ANCHOR-001]"
        return if (answeredFarFromCar) witnessedStop else currentFix
    }

    /** A point, or an AREA when the hole that fed this position bounds a doubt worth drawing. */
    private fun shapeFor(
        state: DetectionSessionState,
        where: GpsPoint,
        config: ParkingDetectionConfig,
        notes: MutableList<DiagnosticNote>,
    ): SavedParkingShape {
        val doubtMeters = walkableInsideGapMeters(state.anchorTrust.capture.gapMs, config.maxPedestrianSpeedMps)
        val worthDrawing = maxOf(where.accuracy, doubtMeters.toFloat()) > config.honestCloseMinZoneRadiusMeters
        if (!worthDrawing) return SavedParkingShape.ExactPin(where, config.reliabilityUserConfirmed)

        val radius = honestZoneRadius(
            centerAccuracyMeters = where.accuracy,
            doubtMeters = doubtMeters,
            floorMeters = config.honestCloseMinZoneRadiusMeters,
            ceilingMeters = config.unattendedZoneMaxRadiusMeters,
        )
        notes += "  ◯ user-confirm saved as a ZONE r=${radius}m — the answer proves the park, " +
            "not the spot (doubt=${doubtMeters.toInt()}m from a ${state.anchorTrust.capture.gapMs}ms " +
            "GPS hole, fixAcc=${where.accuracy}m) [DET-USER-YES-IS-NOT-A-COORDINATE-001]"
        return SavedParkingShape.BoundedZone(where, radius)
    }

    private fun metresBetween(a: GpsPoint, b: GpsPoint): Double =
        haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    private companion object {
        /** [DET-CONFIRM-ANCHOR-001] How far from a car witness (the witnessed stop anchor or the
         *  egress birth) a user "Sí" may arrive and still count as "answered near the car".
         *  Sized between the standard near-car radii (geofenceRadiusMeters 80 m,
         *  geofenceRadiusVanMeters 120 m) and under egressBirthFloorMeters (150 m) — the scale
         *  at which an honest near-car fix can still sit on a sparse stream. */
        const val NEAR_CAR_MAX_METERS = 100.0
    }
}
