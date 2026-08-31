package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingEntity

/**
 * Merges local Room parking sessions with the remote (Firestore) snapshot WITHOUT clobbering an
 * un-synced local edit — the systemic fix for the blind `upsertAll` that let a stale remote
 * `isActive=true` resurrect an ended session on the next cold-start (field incident 2026-07-05,
 * Redmi) and left two "active" sessions mirrored in the cloud. [SYNC-RECONCILE-USERPARKING-001]
 *
 * Delegates to the shared [reconcilePending]: a local row that is [pendingSync] AND strictly newer
 * than remote wins (local is authoritative and hasn't been confirmed to the server yet); otherwise
 * remote wins, but the detection provenance ([tripMaxSpeedMps] is local-only; [armEvidence] +
 * [detectionPath] now sync but are still carried from local as a legacy safety) is preserved from the
 * local row so a remote-wins merge against a pre-provenance snapshot doesn't blank it. Once the server
 * catches up
 * (remote.updatedAt >= local) the row is taken from remote and the pending flag self-heals.
 *
 * The caller re-applies the one-active-session-per-vehicle invariant on the result.
 */
fun reconcileParkingSessions(
    local: List<UserParkingEntity>,
    remote: List<UserParkingEntity>,
): List<UserParkingEntity> = reconcilePending(
    local = local,
    remote = remote,
    id = { it.id },
    updatedAt = { it.updatedAt },
    pendingSync = { it.pendingSync },
    onTakeRemote = { r, l ->
        r.copy(
            tripMaxSpeedMps = l?.tripMaxSpeedMps ?: r.tripMaxSpeedMps,
            // [DET-HANDOFF-NOT-MANUAL-001 §B] Local-only marker: the remote never carries it, so a
            // sync must not erase a pending deduced departure.
            provisionalDepartureAtMs = l?.provisionalDepartureAtMs,
            armEvidence = l?.armEvidence ?: r.armEvidence,
            detectionPath = l?.detectionPath ?: r.detectionPath,
            // [SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001] The doubt radius is synced
            // [DET-DOUBT-REACHES-REMOTE-001], but a remote doc written before it travelled carries
            // null — and taking that null silently turns a zone this device MEASURED into an exact
            // pin, because the map's target badge reads `zoneRadiusMeters != null` and nothing else.
            // That is the asymmetric-failure direction we never take: claiming more certainty than
            // we have. Field 2026-08-30: a 250 m gap-anchor zone reached Room as an exact pin.
            zoneRadiusMeters = r.zoneRadiusMeters ?: l?.zoneRadiusMeters,
            // Close provenance + route length ARE synced, but a legacy remote doc predating the
            // fields must not erase what this device already knows. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
            routeDistanceMeters = r.routeDistanceMeters ?: l?.routeDistanceMeters,
            endedAtMs = r.endedAtMs ?: l?.endedAtMs,
            publishedSpot = r.publishedSpot || l?.publishedSpot == true,
        )
    },
)
