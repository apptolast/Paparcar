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
    sizeCategory = sizeCategory.toEnumOrNull<VehicleSize>(),
    carbodyType = carbodyType.toEnumOrNull<CarbodyType>(),
    privateZoneId = privateZoneId,
    tripMaxSpeedMps = tripMaxSpeedMps,
    armEvidence = armEvidence,
    detectionPath = detectionPath,
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
    val category = placeInfoCategory.toEnumOrNull<PlaceCategory>() ?: return null
    return PlaceInfo(name, category)
}

// ── Domain → UserParkingEntity ────────────────────────────────────────────────

/**
 * @param updatedAt   Epoch-ms of this local edit (0 keeps the legacy "no timestamp" default).
 * @param pendingSync Whether this edit still needs to reach Firestore; the inbound reconcile
 *                    protects a pending+newer row. [SYNC-RECONCILE-USERPARKING-001]
 */
fun UserParking.toEntity(updatedAt: Long = 0, pendingSync: Boolean = false): UserParkingEntity = UserParkingEntity(
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
    // tripMaxSpeedMps: local-only (feeds the repark guard, never synced). armEvidence + detectionPath
    // ARE synced to Firestore for remote provenance diagnostics. [DET-SOLID-001][DET-PIN-PROVENANCE-001]
    tripMaxSpeedMps = tripMaxSpeedMps,
    armEvidence = armEvidence,
    detectionPath = detectionPath,
    updatedAt = updatedAt,
    pendingSync = pendingSync,
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

/** @param updatedAt Epoch-ms of the local edit this write mirrors — carried to Firestore so the
 *  Last-Write-Wins merge can detect server catch-up. [SYNC-RECONCILE-USERPARKING-001] */
fun UserParking.toParkingHistoryDto(updatedAt: Long = 0L) = ParkingHistoryDto(
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
    // Provenance: the ARM trigger + the confirmation PATH that placed this pin — mirrored so a
    // remote diagnostic can attribute a parking to its trigger. [DET-PIN-PROVENANCE-001]
    armEvidence = armEvidence,
    detectionPath = detectionPath,
    updatedAt = updatedAt,
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
    // Provenance now round-trips through Firestore (armEvidence + detectionPath); an inbound pin
    // keeps who/what placed it. tripMaxSpeedMps stays local-only → null here. [DET-PIN-PROVENANCE-001]
    armEvidence = armEvidence,
    detectionPath = detectionPath,
    // A row coming FROM Firestore is by definition already synced → pendingSync=false. Its
    // updatedAt carries the remote edit time for the LWW merge. [SYNC-RECONCILE-USERPARKING-001]
    updatedAt = updatedAt,
    pendingSync = false,
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
