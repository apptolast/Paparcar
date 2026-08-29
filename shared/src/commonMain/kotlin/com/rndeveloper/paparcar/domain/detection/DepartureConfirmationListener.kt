package com.rndeveloper.paparcar.domain.detection

/**
 * Narrow port through which the departure pipeline notifies a live detection session how the
 * departure it was armed on was ADJUDICATED — confirmed after the arm (late evidence: AR ENTER can
 * deliver ~2 min after the geofence exit), or dismissed outright. Implemented by
 * `CoordinatorParkingDetector`; extracted as an interface so
 * [com.rndeveloper.paparcar.domain.usecase.parking.RunDepartureCheckUseCase] is testable without
 * constructing the whole detector. [DET-G-05][DET-SOLID-001]
 *
 * [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] The port used to carry only the good news. Arm
 * evidence is a HYPOTHESIS the departure worker spends ~45 s adjudicating, so a one-way wire meant
 * a refuted arm kept the privileges it was granted on trust: field 2026-08-22 20:50 (Oppo) armed
 * `verified_speed` on an indoor mirage, the worker DISMISSED that very departure two minutes later,
 * and the session — still seeded, still with its anti-walking guards down — confirmed a phantom
 * park in the living room.
 */
interface DepartureConfirmationListener {
    /** Upgrade the RUNNING session with confirmed-departure evidence. No-op between sessions. */
    fun notifyDepartureConfirmed()

    /**
     * The departure this session was armed on did NOT happen (rejected, or attempts exhausted with
     * no admissible vehicle signal). Retract the trust the arm was granted, so the session must
     * measure the drive itself like any unverified arm.
     *
     * Implementations MUST retract narrowly — only for the session armed by [geofenceId], and only
     * while the seed is still UNEARNED. A session that has since measured driving keeps it: the
     * worker adjudicates the EXIT, not the trip that followed it.
     * [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001]
     */
    fun notifyDepartureDismissed(geofenceId: String)
}
