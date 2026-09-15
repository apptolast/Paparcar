package com.rndeveloper.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class VehicleDto(
    val id: String = "",
    val userId: String = "",
    /** Private — never read from Firestore on-device-only, but deserialized gracefully if present. */
    val name: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val sizeCategory: String = "",
    /** [CarbodyType] enum name. Empty string when the vehicle is non-CAR or pre-feature. */
    val carbodyType: String = "",
    /** [VehicleType] enum name. Empty string for pre-feature rows; mappers default to "CAR". */
    val vehicleType: String = "",
    val bluetoothDeviceId: String? = null,
    val showBrandModelOnSpot: Boolean = false,
    val isActive: Boolean = false,
    /** [VehicleColor] enum name. Empty string when undefined (default green). */
    val color: String = "",
    /** [FuelType] enum name. Empty string for human-powered vehicles and pre-feature docs. [VEH-FUEL-001] */
    val fuelType: String = "",
    /** User consumption override (l or kWh /100 km); null ⇒ FuelDefaults applies. [VEH-FUEL-001] */
    val consumptionPer100: Float? = null,
    /** PHEV-only secondary kWh/100 km override. [VEH-FUEL-001] */
    val consumptionElectricPer100: Float? = null,
    val fuelPricePerLiter: Double? = null,
    val energyPricePerKwh: Double? = null,
    val priceCurrency: String? = null,
    /** [EnergyLabel] enum name. Empty string when not answered. [VEH-FUEL-001] */
    val energyLabel: String = "",
    /** Mirror id when this car is shared with a group; null otherwise. [VEH-SHARED-001] */
    val sharedCarId: String? = null,
    /** Owner of a car shared WITH this user (a linked row); null on own cars. [VEH-SHARED-001] */
    val ownerUid: String? = null,
    /** Whether this user's routes in this car are visible to the group. [VEH-SHARED-001] */
    val shareRoutesInGroup: Boolean = true,
    /**
     * Client epoch-ms stamp of the last write, set by the remote data source on every vehicle write.
     * Read back by the inbound sync so the merge can tell when the server has caught up with a local
     * pending edit (self-healing Last-Write-Wins). 0 for pre-feature docs. [SYNC-RECONCILE-001]
     */
    val updatedAt: Long = 0,
)
