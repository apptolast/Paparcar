package com.rndeveloper.paparcar.domain.detection

/**
 * Which signal armed a Coordinator detection session.
 *
 * Detection is a serial, geofence-gated loop: after a park is confirmed the service goes idle and
 * only re-arms on a fresh trigger. Knowing *which* trigger fired is essential for field diagnosis —
 * a stalled loop (no re-arm) versus a phantom arm look identical in the logs without it. The arming
 * site logs this to three sinks: Crashlytics custom key, the remote
 * [com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger], and a debug notification.
 *
 * [DET-SOLID-001 C1b] AR_PROXIMITY was purged with the legacy AR-arming path: arming is exclusive
 * to the geofence exit + the manual affordance; AR is an indicator only.
 */
enum class DetectionTrigger {
    /** The user left their OWN parked-car geofence (the decisive, anchored signal). */
    GEOFENCE_EXIT,

    /** The user tapped "I'm driving" — the manual cold-start affordance. Nobody else may use this
     *  value: it is read as EXPLICIT HUMAN INTENT (it is exempt from the strategy gate and its
     *  evidence label counts as strong), so an automatic arm wearing it is a lie with
     *  consequences. [DET-HANDOFF-NOT-MANUAL-001] */
    MANUAL,

    /** [DET-HANDOFF-NOT-MANUAL-001] `ParkingSafetyNetWorker` dispatched a departure it DEDUCED (the
     *  phone left the parked car's neighbourhood) and handed the rest of the trip to live detection
     *  [DET-ARRIVAL-HANDOFF-001]. It used to ride [MANUAL] — the same intent as the button — so a
     *  worker-born session was indistinguishable from "the user said so" in every diagnostic
     *  (field 2026-08-19 22:32, a bicycle ride armed as `ARM:MANUAL` on both phones).
     *  Nothing witnessed a drive here: the arm is admitted only where the coordinator owns
     *  detection, and its evidence stays weak until the session measures driving itself. */
    ARRIVAL_HANDOFF,

    /** [DET-AR-FIRST-001] A FRESH AR `IN_VEHICLE_ENTER` tied to the user's own car (boarding
     *  inside the fence, or conjunction with the fence's broken-EXIT record) delivered on the
     *  privileged service lane. The LOW-latency nominator — the geofence EXIT arrives minutes
     *  late on OEMs; measured movement still confirms everything. */
    AR_VEHICLE_ENTER,

    /** [DET-RESIDENT-FGS-001] The significant-motion sensor fired while the service was resident in
     *  [ServicePresence.Sentry]. The weakest nominator — it cannot tell a walk from a drive — so it
     *  arms with [ArmEvidence.Unverified] (every anti-walking guard active); only measured driving
     *  confirms. Its value is immediacy + independence: it needs neither Play Services (which OEMs
     *  starve) nor a WorkManager tick, and it lands on an already-live process. */
    SIGNIFICANT_MOTION,
}
