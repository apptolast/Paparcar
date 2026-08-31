package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.physics.isWithinFence
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.UserParking

private const val KMH_PER_MPS = 3.6f

/**
 * [DET-A-DECLINED-ARM-IS-NOT-SILENCE-001] **What the deliberate second look at a declined boarding
 * is allowed to conclude.**
 *
 * `EvaluateArEnterArmUseCase` answers `TickOnly` when a FRESH AR `IN_VEHICLE_ENTER` arrives with a
 * fix OUTSIDE the parked car's fence: someone boarded a vehicle, and it is not provably yours. That
 * decline is correct and stays — AR fires on buses and taxis, and arming on it would be the event
 * confirming itself.
 *
 * What was wrong is what came NEXT: nothing. The service logged the decline, wrote a telemetry row
 * nobody reads, and called `enterSentry` — GPS off. Every remaining watcher shares one evaluator
 * (`EvaluateSafetyNetCheckUseCase`), and between the fence radius (~95 m) and
 * `watchdogFarThresholdMeters` (300 m) that evaluator answers `None`. So the first mechanism able to
 * decide anything is the one that finds the car already far away.
 *
 * Field 2026-08-30 21:20:42 (Oppo): fresh ENTER, lag 232 ms, fix 143 m from the car — squarely in
 * that dead ring. Nobody looked again for 6 min 51 s and 3,6 km. In the day's three other declines
 * the geofence EXIT armed the coordinator within 100 ms; here it landed 7 min late, because the
 * fence had been re-registered 2 min 21 s earlier and the GPS was off, so Play Services had nothing
 * but wifi/cell to converge on.
 *
 * ## Why a second look, and not a lower bar
 *
 * The boarding fix cannot decide: at 21:20:42 it read **0.22 m/s** — the user was still walking or
 * had only just pulled away. A speed test at the moment of the ENTER would have answered "no" and
 * been just as blind. What separates "my car" from "a bus I happened to board" is not available in
 * that instant either, and pretending otherwise is how a phantom pin gets planted.
 *
 * So the doctrine is applied literally rather than bent: **the event NOMINATES — it buys one more
 * look — and only MEASURED movement arms.** The declined boarding is the reason to look, never the
 * reason to arm. If the re-look measures credible driving, something is driving this phone away
 * while nothing is following it, and following is strictly better than not. If it does not, the
 * silence that already happens is the right answer and this costs one fix.
 *
 * The arm it produces carries [ArmEvidence.BoardedAwayFromCar] → [DriveAuthorization.None]: the
 * session follows the trip at full quality but may not save a park in silence. **A bus ride costs
 * one question, never a phantom pin** — which is the same asymmetry the decline itself protects.
 */
fun shouldArmAfterDeclinedBoarding(
    fix: GpsPoint?,
    session: UserParking?,
    config: ParkingDetectionConfig,
): Boolean {
    if (session == null || fix == null) return false

    // Credible DRIVING, judged by the same predicate the rest of the detector uses — no second
    // calibration enters with this ticket. Accuracy is part of it on purpose: the Redmi's GPS
    // invents pedestrian-band speeds at 100+ m accuracy all day.
    if (!config.isCredibleDrivingSpeed(fix.speed * KMH_PER_MPS, fix.accuracy)) return false

    // Still inside the car's own fence → whatever the speed field claims, this phone has not gone
    // anywhere. Driving speed AT the parked car is the manoeuvring/GPS-noise case the fence exists
    // to absorb, and arming on it would re-open the false-ENTER churn the decline just avoided.
    val radiusMeters = config.geofenceRadiusFor(session.sizeCategory, session.location.accuracy)
    if (isWithinFence(fix, session.location, radiusMeters)) return false

    return true
}
