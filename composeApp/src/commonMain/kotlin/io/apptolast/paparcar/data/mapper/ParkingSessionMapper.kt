package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.UserParkingEntity
import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize

// ── UserParkingEntity → Domain ────────────────────────────────────────────────

fun UserParkingEntity.toDomain(): UserParking = UserParking(
    id = id,
    userId = userId,
    vehicleId = vehicleId,
    location = GpsPoint(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = timestamp,
        speed = 0f,
    ),
    spotId = spotId,
    geofenceId = geofenceId,
    isActive = isActive,
    address = addressOrNull(),
    placeInfo = placeInfoOrNull(),
    detectionReliability = detectionReliability,
    sizeCategory = sizeCategory?.let { runCatching { VehicleSize.valueOf(it) }.getOrNull() },
    carbodyType = carbodyType?.let { runCatching { CarbodyType.valueOf(it) }.getOrNull() },
    privateZoneId = privateZoneId,
)

private fun UserParkingEntity.addressOrNull(): AddressInfo? =
    if (addressStreet != null || addressCity != null || addressRegion != null || addressCountry != null) {
        AddressInfo(
            street = addressStreet,
            city = addressCity,
            region = addressRegion,
            country = addressCountry,
            countryCode = addressCountryCode,
        )
    } else null

private fun UserParkingEntity.placeInfoOrNull(): PlaceInfo? {
    val name = placeInfoName ?: return null
    val cat = placeInfoCategory ?: return null
    return runCatching { PlaceInfo(name, PlaceCategory.valueOf(cat)) }.getOrNull()
}

// ── Domain → UserParkingEntity ────────────────────────────────────────────────

fun UserParking.toEntity(): UserParkingEntity = UserParkingEntity(
    id = id,
    userId = userId,
    vehicleId = vehicleId,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracy = location.accuracy,
    timestamp = location.timestamp,
    spotId = spotId,
    geofenceId = geofenceId,
    isActive = isActive,
    addressStreet = address?.street,
    addressCity = address?.city,
    addressRegion = address?.region,
    addressCountry = address?.country,
    addressCountryCode = address?.countryCode,
    placeInfoName = placeInfo?.name,
    placeInfoCategory = placeInfo?.category?.name,
    detectionReliability = detectionReliability,
    sizeCategory = sizeCategory?.name,
    carbodyType = carbodyType?.name,
    privateZoneId = privateZoneId,
)

// ── Domain → Spot (when user departs, spot is published for others) ───────────

fun UserParking.toSpot(): Spot = Spot(
    id = id,
    location = location,
    reportedBy = userId,
    address = address,
    placeInfo = placeInfo,
    sizeCategory = sizeCategory,
    carbodyType = carbodyType,
)

// ── Domain → ParkingHistoryDto (write-through to Firestore) ──────────────────

fun UserParking.toParkingHistoryDto() = ParkingHistoryDto(
    id = id,
    userId = userId,
    vehicleId = vehicleId,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracy = location.accuracy,
    timestamp = location.timestamp,
    isActive = isActive,
    spotId = spotId,
    geofenceId = geofenceId,
    address = address?.toAddressDto(),
    placeInfo = placeInfo?.toPlaceInfoDto(),
    detectionReliability = detectionReliability,
    sizeCategory = sizeCategory?.name,
    carbodyType = carbodyType?.name,
)

// ── ParkingHistoryDto → Entity (sync from Firestore on new device) ────────────

fun ParkingHistoryDto.toEntity() = UserParkingEntity(
    id = id,
    userId = userId,
    vehicleId = vehicleId,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    timestamp = timestamp,
    isActive = isActive,
    spotId = spotId,
    geofenceId = geofenceId,
    addressStreet = address?.street,
    addressCity = address?.city,
    addressRegion = address?.region,
    addressCountry = address?.country,
    addressCountryCode = address?.countryCode,
    placeInfoName = placeInfo?.name,
    placeInfoCategory = placeInfo?.category,
    detectionReliability = detectionReliability,
    sizeCategory = sizeCategory,
    carbodyType = carbodyType,
)

// ── Shared DTO helpers ────────────────────────────────────────────────────────

fun AddressInfo.toAddressDto() = AddressDto(
    street = street,
    city = city,
    region = region,
    country = country,
    countryCode = countryCode,
)

fun PlaceInfo.toPlaceInfoDto() = PlaceInfoDto(
    name = name,
    category = category.name,
)
