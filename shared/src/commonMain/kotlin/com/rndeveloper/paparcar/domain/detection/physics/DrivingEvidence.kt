package com.rndeveloper.paparcar.domain.detection.physics

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig

/**
 * [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] **Did this session watch a car drive, and what does that
 * entitle it to?** — one verdict, measured, with the right it grants attached to it.
 *
 * ## Why a type and not four booleans
 *
 * The question used to be answered in four places with four different things: `driveAuthorized`
 * (the arm's NOMINATION, which one in-band fix flips), `hasEverMoved` (speed and displacement
 * crossed once), `DriveProof.proven` (the track's corroboration) and `provenDrivingBandMs` (time
 * held in band). The path that plants the most pins — `steps+egress` — consulted **none of the last
 * two**: it leaned on the nomination having let the fix through, and `PreDriveSkipStage` says so in
 * its own KDoc (*"passing this gate proves nothing about the trip"*) three stages above the site
 * that treats it as proof.
 *
 * Field 2026-08-29 23:56 is what that costs: one mirage fix at 7.71 m/s (its 71.6 m undone 64.8 m
 * backwards 3.5 s later) was the entire driving evidence of a session that silently pinned "La
 * Parafarmacia" at reliability 0.9, with the user on foot the whole time.
 *
 * ## The three tiers, and the right each one grants
 *
 * | evidence   | right                                    |
 * |------------|------------------------------------------|
 * | [Measured] | confirm a park in SILENCE                |
 * | [Weak]     | **ask**, never plant                     |
 * | [None]     | close; not even a question about a park  |
 *
 * The doctrine this encodes is the governing one — *the event nominates, only measured movement
 * confirms* — with the asymmetric-failure rule underneath it: in doubt we ASK, because a question
 * costs a tap and a phantom pin costs the user's trust.
 *
 * @see drivingEvidence for the single construction site and the three bars [Measured] must clear.
 */
sealed interface DrivingEvidence {

    /** Fixes seen at real driving speed with credible accuracy — the count all three tiers report. */
    val credibleFixes: Int

    /** Not one fix at driving speed with believable accuracy. Nothing here says a car was involved. */
    data object None : DrivingEvidence {
        override val credibleFixes: Int get() = 0
    }

    /**
     * Something crossed the driving bar, but not enough of it to be a trip: this is the band GPS
     * noise lives in. May open a question; may never plant a pin.
     *
     * @param why Which bar it failed, in words, so a `parkdiag` line and a diagnostic can say it.
     */
    data class Weak(override val credibleFixes: Int, val why: String) : DrivingEvidence

    /**
     * Measured driving. The only tier that authorises a silent pin.
     *
     * @param excursionMeters Furthest the position provably got from the session origin, over fixes
     *   whose accuracy was credible.
     * @param sustainedBandMs Time held in the driving band, drive-proof gated.
     */
    data class Measured(
        override val credibleFixes: Int,
        val excursionMeters: Double,
        val sustainedBandMs: Long,
    ) : DrivingEvidence

    /** ONLY [Measured] may save a park without asking. */
    val mayConfirmSilently: Boolean get() = this is Measured

    /** [None] may not even ask: a question about a park it has no reason to believe happened is a
     *  question whose answers both damage the state. */
    val mayAskAboutAPark: Boolean get() = this !is None
}

/**
 * [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] The single construction site. Every call must come through
 * `DetectionSessionState.drivingEvidence`, which is the only place that knows all four inputs.
 *
 * [DrivingEvidence.Measured] requires **all three** bars, and on the field night of 2026-08-29 each
 * one killed the false positive on its own, while both real trips cleared all three:
 *
 * | bar                                            | value | FP   | 21:47   | 01:20   | Calle Gavia |
 * |------------------------------------------------|-------|------|---------|---------|-------------|
 * | `credibleFixes >= minDrivingFixesForConfirm`    | 2     | **1**| 86      | 7       | 2           |
 * | `excursionMeters >= minimumTripDistanceMeters`  | 150 m | **72**| 3098 m | 2493 m  | 543 m       |
 * | `sustainedBandMs >= sustainedDriveProofMs`      | 30 s  | **0**| 30,0 s  | 45,0 s  | 36 s        |
 *
 * ⚠️ **The fix-count bar is the WEAKEST of the three, and 5 was measured to be wrong.** The redesign
 * proposed 5 from a fleet statistic; the Calle Gavia trace of 2026-07-04 — the skeletal MIUI stream
 * the config already calls the weakest legitimate car trace on file — has **2 credible fixes in
 * 11**, and a bar of 5 turned that correct park into a silent false negative (its own replay test
 * caught it). A fix count measures how densely the OS sampled, not whether a car moved. Two is the
 * LONE-SAMPLE rule instead: `DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001` will not move an anchor
 * on one trip-speed fix, and this will not plant a pin on one.
 *
 * The physical bars carry the load, and they are the ones with real margin: the false positive
 * failed all three independently (72 m against 150, 0 ms against 30 s), while Calle Gavia cleared
 * both physical bars with room to spare.
 *
 * ⚠️ **Excursion, not net origin→final displacement.** Taken literally, "net displacement" makes a
 * there-and-back trip that parks on its own street measure ≈0 and turns a real park into a false
 * negative. What has to be refuted is *"71 m out, 65 m back"*, and the furthest point the position
 * provably reached refutes exactly that: the FP got to 72 m, well under the 150 m the config already
 * calls a trip.
 *
 * @param shortHopProven A corroborated SHORT_HOP satisfies the excursion bar without reaching 150 m.
 *   Its proof is already a corroborated displacement (a run of credible fixes unambiguously away
 *   from the pin the car left), and without this exemption moving the car 100 m down the street
 *   would be a false negative.
 */
fun drivingEvidence(
    credibleFixes: Int,
    excursionMeters: Double,
    sustainedBandMs: Long,
    shortHopProven: Boolean,
    config: ParkingDetectionConfig,
): DrivingEvidence {
    if (credibleFixes <= 0) return DrivingEvidence.None

    val excursionCleared = shortHopProven || excursionMeters >= config.minimumTripDistanceMeters
    val why = when {
        credibleFixes < config.minDrivingFixesForConfirm ->
            "only $credibleFixes credible driving fix(es), bar is ${config.minDrivingFixesForConfirm}"
        !excursionCleared ->
            "position never got further than ${excursionMeters.toInt()}m from the origin, bar is ${config.minimumTripDistanceMeters.toInt()}m"
        sustainedBandMs < config.sustainedDriveProofMs ->
            "only ${sustainedBandMs}ms held in the driving band, bar is ${config.sustainedDriveProofMs}ms"
        else -> null
    }

    return if (why == null) {
        DrivingEvidence.Measured(
            credibleFixes = credibleFixes,
            excursionMeters = excursionMeters,
            sustainedBandMs = sustainedBandMs,
        )
    } else {
        DrivingEvidence.Weak(credibleFixes = credibleFixes, why = why)
    }
}
