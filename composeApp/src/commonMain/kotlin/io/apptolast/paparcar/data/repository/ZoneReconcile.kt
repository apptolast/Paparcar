package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.ZoneEntity

/**
 * Merges local Room zones with the remote (Firestore) snapshot without clobbering un-synced local
 * edits — same fix as vehicles for the blind remote-wins overwrite. Zones have no on-device-only
 * fields, so remote is taken as-is. Delegates to the shared [reconcilePending]. [SYNC-RECONCILE-001]
 */
fun reconcileZones(
    local: List<ZoneEntity>,
    remote: List<ZoneEntity>,
): List<ZoneEntity> = reconcilePending(
    local = local,
    remote = remote,
    id = { it.id },
    updatedAt = { it.updatedAt },
    pendingSync = { it.pendingSync },
)
