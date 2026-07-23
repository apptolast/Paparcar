package io.apptolast.paparcar.domain.detection

/**
 * [DET-FGS-REAPER-001] Pure decision: may the safety net reap the GHOST detection foreground-service
 * notification (`DETECTION_NOTIFICATION_ID`) this tick?
 *
 * A stale pending means a coordinator session armed and its process died before the service's finally
 * could tear down — exactly when the foreground service leaves its detection notification glued to a
 * dead/frozen process (ColorOS freezes rather than kills, so Android never reaps it; field 2026-07-21,
 * Oppo: a false-enter FGS hung ~2 h while the user walked). Reaping only DISMISSES an orphan
 * notification — it never kills a process.
 *
 * Two independent locks make this NEVER touch a LIVE session's notification:
 *  - **[isDetectionRunning] false.** A live coordinator session sets the runtime flag true at arm and
 *    false in its finally, so a running session vetoes the reap. A fresh pending is never stale, but an
 *    OLD stale pending must not collateral-kill a NEW live session's FGS — this lock forbids it.
 *  - **[isPeriodicTick] true.** The worker posts its OWN copy of the notification (via getForegroundInfo)
 *    only on EXPEDITED runs. The periodic tick runs in the background and never posts it, so a live
 *    notification there is ALWAYS the ghost. Expedited runs already self-heal the ghost as a side effect
 *    (WorkManager posts on promote, removes on finish), so they must NOT reap — dodging that collision.
 *
 * @param isPeriodicTick     true only for the background 15-min periodic wake (SOURCE_PERIODIC).
 * @param isDetectionRunning the in-memory runtime flag — true while a coordinator session is alive.
 * @param hasStalePending    a pending whose heartbeat went stale exists (the process-death signal).
 */
fun shouldReapGhostDetectionFgs(
    isPeriodicTick: Boolean,
    isDetectionRunning: Boolean,
    hasStalePending: Boolean,
): Boolean = hasStalePending && isPeriodicTick && !isDetectionRunning
