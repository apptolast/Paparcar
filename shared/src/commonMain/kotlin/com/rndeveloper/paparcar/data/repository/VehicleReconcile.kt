package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.data.datasource.local.room.VehicleEntity

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
    onTakeRemote = { r, l ->
        r.copy(
            licensePlate = l?.licensePlate ?: r.licensePlate,
            // [VEH-FUEL-001] The fuel block IS synced, but a remote doc written before the fields
            // travelled carries null — taking that null would silently erase what this device
            // already knows. Same shape as zoneRadiusMeters in the parking reconcile.
            fuelType = r.fuelType ?: l?.fuelType,
            consumptionPer100 = r.consumptionPer100 ?: l?.consumptionPer100,
            consumptionElectricPer100 = r.consumptionElectricPer100 ?: l?.consumptionElectricPer100,
            energyLabel = r.energyLabel ?: l?.energyLabel,
        )
    },
)
