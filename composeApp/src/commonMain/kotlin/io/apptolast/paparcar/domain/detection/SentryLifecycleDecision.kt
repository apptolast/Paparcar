package io.apptolast.paparcar.domain.detection

/**
 * [DET-RESIDENT-FGS-001] Pure decision for what the foreground detection service does when a
 * tracking job ends and the service falls idle: DIE (today's wake-and-kill) or stay resident in
 * [ServicePresence.Sentry] (GPS off, waiting to catch the next departure on a live process).
 *
 * Kept as a pure function in commonMain so the (Android-only, race-fix-laden) service reads its fate
 * from one tested place instead of inlining the branch. F1 gates SENTRY on two things only:
 *  - `sentryEnabled` — the internal experiment flag (default OFF → always [PostDetectionLifecycle.Stop],
 *    i.e. byte-for-byte today's behaviour), and
 *  - `hasParkedSession` — residency is pointless with nothing parked to watch (after a revert / with
 *    no active session, DIE so we never leave a resident FGS with no purpose).
 *
 * Tier gating (only the "automatic" tier stays resident) is deliberately NOT here yet — it lands in
 * F3 with the settings + notification work. Keeping it out of F1 means the flag alone controls the
 * blast radius during the device experiment.
 */
enum class PostDetectionLifecycle {
    /** Tear the service down — `stopSelfResult` + onDestroy, exactly as today. */
    Stop,

    /** Keep the service alive and foreground with the GPS off. [ServicePresence.Sentry] */
    EnterSentry,
}

fun resolvePostDetectionLifecycle(
    sentryEnabled: Boolean,
    hasParkedSession: Boolean,
): PostDetectionLifecycle =
    if (sentryEnabled && hasParkedSession) PostDetectionLifecycle.EnterSentry else PostDetectionLifecycle.Stop
