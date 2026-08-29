package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig

/**
 * [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] What a single speed sample is allowed to say about
 * the departure event it accompanies.
 */
enum class DepartureSpeedVerdict {
    /** Credible driving speed measured INDEPENDENTLY of the event — admissible departure proof. */
    Independent,

    /** Credible driving speed, but from a fix contemporaneous with the event that fired: the
     *  trigger echoing itself, not a second measurement. */
    Echo,

    /** No credible driving speed (too slow, or too degraded a fix to trust the speed at all). */
    NotDriving,
}

/**
 * [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] THE independence rule: **one fix must never both
 * FIRE a departure event and CONFIRM it.**
 *
 * [ParkingDetectionConfig.isCredibleDrivingSpeed] answers "is this sample fast enough, and precise
 * enough to believe its speed?". It cannot answer "is this sample a *second opinion*?" — and that
 * is the question an indoor GPS mirage fails. A stationary phone indoors emits bursts that claim
 * driving speed at credible accuracy from a position a hundred metres away; the burst breaks the
 * parked car's geofence and, milliseconds later, vouches for the very exit it caused. A real driver
 * is still at speed on the next genuinely new samples ~20 s later; observed mirage bursts die
 * within ~10 s and never survive the gap.
 *
 * The rule lived inside `DetectParkingDepartureUseCase` only, so the *worker* lane was immune while
 * the *arm* lane was not — the two evaluators looked at the SAME fix and disagreed. Field
 * 2026-08-22 20:50 (Oppo, at home): one 36 km/h fix at 101 m armed the coordinator `verified_speed`
 * (seeding `hasEverReachedDrivingSpeed`, disarming every anti-walking guard) while the worker, 760 ms
 * later, called the same sample `exit_echo` and went on to DISMISS the departure outright. Four
 * minutes later the walk through the house confirmed a phantom park in the living room, replacing
 * the correct pin and deleting its geofence. Predecessor incident, same shape, same phone, same
 * house: field 2026-07-27 18:30 — which is what bought the gate that was then wired into one lane.
 *
 * Shared by two verdicts (the arm's [com.rndeveloper.paparcar.domain.usecase.parking
 * .VerifyDepartureEvidenceUseCase] and the worker's
 * [com.rndeveloper.paparcar.domain.usecase.parking.DetectParkingDepartureUseCase]), so it is a pure
 * top-level function here rather than a private copy in each. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * @param fixTimestampMs  Epoch-ms the speed sample was taken, or null when unavailable — then the
 *   sample cannot prove its independence and fails closed to [DepartureSpeedVerdict.Echo].
 * @param eventTimestampMs Epoch-ms of the departure event this sample would corroborate.
 */
fun classifyDepartureSpeed(
    config: ParkingDetectionConfig,
    speedKmh: Float?,
    accuracyM: Float?,
    fixTimestampMs: Long?,
    eventTimestampMs: Long,
): DepartureSpeedVerdict {
    if (!config.isCredibleDrivingSpeed(speedKmh, accuracyM)) return DepartureSpeedVerdict.NotDriving
    val independent = fixTimestampMs != null &&
        fixTimestampMs - eventTimestampMs >= config.departureProofMinGapMs
    return if (independent) DepartureSpeedVerdict.Independent else DepartureSpeedVerdict.Echo
}
