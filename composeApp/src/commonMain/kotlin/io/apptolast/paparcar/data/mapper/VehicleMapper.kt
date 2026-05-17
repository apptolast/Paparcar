package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.VehicleEntity
import io.apptolast.paparcar.data.datasource.remote.dto.VehicleDto
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

// ── VehicleDto → Entity (sync from Firestore) ──────────────────────────────

fun VehicleDto.toEntity(): VehicleEntity = VehicleEntity(
    id = id,
    userId = userId,
    brand = brand,
    model = model,
    sizeCategory = sizeCategory,
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isDefault = isDefault,
)

// ── Domain → VehicleDto (write to Firestore) ────────────────────────────────

fun Vehicle.toDto(): VehicleDto = VehicleDto(
    id = id,
    userId = userId,
    brand = brand,
    model = model,
    sizeCategory = sizeCategory.name,
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isDefault = isDefault,
)
