package com.rndeveloper.paparcar.domain.entitlement

/**
 * Whether the user may ADD another own vehicle, decided in ONE place. [FREE-LIMITS-001]
 *
 * Free keeps ONE own car; Plus has no cap. Only the ADD is gated — every vehicle that already
 * exists keeps working (and detecting) whatever the count: losing Plus never deletes or disables
 * anything, it only gates what is NEW. "Own" today means every vehicle the user has; when shared
 * cars land (VEH-SHARED-001) a linked car carries `ownerUid != uid` and must NOT count here.
 */
fun canAddOwnVehicle(ownedCount: Int, isPlus: Boolean): Boolean =
    isPlus || ownedCount < FREE_OWN_VEHICLE_LIMIT

const val FREE_OWN_VEHICLE_LIMIT = 1
