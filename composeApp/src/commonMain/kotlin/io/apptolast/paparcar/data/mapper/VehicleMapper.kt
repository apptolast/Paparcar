package io.apptolast.paparcar.data.mapper

import dev.gitlive.firebase.firestore.DocumentSnapshot
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

/**
 * Reads a Firestore vehicle document into [VehicleEntity] field-by-field, matching the
 * pattern used by [RemoteUserProfileDataSourceImpl.toParkingHistoryDto]. The previous
 * implementation called `.data<Map<String, Any?>>()` which throws at runtime because
 * kotlinx-serialization has no compiled serializer for `Any` — that path silently
 * killed every `VehicleRepository.syncFromRemote` since it was added. [VEHICLE-SYNC-DESERIAL-001]
 *
 * Returns null when required fields (id, userId) are missing or the document fails to
 * deserialize. `bluetoothDeviceId` is intentionally not read: it lives on-device only.
 */
fun DocumentSnapshot.toVehicleEntity(): VehicleEntity? = runCatching {
    val docId = runCatching { get<String?>("id") }.getOrNull() ?: id
    val docUserId = runCatching { get<String?>("userId") }.getOrNull() ?: return@runCatching null
    VehicleEntity(
        id = docId,
        userId = docUserId,
        brand = runCatching { get<String?>("brand") }.getOrNull(),
        model = runCatching { get<String?>("model") }.getOrNull(),
        sizeCategory = runCatching { get<String?>("sizeCategory") }.getOrNull() ?: VehicleSize.MEDIUM.name,
        bluetoothDeviceId = null,  // on-device only — never read from Firestore
        showBrandModelOnSpot = runCatching { get<Boolean?>("showBrandModelOnSpot") }.getOrNull() ?: false,
        isDefault = runCatching { get<Boolean?>("isDefault") }.getOrNull() ?: false,
    )
}.getOrNull()
