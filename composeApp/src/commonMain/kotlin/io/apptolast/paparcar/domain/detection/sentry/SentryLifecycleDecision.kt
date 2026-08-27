package io.apptolast.paparcar.domain.detection.sentry

import io.apptolast.paparcar.domain.detection.ServicePresence

import io.apptolast.paparcar.domain.detection.ParkingStrategy

/**
 * [DET-RESIDENT-FGS-001] Pure decision for what the foreground detection service does when a
 * tracking job ends and the service falls idle: DIE (today's wake-and-kill) or stay resident in
 * [ServicePresence.Sentry] (GPS off, waiting to catch the next departure on a live process).
 *
 * Kept as a pure function in commonMain so the (Android-only, race-fix-laden) service reads its fate
 * from one tested place instead of inlining the branch. SENTRY residency requires three things:
 *  - `autoDetectEnabled` — the user's Settings auto-detect toggle. F3 decision (2026-08-06, user):
 *    the sentry has NO switch of its own — residency is HOW detection stays ready, so the existing
 *    detection toggle governs it. During F1/F2 this input was an internal experiment flag.
 *  - `hasParkedSession` — residency is pointless with nothing parked to watch (after a revert / with
 *    no active session, DIE so we never leave a resident FGS with no purpose), and
 *  - `strategy == COORDINATOR` — the resident watcher is the crutch of the PROBABILISTIC pipeline
 *    (its wake signal, significant motion, cannot be delivered to a dead process). Under
 *    [ParkingStrategy.BLUETOOTH] the deterministic ACL broadcast wakes the process by itself
 *    (manifest receiver, FGS-from-background exempt on Android 12+), so residency would only cost
 *    battery and a permanent notification; under NONE there is nothing to detect.
 *    [DET-STRATEGY-GATE-001]
 *
 * Deliberately NOT tier-gated: the sentry costs a silent minimum-importance notification and near-zero
 * battery on every tier, and the ASSISTED tiers (no BT receiver to revive a dead process) are exactly
 * where residency saves departures. Who wants no permanent notification turns detection off — one
 * concept, one switch.
 */
enum class PostDetectionLifecycle {
    /** Tear the service down — `stopSelfResult` + onDestroy, exactly as today. */
    Stop,

    /** Keep the service alive and foreground with the GPS off. [ServicePresence.Sentry] */
    EnterSentry,
}

fun resolvePostDetectionLifecycle(
    autoDetectEnabled: Boolean,
    hasParkedSession: Boolean,
    strategy: ParkingStrategy,
): PostDetectionLifecycle =
    if (autoDetectEnabled && hasParkedSession && strategy == ParkingStrategy.COORDINATOR) {
        PostDetectionLifecycle.EnterSentry
    } else {
        PostDetectionLifecycle.Stop
    }

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
