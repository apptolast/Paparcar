package com.rndeveloper.paparcar.domain.detection.sentry

/**
 * How the parked-session heartbeat is delivered to the app. [DET-HEARTBEAT-LANE-REPAIR-001]
 *
 * Both lanes trigger the SAME check chain and neither confirms anything: waking is not evidence,
 * and the evaluator demands the same proofs whichever door the tick came through. What changes is
 * only whether the tick arrives at all on a device whose broadcast queue has stalled.
 */
enum class HeartbeatLane {

    /** `PendingIntent.getForegroundService` into the single intake [DET-INTAKE-001]. */
    SERVICE,

    /** `PendingIntent.getBroadcast` into `ExactHeartbeatReceiver` — the original lane. */
    BROADCAST,
}

/**
 * Which lane may carry the next heartbeat tick. [DET-HEARTBEAT-LANE-REPAIR-001]
 *
 * ## The measurement that made this a decision instead of a constant
 *
 * On the Oppo CPH2371 (ColorOS, Android 13) the broadcast lane **died at an exact second**:
 * 21:16:56 on 2026-08-21, both of the app's broadcast receivers at once, while the service lane
 * kept delivering for another 82 minutes. At 22:09:23 the SAME physical geofence event fired both
 * PendingIntents — `getForegroundService` for the EXIT and `getBroadcast` for the witness — and
 * only the service one arrived. Same device, same instant, same event, two mechanisms, one works.
 * The last three deliveries came as a 5-second burst carrying `fixAge` of ~2 hours: that is a
 * queue draining stale, not a lane working.
 *
 * So the fast lane moves to the door that still opens. But NOT unconditionally — two guards, and
 * both of them are about avoiding a cure worse than the disease:
 *
 * ### 1 · [canScheduleExact] — because the FGS exemption is what makes the lane legal
 *
 * Starting a foreground service from the background is restricted on Android 12+. An alarm's
 * `getForegroundService` is exempt only when the alarm is EXACT and the app holds
 * `SCHEDULE_EXACT_ALARM`. Without the special access the scheduler degrades to
 * `setAndAllowWhileIdle`, which carries NO such exemption: delivering into a process the OS has
 * killed would throw instead of waking anything. Trading a stalled lane for one that throws is not
 * a fix, so without the permission the tick stays on broadcast — the lane we know is merely slow
 * here, never broken everywhere.
 *
 * ⚠️ On a targetSdk 33+ install that access is DENIED by default and [the project decided not to
 * ask for it][DET-EXACT-HEARTBEAT-001], so `false` is the NORMAL case and most installs keep the
 * broadcast lane. That is the honest reach of this fix, and it is why the armed lane is stamped
 * into the session header: otherwise "the fix did not work" and "the fix never applied" look
 * identical from Firestore.
 *
 * ### 2 · [sentryResident] — because a tick must not cost a notification
 *
 * The heartbeat is armed from "parked and idle", which does not look at the detection strategy.
 * Under Bluetooth the sentry deliberately does NOT stay resident [DET-STRATEGY-GATE-001], so a
 * service start every 5 minutes would raise — and drop — a foreground-service notification every 5
 * minutes beside a car that is simply parked. Under Coordinator the service is already in
 * foreground and the tick is one more `onStartCommand`: no new process, no new notification, no
 * flicker. Residency is therefore not a performance detail, it is the difference between a silent
 * tick and a blinking one.
 *
 * @param canScheduleExact the app may schedule exact alarms (API < 31, or the special access granted).
 * @param sentryResident a resident SENTRY foreground service is watching the parked state.
 */
fun heartbeatDeliveryLane(canScheduleExact: Boolean, sentryResident: Boolean): HeartbeatLane =
    if (canScheduleExact && sentryResident) HeartbeatLane.SERVICE else HeartbeatLane.BROADCAST
