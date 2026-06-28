package io.apptolast.paparcar.domain.detection

/**
 * Starts automatic parking detection on demand — backs the cold-start "I'm driving" affordance. [DET-G-01b]
 *
 * Detection normally arms itself when the user's parking geofence is exited. On a true cold start
 * (no parking ever marked → no geofence) a user who opens the app while **already driving** has no
 * automatic trigger. This kicks off the Coordinator tracking job right now: it follows the trip and,
 * when the user parks, the egress detector confirms the spot and creates the first geofence —
 * bootstrapping the automatic loop. No-op where automatic detection isn't available yet (iOS).
 */
interface ManualParkingDetection {
    /** Begin tracking the current trip immediately. Safe to call repeatedly (the service is idempotent). */
    fun start()
}
