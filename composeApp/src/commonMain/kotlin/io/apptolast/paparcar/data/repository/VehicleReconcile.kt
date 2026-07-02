package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.VehicleEntity

/**
 * Merges the local Room rows with the remote (Firestore) snapshot WITHOUT clobbering un-synced local
 * edits — the fix for the blind remote-wins overwrite (`deleteByUser` + `upsertAll`) that reverted an
 * offline change (most visibly the active vehicle) on the next cold-start sync. Delegates to the
 * shared [reconcilePending]; on-device-only fields (`licensePlate`) are carried over when taking
 * remote. The caller re-applies the single-active invariant. [SYNC-RECONCILE-001]
 */
fun reconcileVehicles(
    local: List<VehicleEntity>,
    remote: List<VehicleEntity>,
): List<VehicleEntity> = reconcilePending(
    local = local,
    remote = remote,
    id = { it.id },
    updatedAt = { it.updatedAt },
    pendingSync = { it.pendingSync },
    onTakeRemote = { r, l -> r.copy(licensePlate = l?.licensePlate ?: r.licensePlate) },
)
