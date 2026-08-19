package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.VehicleType

/**
 * [DET-BIKE-NOT-A-CAR-001] Was the movement this session measured made under HUMAN power?
 *
 * A PREDICATE, not a verdict: it produces no `detectionPath`, no `outcome` and nothing the user
 * reads — it is an input two verdicts consume (the candidate-phase confirm and the unattended
 * timeout). So it lives here, with the rest of detection's pure policy functions
 * ([nextSentryWakeAbortStreak], [SentryLifecycleDecision], [VehicleFenceOwnershipPolicy]…), instead
 * of as an injected `Evaluate…UseCase` class. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * **Why it has to exist at all.** Every kinematic threshold in the probabilistic lane is calibrated
 * against a person on foot, and a bicycle clears all of them: `minimumDepartureSpeedKmh` is 10 km/h,
 * `maxPedestrianSpeedMps` 2,5 m/s (9 km/h), `minimumTripSpeedMps` 5 m/s (18 km/h). Field 2026-08-16
 * 11:08Z (Samsung SM-A536B, session `1786878499475`): a 59-minute ride to Los Toruños peaked at
 * 38 km/h with 58 driving fixes, broke the car's own geofence at 352 m, was sealed `verified_speed`,
 * and re-pinned a Mercedes 4,8 km away on a beach path while the real car sat in Calle Toledo.
 *
 * The pre-existing guard read the REGISTERED VEHICLE PROFILE, and the profile said `CAR` —
 * correctly, because the user owns a car; they simply were not in it. The profile answers "what do
 * you drive", never "what are you on right now". Android's classifier answers that, and we had never
 * asked: only `IN_VEHICLE` transitions were registered while `ON_BICYCLE` sat unused in
 * `ActivityRecognitionLabels`.
 *
 * **Doctrine.** A VETO, never an arm: cycling may only contradict, never confirm. That is the
 * direction asymmetric failure allows — a wrong veto costs one nudge the user can answer, a wrong
 * pin costs a car. And a bicycle carries no Bluetooth MAC, so the deterministic lane is untouched by
 * construction: the two strategies stay separate.
 *
 * **Two sources, measured outranking remembered.** [DET-MOTOR-PROOF-001] AR is the classifier and
 * it never classifies a SHORT ride: field 2026-08-18 20:32 (Oppo, session 1787077943062), a
 * 6-minute bicycle ride produced ZERO AR events among the session's 316 and pinned the bike rack in
 * silence with the car 540 m away. The session's own stream had the answer all along — the step
 * detector ticked 16-20 times while GPS read 3,3-4,1 m/s. Walking at 4 m/s is impossible, and in a
 * moving car the counter stays silent (its phantoms arrive as bursts of 1-3, or while parked): steps
 * concurrent with above-pedestrian-ceiling fixes are a PEDALLING signature. So the kinematic source
 * is judged FIRST — it is this session's measurement, while the AR stamps are a memory with a
 * staleness window. Known cost, accepted: a bike→car trip in one session keeps the cadence latch
 * (there is no kinematic "boarding" to supersede it, unlike the AR lane below) and degrades the
 * final pin to a prompt — one tap, the direction asymmetric failure allows.
 *
 * @param vehicleType The active vehicle's registered profile.
 * @param bicycleRideAtMs True transition time of the last AR `ON_BICYCLE` ENTER this session saw
 *   (epoch-ms), or null. AR reports the real transition moment, not the delivery one, so its ~2 min
 *   of latency does not shift the verdict.
 * @param vehicleRideAtMs True transition time of the last AR `IN_VEHICLE` ENTER, or null.
 * @param nowMs Wall clock.
 * @param fastMotionStepEvents Session count of step events concurrent with a fresh, credible GPS
 *   fix above the pedestrian ceiling (`egressStepMaxSpeedMps`). [DET-MOTOR-PROOF-001]
 * @param fastMotionStepFixes Distinct fixes credited with at least one such step — one fix's burst
 *   can be one pothole; the same signature across separate fixes is a rhythm.
 */
fun isHumanPoweredRide(
    vehicleType: VehicleType?,
    bicycleRideAtMs: Long?,
    vehicleRideAtMs: Long?,
    nowMs: Long,
    fastMotionStepEvents: Int = 0,
    fastMotionStepFixes: Int = 0,
    config: ParkingDetectionConfig,
): Boolean {
    // [DET-SOLID-001][C2] The profile answer stands on its own and always did: a registered bike or
    // scooter never auto-confirms, whatever AR happens to think.
    if (vehicleType == VehicleType.SCOOTER || vehicleType == VehicleType.BIKE) return true

    // [DET-MOTOR-PROOF-001] Pedal cadence — the kinematic source. Feet moving in rhythm WHILE the
    // position travels above the pedestrian ceiling is muscle propelling the movement; measured
    // this session, so it is not subject to the AR staleness/supersession rules below.
    if (fastMotionStepEvents >= config.pedalCadenceMinStepEvents &&
        fastMotionStepFixes >= config.pedalCadenceMinFixes
    ) {
        return true
    }

    val bicycle = bicycleRideAtMs ?: return false
    // Stale evidence decides nothing. A ride this morning must not veto a drive this evening; the
    // session is the natural scope, and the memory window bounds a latch that outlived it.
    if (nowMs - bicycle > config.humanPoweredRideMemoryMs) return false
    // Cycling to the station and then driving is a real trip made by car. The LAST boarding wins,
    // which is also why this reads timestamps rather than a boolean latch: AR delivers transitions
    // out of order relative to wall clock, and only the true transition times are comparable.
    if (vehicleRideAtMs != null && vehicleRideAtMs >= bicycle) return false
    return true
}
