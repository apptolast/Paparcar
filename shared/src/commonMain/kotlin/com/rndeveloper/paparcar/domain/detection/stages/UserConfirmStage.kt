package com.rndeveloper.paparcar.domain.detection.stages

import com.rndeveloper.paparcar.domain.detection.physics.SavedParkingShape
import com.rndeveloper.paparcar.domain.detection.physics.honestZoneRadius
import com.rndeveloper.paparcar.domain.detection.physics.walkableInsideGapMeters
import com.rndeveloper.paparcar.domain.detection.physics.walkedInToAnchorMeters
import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.detection.state.EgressBirthJudgement
import com.rndeveloper.paparcar.domain.detection.state.isAnchorWalkEntered
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
 * ⚠️ [DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001] **That floor rule is about
 * PRECISION, and it was being asked a question about PLACE.** It holds for a doubt the cascade has
 * already spent by relocating — a distance from a fix it chose. It does not hold for the one taint
 * the cascade deliberately does NOT relocate away from: a walk-entered anchor is kept as the centre,
 * and there the number is a lower bound on how wrong the PLACE is, so "the number is under 60 m"
 * says nothing about the anchor being good. See [shapeFor].
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

    /**
     * [DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001] The position this save believes, and
     * **whether that position is a tainted one it kept**.
     *
     * The two travel together because separating them is what broke: [shapeFor] used to re-derive
     * the doubt from the session and could only see the hole, while the fact that the cascade had
     * just RETURNED a walk-entered anchor — the one taint it does not relocate away from — lived
     * only in [whereTheCarIs]'s control flow and reached nobody.
     */
    private data class Where(val point: GpsPoint, val keptATaintedAnchor: Boolean)

    /** The anchor cascade: whose position does this save believe? */
    private fun whereTheCarIs(
        state: DetectionSessionState,
        fix: GpsPoint,
        config: ParkingDetectionConfig,
        notes: MutableList<DiagnosticNote>,
    ): Where {
        val anchor = state.anchorTrust.anchor
        // Identity, not a second opinion: whatever the cascade below returns, this asks whether the
        // thing it handed back IS the anchor and whether that anchor was walked into.
        fun resolved(point: GpsPoint) =
            Where(point, keptATaintedAnchor = point === anchor && state.isAnchorWalkEntered(config))
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
            return resolved(anchor ?: state.bestFix(fix))
        }
        // A gap-born anchor may be a drive-past point hundreds of metres out with unboundable
        // forward error, so it never wins here.
        val witnessedStop = anchor?.takeIf { !state.anchorGapEnteredAtCapture }
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
        return resolved(if (answeredFarFromCar) witnessedStop else currentFix)
    }

    /**
     * A point, or an AREA when this position carries a doubt worth drawing.
     *
     * ## [DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001] Two doubts, and only one of them
     * has to clear the floor
     *
     * A **relocated** doubt is a distance: the cascade above walked away from a gap-born anchor, so
     * what is left to draw is how far the car might be from the fix it chose instead. That is a
     * question of precision, the floor rule answers it, and it is unchanged — below the floor an
     * area really does say less than the point does.
     *
     * A **kept** doubt is not a distance, it is a doubt about the PLACE. When the cascade returns a
     * walk-entered anchor it does not relocate — that is deliberate, a door 40 m away is a worse
     * guess than the stop the session measured — so the save lands on a point the session itself has
     * marked as the pedestrian's, and nothing downstream used to know. Asking "is the number bigger
     * than 60 m?" is the wrong question there, because [walkedInToAnchorMeters] is a LOWER bound:
     * field 2026-07-15 measured **29,5 m** of walk-in against a real error of **37 m**. A small
     * bound is not evidence that the anchor is good; it is evidence that the walk was only partly
     * seen. So a kept taint draws an area whatever its magnitude, and the magnitude only sizes it.
     *
     * This is the same bargain the unattended timeout has always struck on the same anchor
     * (`WALK_ENTERED_ANCHOR`, licensed by `doubt > 0`). Two doors out of one session now answer the
     * question the same way instead of two ways.
     */
    private fun shapeFor(
        state: DetectionSessionState,
        where: Where,
        config: ParkingDetectionConfig,
        notes: MutableList<DiagnosticNote>,
    ): SavedParkingShape {
        val point = where.point
        val gapMs = state.anchorTrust.capture.gapMs
        val gapDoubt = walkableInsideGapMeters(gapMs, config.maxPedestrianSpeedMps)
        val walkInDoubt = if (where.keptATaintedAnchor) {
            walkedInToAnchorMeters(
                stepEventsAtCapture = state.anchorTrust.capture.stepEvents,
                walkInSpanMeters = state.anchorTrust.capture.walkInSpanMeters,
                strideMeters = config.anchorStrideMeters,
            )
        } else {
            0.0
        }
        val doubtMeters = maxOf(gapDoubt, walkInDoubt)
        val worthDrawing = where.keptATaintedAnchor ||
            maxOf(point.accuracy, doubtMeters.toFloat()) > config.honestCloseMinZoneRadiusMeters
        if (!worthDrawing) return SavedParkingShape.ExactPin(point, config.reliabilityUserConfirmed)

        val radius = honestZoneRadius(
            centerAccuracyMeters = point.accuracy,
            doubtMeters = doubtMeters,
            floorMeters = config.honestCloseMinZoneRadiusMeters,
            ceilingMeters = config.unattendedZoneMaxRadiusMeters,
        )
        notes += "  ◯ user-confirm saved as a ZONE r=${radius}m — the answer proves the park, " +
            "not the spot (doubt=${doubtMeters.toInt()}m from a ${gapMs}ms GPS hole" +
            (if (where.keptATaintedAnchor) " + a walk-entered anchor (${walkInDoubt.toInt()}m walked in)" else "") +
            ", fixAcc=${point.accuracy}m) [DET-USER-YES-IS-NOT-A-COORDINATE-001]" +
            (if (where.keptATaintedAnchor) "[DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001]" else "")
        return SavedParkingShape.BoundedZone(point, radius)
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
