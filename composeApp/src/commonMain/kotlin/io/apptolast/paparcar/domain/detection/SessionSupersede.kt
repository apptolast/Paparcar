package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * [DET-SUPERSEDE-001] Decide whether a new arm trigger (GF_EXIT / AR_VEHICLE_ENTER) that arrived
 * while a detection job is ALREADY running should SUPERSEDE that session — cancel it and arm on the
 * new event — instead of being dropped by the "coordinator already running; not re-arming" guard.
 *
 * The guard exists so a running session's own GPS stream can't reset its abort timer at the SAME
 * place and spin a restart loop [DET-AR-REARM-001]. But when the new event is at a clearly DIFFERENT
 * location than the running session's anchor, that session is a zombie relative to it (field
 * 2026-07-12: a spurious fence left ~100 m from the real next park at WA YUKI blocked its detection).
 * "Different" = beyond the new park's geofence radius plus its own fix accuracy — the same boundary
 * [io.apptolast.paparcar.domain.usecase.detection.EvaluateArEnterArmUseCase] uses for ArmAtCar.
 *
 * Conservative by design: returns false (keep suppressing) when the running anchor is unknown, so a
 * session with no published trip is never superseded on a guess.
 */
fun shouldSupersedeRunningSession(
    newParkLocation: GpsPoint,
    runningAnchor: GpsPoint?,
    newFenceRadiusMeters: Float,
): Boolean {
    val anchor = runningAnchor ?: return false
    val distanceMeters = haversineMeters(
        newParkLocation.latitude, newParkLocation.longitude,
        anchor.latitude, anchor.longitude,
    )
    return distanceMeters > newFenceRadiusMeters + newParkLocation.accuracy
}
