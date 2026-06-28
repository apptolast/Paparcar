package io.apptolast.paparcar.domain

interface ActivityRecognitionManager {
    /**
     * Registers the always-on IN_VEHICLE **EXIT** transition (delivered to a plain broadcast
     * receiver). EXIT is a non-decisive hint forwarded to a running Coordinator — no foreground
     * service, so no notification flash on bus rides. [DET-G-01]
     */
    fun registerTransitions()
    fun unregisterTransitions()

    /**
     * Registers the IN_VEHICLE **ENTER** transition delivered DIRECTLY to the detection service
     * (privileged FGS start), gated to the window in which a car is parked. Boarding a vehicle
     * where the car is parked is the software fallback that re-arms detection for short trips the
     * geofence EXIT never catches. Scoped to the parked window so the FGS-promote (and its brief
     * notification) only happens when the user actually has a car parked — not on every bus ride.
     * Idempotent. [DET-AR-REARM-001]
     */
    fun registerVehicleEnterArming()

    /** Removes the scoped IN_VEHICLE ENTER arming registration. Called when the parked session ends. */
    fun unregisterVehicleEnterArming()
}
