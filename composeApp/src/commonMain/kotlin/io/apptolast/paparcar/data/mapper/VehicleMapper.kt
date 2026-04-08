package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.VehicleEntity
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize

fun VehicleEntity.toDomain(): Vehicle = Vehicle(
    id = id,
    userId = userId,
    brand = brand,
    model = model,
    sizeCategory = runCatching { VehicleSize.valueOf(sizeCategory) }.getOrDefault(VehicleSize.MEDIUM),
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isDefault = isDefault,
)

fun Vehicle.toEntity(): VehicleEntity = VehicleEntity(
    id = id,
    userId = userId,
    brand = brand,
    model = model,
    sizeCategory = sizeCategory.name,
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isDefault = isDefault,
)

/** Maps a raw Firestore document map to [VehicleEntity]. Returns null if required fields are missing. */
fun Map<String, Any?>.toVehicleEntity(): VehicleEntity? {
    val id = this["id"] as? String ?: return null
    val userId = this["userId"] as? String ?: return null
    val sizeCategory = this["sizeCategory"] as? String ?: VehicleSize.MEDIUM.name
    return VehicleEntity(
        id = id,
        userId = userId,
        brand = this["brand"] as? String,
        model = this["model"] as? String,
        sizeCategory = sizeCategory,
        bluetoothDeviceId = null, // on-device only — never in Firestore
        showBrandModelOnSpot = this["showBrandModelOnSpot"] as? Boolean ?: false,
        isDefault = this["isDefault"] as? Boolean ?: false,
    )
}
