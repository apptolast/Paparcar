package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig

/**
 * [DET-WATCHDOG-DEPARTURE-KNOWS-NO-HOUR-001] Is the plaza this departure freed still likely to BE
 * there — i.e. may it be advertised to the community?
 *
 * A PREDICATE, not a verdict: it produces no `detectionPath`, no `outcome` and nothing the user
 * reads. It only answers one question two closing paths both have to ask, so it lives here with the
 * rest of detection's pure policy functions ([isHumanPoweredRide], [nextSentryWakeAbortStreak],
 * [VehicleFenceOwnershipPolicy]…) instead of as an injected `Evaluate…UseCase`.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * **Why it exists.** The rule itself is old — [DET-RECONCILE-001] wrote it after the Redmi processed
 * a departure 5 h late on 2026-07-06 and published a hole that had closed long before — but it lived
 * as an inline expression inside `RunDepartureCheckUseCase`, the one caller that happens to hold an
 * exit timestamp. The other closing path never got it: the watchdog's *"still parked here?"* prompt
 * published unconditionally, and that is the path with the WORST clock of the two.
 *
 * **An unknown hour is not a recent hour.** Null [exitAtMs] does not mean "assume it just happened",
 * it means the opposite. The watchdog prompt exists *because* nobody observed the EXIT: the safety
 * net measured the user far from their car with no departure evidence, and neither of the two sites
 * that raise it ([ParkingSafetyNetWorker], FGS-start denied and `PromptStillParked`) has an instant
 * to offer — they fire precisely when the anchor is missing. When the user then taps "I've left",
 * they witness the FACT of having gone, never the HOUR. So the fact still closes the session and the
 * geofence in full ([DET-HANDOFF-NOT-MANUAL-001] §B is untouched); the missing hour only forbids the
 * one commitment that is made to STRANGERS.
 *
 * Which direction to fail in is not a judgement call here: publishing early costs a stranger a wasted
 * trip to a space that was taken hours ago, while not publishing costs a plaza nobody could have
 * trusted anyway. Asymmetric failure picks the silence.
 *
 * @param exitAtMs True instant the car left, epoch-ms, or **null when it is not known**.
 * @param nowMs Wall clock.
 * @param config Supplies `spotPublishMaxAgeMs` (10 min) — how old a freed plaza may be and still be
 *   worth announcing.
 */
fun freedSpotIsStillThere(
    exitAtMs: Long?,
    nowMs: Long,
    config: ParkingDetectionConfig,
): Boolean {
    val exitAt = exitAtMs ?: return false
    return nowMs - exitAt <= config.spotPublishMaxAgeMs
}
