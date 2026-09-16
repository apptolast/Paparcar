package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig

/**
 * [DET-A-TRAIN-IS-NOT-A-CAR-001] How believable it is that the movement this session measured was
 * made by a ROAD vehicle.
 *
 * Three values, because the evidence genuinely comes in three strengths and collapsing them would
 * force one of two wrong behaviours on the middle one — pinning a doubt, or closing a car.
 */
enum class RoadPlausibility {
    /** Nothing measured contradicts a car. The overwhelming majority of sessions. */
    PLAUSIBLE,

    /** Faster than a car should ever be, but not faster than a car CAN be. Ask, never pin. */
    DOUBTFUL,

    /** Faster than a road vehicle is, sustained, across a journey of a scale no road explains. */
    IMPOSSIBLE,
}

/**
 * [DET-A-TRAIN-IS-NOT-A-CAR-001] Was this session's movement possible for a car at all?
 *
 * A PREDICATE, not a verdict: it produces no `detectionPath`, no `outcome` and nothing the user
 * reads — it is an input the confirm verdict and the unattended timeout both consume. So it lives
 * here with the rest of detection's pure policy functions ([isHumanPoweredRide],
 * [freedSpotIsStillThere], [SentryLifecycleDecision]…) instead of as an injected `Evaluate…UseCase`.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ## Why it has to exist at all
 *
 * [isHumanPoweredRide] asks *"did a muscle move you?"*. This asks the question on the other side of
 * the car: *"did something move you that a road cannot hold?"* — and until this ticket nothing did.
 * Every kinematic bar in the probabilistic lane is a FLOOR (`minimumDepartureSpeedKmh` 10 km/h,
 * `minimumTripSpeedMps` 18 km/h, `motorProofSpeedMps` 40 km/h); none is a ceiling. A high-speed
 * train clears the highest of them by a factor of six, so it does not merely pass for a car — it
 * reads as the most solid drive the app has ever recorded.
 *
 * Field 2026-09-15 (Samsung SM-A556B, session `1789472756175`): a tester parked beside a station,
 * walked in to catch the train, and the geofence exit armed a session that then measured 253 km/h
 * for most of 270 minutes with 762 credible driving fixes. It confirmed `steps+egress` on the
 * arrival walk and planted a pin in a terminus 600 km from the car — which in turn left a phantom
 * geofence inside the station, whose sentry armed five more sessions and a sixth that pinned the car
 * a second time, in the city centre, off a 55 km/h bus ride.
 *
 * ⚠️ **The number was already in the codebase, filed under the wrong question.**
 * `sustainedDepartureMaxRateMps` has read 55 m/s since [DET-CREDIBLE-DRIVE-001], commented *"no
 * urban drive sustains more than motorway pace. 55 m/s ≈ 200 km/h"* — and used it to DISCARD such a
 * rate as a cache teleport. The app knew 200 km/h is not a car; it had decided that meant the fix
 * was bad, not that the vehicle was wrong, and so threw away the only evidence that convicts a
 * train. One asks about the quality of a measurement, this one about the nature of what was
 * measured.
 *
 * ## Doctrine
 *
 * A **VETO**, never an arm: implausibility may only contradict, never confirm. Nothing here can turn
 * a session into a pin; it can only stop one.
 *
 * And, unlike the human-powered veto, **it is not revocable by Activity Recognition**. That veto
 * yields to a witnessed `IN_VEHICLE` boarding because AR genuinely answers "what are you on right
 * now" for bicycles. It cannot answer it here: Android's classifier has no rail class and labels a
 * train `IN_VEHICLE`, so deferring to it would mean deferring to the one witness guaranteed to be
 * wrong. Nor is it refuted by the sustained-motor proofs that revoke the bicycle — a train is a
 * motor, and asking "was there a motor?" of a locomotive answers yes.
 *
 * ## Why the two bands are not one threshold
 *
 * There is no single speed that separates cars from trains, because the populations overlap: the
 * highest legal motorway limit across the nine markets we ship to is 140 km/h (Poland), but German
 * unrestricted Autobahn has no limit at all, and roughly 1 % of vehicles measured there exceed
 * 160 km/h (IW, 1.9 billion measurements in North Rhine-Westphalia, 2024). A flat ceiling either
 * sits low enough to convict that 1 % or high enough to acquit slow trains.
 *
 * So the bands split by what the evidence can actually carry:
 *
 *  - **[DOUBTFUL]** — `roadDoubtfulSpeedMps` (160 km/h) held for `sustainedDriveProofMs` (30 s).
 *    Above every legal limit we ship into and at the 99th percentile of the most permissive network
 *    in Europe. A car *can* be here, so the session degrades to a question and the user settles it.
 *  - **[IMPOSSIBLE]** — `roadImpossibleSpeedMps` (200 km/h) held for `roadImpossibleSustainedMs`
 *    (5 min) **AND** a straight-line journey rate of `roadImpossibleJourneyRateMps` (100 km/h)
 *    sustained over `roadImpossibleJourneyBaselineMs` (an hour).
 *
 * The **AND** is the ticket's actual load-bearing idea, and it is what makes 200 km/h safe to act on
 * without asking. Either half alone has an innocent explanation: 200 km/h for five minutes is a fast
 * car on an empty unrestricted stretch, and 100 km/h of straight-line progress for an hour is a
 * motorway haul. Together they describe a vehicle holding rail speed across a journey whose scale no
 * road explains — and they are independent measurements, a clock over samples and a rate over a
 * baseline, so no single sensor pathology produces both.
 *
 * ## Asymmetric failure, and where it moved to
 *
 * *Better a false negative than a false positive* normally reads "when in doubt, ask". [DOUBTFUL]
 * obeys it literally. [IMPOSSIBLE] deliberately does not ask, and that is not an exception to the
 * doctrine but an application of it: the doubt here is not about WHETHER the car parked, it is about
 * WHERE, and the place on offer is hundreds of kilometres from the vehicle. `EvaluateParkingDecision`
 * already wrote the rule for that case under [DET-ASSERTION-OUTRANKS-INFERENCE-001] — *a question
 * whose every possible answer damages the state must not be asked*. A "yes" would move the car to a
 * railway terminus; a silence would leave the prompt to expire into the same guess.
 *
 * The asymmetry survives in the cost of being wrong: a mistaken [IMPOSSIBLE] costs one automatic pin
 * that the driver's next trip re-plants, while the false positive it prevents costs a car marked 600
 * km from where it stands and a stranger sent to a space that was never freed.
 *
 * @param roadDoubtfulBandMs Time this session HELD `roadDoubtfulSpeedMps` across credible successive
 *   fixes (`DriveProof.roadDoubtfulBandMs`).
 * @param roadImpossibleBandMs The same, one band higher (`DriveProof.roadImpossibleBandMs`).
 * @param journeyGroundRateMps The fastest straight-line ground rate sustained from the session
 *   origin over a baseline of at least an hour (`DriveProof.journeyGroundRateMps`). Geodesic, so it
 *   is a conservative lower bound on the road speed the trip would have demanded.
 * @param config Supplies all four bars.
 */
fun judgeRoadPlausibility(
    roadDoubtfulBandMs: Long,
    roadImpossibleBandMs: Long,
    journeyGroundRateMps: Float,
    config: ParkingDetectionConfig,
): RoadPlausibility {
    // Both halves, or neither. The band alone is an Autobahn; the journey alone is a long drive.
    val heldRailSpeed = roadImpossibleBandMs >= config.roadImpossibleSustainedMs
    val travelledBeyondRoad = journeyGroundRateMps >= config.roadImpossibleJourneyRateMps
    if (heldRailSpeed && travelledBeyondRoad) return RoadPlausibility.IMPOSSIBLE

    // Ordered after the terminal case on purpose: a session that convicts also clears the doubt
    // band by construction, and reporting it as DOUBTFUL would degrade a decided session to a
    // question — the same collapse `DET-PROMPT-STATES-ITS-REASON-001` split the prompt reasons over.
    if (roadDoubtfulBandMs >= config.sustainedDriveProofMs) return RoadPlausibility.DOUBTFUL

    return RoadPlausibility.PLAUSIBLE
}
