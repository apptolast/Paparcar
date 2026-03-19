package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.UserParkingEntity
import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking

// ── UserParkingEntity → Domain ────────────────────────────────────────────────

fun UserParkingEntity.toDomain(): UserParking = UserParking(
    id = id,
    userId = userId,
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
)

private fun UserParkingEntity.addressOrNull(): AddressInfo? =
    if (addressStreet != null || addressCity != null || addressRegion != null || addressCountry != null) {
        AddressInfo(
            street = addressStreet,
            city = addressCity,
            region = addressRegion,
            country = addressCountry,
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
    placeInfoName = placeInfo?.name,
    placeInfoCategory = placeInfo?.category?.name,
)

// ── Domain → Spot (when user departs, spot is published for others) ───────────

fun UserParking.toSpot(): Spot = Spot(
    id = id,
    location = location,
    reportedBy = userId,
    address = address,
    placeInfo = placeInfo,
)

// ── Domain → ParkingHistoryDto (write-through to Firestore) ──────────────────

fun UserParking.toParkingHistoryDto() = ParkingHistoryDto(
    id = id,
    userId = userId,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracy = location.accuracy,
    timestamp = location.timestamp,
    isActive = isActive,
    spotId = spotId,
    geofenceId = geofenceId,
    address = address?.toAddressDto(),
    placeInfo = placeInfo?.toPlaceInfoDto(),
)

// ── ParkingHistoryDto → Entity (sync from Firestore on new device) ────────────

fun ParkingHistoryDto.toEntity() = UserParkingEntity(
    id = id,
    userId = userId,
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
    placeInfoName = placeInfo?.name,
    placeInfoCategory = placeInfo?.category,
    detectionReliability = detectionReliability,
)

// ── Shared DTO helpers ────────────────────────────────────────────────────────

fun AddressInfo.toAddressDto() = AddressDto(
    street = street,
    city = city,
    region = region,
    country = country,
)

fun PlaceInfo.toPlaceInfoDto() = PlaceInfoDto(
    name = name,
    category = category.name,
)
