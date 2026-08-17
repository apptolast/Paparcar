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
 * @param vehicleType The active vehicle's registered profile.
 * @param bicycleRideAtMs True transition time of the last AR `ON_BICYCLE` ENTER this session saw
 *   (epoch-ms), or null. AR reports the real transition moment, not the delivery one, so its ~2 min
 *   of latency does not shift the verdict.
 * @param vehicleRideAtMs True transition time of the last AR `IN_VEHICLE` ENTER, or null.
 * @param nowMs Wall clock.
 */
fun isHumanPoweredRide(
    vehicleType: VehicleType?,
    bicycleRideAtMs: Long?,
    vehicleRideAtMs: Long?,
    nowMs: Long,
    config: ParkingDetectionConfig,
): Boolean {
    // [DET-SOLID-001][C2] The profile answer stands on its own and always did: a registered bike or
    // scooter never auto-confirms, whatever AR happens to think.
    if (vehicleType == VehicleType.SCOOTER || vehicleType == VehicleType.BIKE) return true

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
