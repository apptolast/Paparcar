package com.rndeveloper.paparcar.domain.entitlement

import com.rndeveloper.paparcar.domain.model.Vehicle

/**
 * Whether the user may ADD another own vehicle, decided in ONE place. [FREE-LIMITS-001]
 *
 * Free keeps ONE own car; Plus has no cap. Only the ADD is gated — every vehicle that already
 * exists keeps working (and detecting) whatever the count: losing Plus never deletes or disables
 * anything, it only gates what is NEW.
 *
 * ⛔ **"Own" excludes a car somebody shared with you** ([Vehicle.isLinked], `ownerUid != null`) —
 * the hole this KDoc reserved for VEH-SHARED-001, now filled. Counting a linked car here would
 * make accepting an invitation spend the free user's only slot, which is the opposite of the deal:
 * the OWNER's Plus pays for the group. Callers pass [ownedCount] through [countOwnVehicles] rather
 * than `vehicles.size` — the sum is the gate's business, not each screen's.
 */
fun canAddOwnVehicle(ownedCount: Int, isPlus: Boolean): Boolean =
    isPlus || ownedCount < FREE_OWN_VEHICLE_LIMIT

/** How many of these cars are the user's OWN, for [canAddOwnVehicle]. [VEH-SHARED-001] */
fun countOwnVehicles(vehicles: List<Vehicle>): Int = vehicles.count { !it.isLinked }

const val FREE_OWN_VEHICLE_LIMIT = 1
