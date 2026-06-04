package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.VehicleEntity
import io.apptolast.paparcar.data.datasource.remote.dto.VehicleDto
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType

fun VehicleEntity.toDomain(): Vehicle = Vehicle(
    id = id,
    userId = userId,
    name = name,
    brand = brand,
    model = model,
    sizeCategory = runCatching { VehicleSize.valueOf(sizeCategory) }.getOrDefault(VehicleSize.MEDIUM),
    vehicleType = runCatching { VehicleType.valueOf(vehicleType) }.getOrDefault(VehicleType.CAR),
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isActive = isActive,
)

fun Vehicle.toEntity(): VehicleEntity = VehicleEntity(
    id = id,
    userId = userId,
    name = name,
    brand = brand,
    model = model,
    sizeCategory = sizeCategory.name,
    vehicleType = vehicleType.name,
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isActive = isActive,
)

// ── VehicleDto → Entity (sync from Firestore) ──────────────────────────────
// Older Firestore rows may not have the vehicleType field — fall back to "CAR"
// so the entity's NOT NULL column always holds a valid enum name.

fun VehicleDto.toEntity(): VehicleEntity = VehicleEntity(
    id = id,
    userId = userId,
    name = name,
    brand = brand,
    model = model,
    sizeCategory = sizeCategory,
    vehicleType = vehicleType.ifBlank { VehicleType.CAR.name },
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isActive = isActive,
)

// ── Domain → VehicleDto (write to Firestore) ────────────────────────────────

fun Vehicle.toDto(): VehicleDto = VehicleDto(
    id = id,
    userId = userId,
    name = name,
    brand = brand,
    model = model,
    sizeCategory = sizeCategory.name,
    vehicleType = vehicleType.name,
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isActive = isActive,
)
