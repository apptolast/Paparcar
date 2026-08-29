package com.rndeveloper.paparcar.domain.detection.sentry

import com.rndeveloper.paparcar.domain.detection.ArmEvidence

import com.rndeveloper.paparcar.domain.detection.state.DriveProof
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.haversineMeters

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
 * [com.rndeveloper.paparcar.domain.usecase.detection.EvaluateArEnterArmUseCase] uses for ArmAtCar.
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

/**
 * [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] **What the successor INHERITS** from the
 * session it replaces. The sibling verdict of [shouldSupersedeRunningSession]: that one answers
 * *whether* to hand over, this one answers *what travels*.
 *
 * Until now the answer was "nothing", and nothing is wrong. A supersede is not a new trip — it is
 * the same trip changing which session follows it, because a fresh trigger says the journey is not
 * over. Field 2026-08-25: the user stopped at a petrol station two streets from home, got out,
 * got back in, and drove the last few metres. The AR `IN_VEHICLE ENTER` at 19:59:05 was TRUE, so
 * superseding was right; what was wrong is that the replacement started from zero. It measured
 * 35 s at ≤13,7 km/h, never reached driving speed, and the false-ENTER abort discarded a park whose
 * drive the predecessor had proven at 98 km/h across 60 fixes.
 *
 * Only a PROVEN drive is inherited, never a mere authorization. The predecessor could itself have
 * been seeded on trust ([SessionTelemetry.authorizedOnArmTrustOnly]), and passing that on would
 * launder a nomination into a measurement one supersede at a time — a chain of sessions each
 * believing the previous one saw a car, none of which ever did. [DriveProof.proven] is the latched,
 * measured fact and the only thing here that qualifies.
 *
 * @return null when there is nothing to inherit, so the caller keeps whatever its own lane verified
 *   and a supersede of a session that never drove changes nothing. When non-null it OUTRANKS the
 *   caller's evidence: every other arm is a nomination, and this one is a measurement.
 */
fun inheritedArmEvidence(runningDrive: DriveProof): ArmEvidence.InheritedDrive? {
    val source = runningDrive.proven ?: return null
    return ArmEvidence.InheritedDrive(maxSpeedMps = runningDrive.provenMaxSpeedMps, source = source)
}
