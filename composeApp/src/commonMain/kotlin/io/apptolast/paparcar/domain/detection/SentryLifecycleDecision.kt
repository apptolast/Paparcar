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

/**
 * [DET-RESIDENT-FGS-001 · F2] Pure verdict for a sentry-residency stamp found on disk. The service
 * stamps "I am resident" on enterSentry and clears it on every DELIBERATE exit (SENTRY→ACTIVE wake,
 * idle teardown), so a stamp that outlives the process without one of those exits is evidence the OS
 * killed the resident watcher — unless a reboot explains it innocently (consistent with the
 * BackgroundKillSuspected heuristic, which also skips reboots).
 *
 * Shared by BOTH detection lanes so they cannot diverge: the safety-net worker's periodic tick
 * (which can also measure the heartbeat gap = the dark window) and the service's own arm path
 * (a trigger reviving a dead process with the stamp still set IS the kill being witnessed live).
 */
sealed interface SentryKillVerdict {
    /** No residency expected, or the sentry is alive and well — nothing to do. */
    data object None : SentryKillVerdict

    /** The residency ended innocently (reboot, or a live job missed the handoff) — clear the
     *  stamp silently, log nothing. */
    data object ClearStamp : SentryKillVerdict

    /** The resident watcher died without a deliberate exit — log `sentry killed` + clear the stamp. */
    data object Killed : SentryKillVerdict
}

fun resolveSentryKillVerdict(
    residencyExpected: Boolean,
    presence: ServicePresence,
    rebootedSince: Boolean,
): SentryKillVerdict = when {
    !residencyExpected -> SentryKillVerdict.None
    presence == ServicePresence.Sentry -> SentryKillVerdict.None
    rebootedSince -> SentryKillVerdict.ClearStamp
    presence == ServicePresence.Active -> SentryKillVerdict.ClearStamp
    else -> SentryKillVerdict.Killed
}
