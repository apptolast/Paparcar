package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.VehicleEntity
import io.apptolast.paparcar.data.datasource.remote.dto.VehicleDto
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleColor
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType

fun VehicleEntity.toDomain(): Vehicle = Vehicle(
    id = id,
    userId = userId,
    name = name,
    brand = brand,
    model = model,
    // Legacy/blank rows synced from a Firestore doc that pre-dated VEHICLE-CATEGORIZATION-001
    // get a safe MEDIUM_SUV fallback so the rest of the app (geofence sizing, peek fit pill,
    // marker icons) keeps working instead of crashing on VehicleSize.valueOf("").
    sizeCategory = sizeCategory.toEnumOrDefault(VehicleSize.MEDIUM_SUV),
    carbodyType = carbodyType.toEnumOrNull<CarbodyType>(),
    vehicleType = vehicleType.toEnumOrDefault(VehicleType.CAR),
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isActive = isActive,
    licensePlate = licensePlate,
    color = VehicleColor.fromNameOrNull(color),
)

fun Vehicle.toEntity(): VehicleEntity = VehicleEntity(
    id = id,
    userId = userId,
    name = name,
    brand = brand,
    model = model,
    sizeCategory = sizeCategory.name,
    carbodyType = carbodyType?.name,
    vehicleType = vehicleType.name,
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isActive = isActive,
    licensePlate = licensePlate,
    color = color?.name,
)

// ── VehicleDto → Entity (sync from Firestore) ──────────────────────────────
// `vehicleType` falls back to "CAR" so the NOT NULL Room column always holds a
// valid enum name. `carbodyType` is allowed to remain null for non-CAR vehicles.
// licensePlate is on-device only — never synced from Firestore.

fun VehicleDto.toEntity(): VehicleEntity = VehicleEntity(
    id = id,
    userId = userId,
    name = name,
    brand = brand,
    model = model,
    sizeCategory = sizeCategory,
    carbodyType = carbodyType.ifBlank { null },
    vehicleType = vehicleType.ifBlank { VehicleType.CAR.name },
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isActive = isActive,
    licensePlate = null,
    color = color.ifBlank { null },
)

// ── Domain → VehicleDto (write to Firestore) ────────────────────────────────
// licensePlate is on-device only — intentionally omitted from Firestore writes.

fun Vehicle.toDto(): VehicleDto = VehicleDto(
    id = id,
    userId = userId,
    name = name,
    brand = brand,
    model = model,
    sizeCategory = sizeCategory.name,
    carbodyType = carbodyType?.name ?: "",
    vehicleType = vehicleType.name,
    bluetoothDeviceId = bluetoothDeviceId,
    showBrandModelOnSpot = showBrandModelOnSpot,
    isActive = isActive,
    color = color?.name ?: "",
)
