package io.apptolast.paparcar.domain.detection

/**
 * Which signal armed a Coordinator detection session.
 *
 * Detection is a serial, geofence-gated loop: after a park is confirmed the service goes idle and
 * only re-arms on a fresh trigger. Knowing *which* trigger fired is essential for field diagnosis —
 * a stalled loop (no re-arm) versus a phantom arm look identical in the logs without it. The arming
 * site logs this to three sinks: Crashlytics custom key, the remote
 * [io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger], and a debug notification.
 *
 * [DET-SOLID-001 C1b] AR_PROXIMITY was purged with the legacy AR-arming path: arming is exclusive
 * to the geofence exit + the manual affordance; AR is an indicator only.
 */
enum class DetectionTrigger {
    /** The user left their OWN parked-car geofence (the decisive, anchored signal). */
    GEOFENCE_EXIT,

    /** The user tapped "I'm driving" — the manual cold-start affordance. */
    MANUAL,

    /** [DET-AR-FIRST-001] A FRESH AR `IN_VEHICLE_ENTER` tied to the user's own car (boarding
     *  inside the fence, or conjunction with the fence's broken-EXIT record) delivered on the
     *  privileged service lane. The LOW-latency nominator — the geofence EXIT arrives minutes
     *  late on OEMs; measured movement still confirms everything. */
    AR_VEHICLE_ENTER,
}
