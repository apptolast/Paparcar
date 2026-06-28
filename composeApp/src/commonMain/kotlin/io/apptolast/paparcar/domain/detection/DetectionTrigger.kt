package io.apptolast.paparcar.domain.detection

/**
 * Which signal armed a Coordinator detection session. [DET-AR-REARM-001]
 *
 * Detection is a serial, geofence-gated loop: after a park is confirmed the service goes idle and
 * only re-arms on a fresh trigger. Knowing *which* trigger fired is essential for field diagnosis —
 * a stalled loop (no re-arm) versus a phantom arm (AR fired far from the car) look identical in the
 * logs without it. The arming site logs this to three sinks: Crashlytics custom key, the remote
 * [io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger], and a debug notification.
 */
enum class DetectionTrigger {
    /** The user left their OWN parked-car geofence (the decisive, anchored signal). */
    GEOFENCE_EXIT,

    /**
     * Activity Recognition reported IN_VEHICLE_ENTER while the phone was within
     * proximity of the parked car — the software fallback for short trips that never
     * cross the geofence radius, or when Play Services drops the EXIT. [DET-AR-REARM-001]
     */
    AR_PROXIMITY,

    /** The user tapped "I'm driving" — the manual cold-start affordance. */
    MANUAL,
}
