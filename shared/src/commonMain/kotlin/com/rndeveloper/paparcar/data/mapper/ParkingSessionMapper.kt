package com.rndeveloper.paparcar.data.mapper

import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingEntity
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.RouteInferenceResolution
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.util.PolylineCodec

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
    // Legacy rows (null) predate v15 → the pre-v15 implicit default was AUTO_DETECTED. [HISTORY-DETAIL-001]
    spotType = spotType.toEnumOrDefault(SpotType.AUTO_DETECTED),
    privateZoneId = privateZoneId,
    tripMaxSpeedMps = tripMaxSpeedMps,
    provisionalDepartureAtMs = provisionalDepartureAtMs,
    armEvidence = armEvidence,
    detectionPath = detectionPath,
    zoneRadiusMeters = zoneRadiusMeters,
    routePolyline = routePolyline,
    routeSnapped = routeSnapped,
    routeInferredSpans = routeInferredSpans,
    routeInferredResolution = routeInferredResolution.toEnumOrNull<RouteInferenceResolution>(),
    routeDistanceMeters = routeDistanceMeters,
    endedAtMs = endedAtMs,
    publishedSpot = publishedSpot,
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
    spotType = spotType.name,
    privateZoneId = privateZoneId,
    // tripMaxSpeedMps: local-only (feeds the repark guard, never synced). armEvidence + detectionPath
    // ARE synced to Firestore for remote provenance diagnostics. [DET-SOLID-001][DET-PIN-PROVENANCE-001]
    tripMaxSpeedMps = tripMaxSpeedMps,
    // provisionalDepartureAtMs: local-only pending-deduced-departure marker. [DET-HANDOFF-NOT-MANUAL-001 §B]
    provisionalDepartureAtMs = provisionalDepartureAtMs,
    armEvidence = armEvidence,
    detectionPath = detectionPath,
    // zoneRadiusMeters: local-only honest-close artifact — round-trips Room, never synced (an
    // unrefined approximate zone stays on the device that detected it). [DET-HONEST-CLOSE-001]
    zoneRadiusMeters = zoneRadiusMeters,
    // The driven route round-trips Room and syncs to Firestore; its inferred-stretch provenance and
    // the user's verdict travel with it. [DET-ROUTE-TRACK-001][ROUTE-GAP-HONEST-001]
    routePolyline = routePolyline,
    routeSnapped = routeSnapped,
    routeInferredSpans = routeInferredSpans,
    routeInferredResolution = routeInferredResolution?.name,
    // Close provenance + route length round-trip Room and sync to Firestore.
    // [VEH-STATS-SAY-SOMETHING-USEFUL-001]
    routeDistanceMeters = routeDistanceMeters,
    endedAtMs = endedAtMs,
    publishedSpot = publishedSpot,
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
    spotType = spotType.name,
    // Provenance: the ARM trigger + the confirmation PATH that placed this pin — mirrored so a
    // remote diagnostic can attribute a parking to its trigger. [DET-PIN-PROVENANCE-001]
    armEvidence = armEvidence,
    detectionPath = detectionPath,
    // [DET-DOUBT-REACHES-REMOTE-001] The doubt travels too. A zone that reads as an exact pin in
    // remote is a measurement error at the analysis layer, and it cost a cable to work around.
    zoneRadiusMeters = zoneRadiusMeters,
    // The driven route travels to Firestore so the trip renders in history on any device, with the
    // inferred-stretch provenance + verdict. [DET-ROUTE-TRACK-001][ROUTE-GAP-HONEST-001]
    routePolyline = routePolyline,
    routeSnapped = routeSnapped,
    routeInferredSpans = routeInferredSpans,
    routeInferredResolution = routeInferredResolution?.name,
    routeDistanceMeters = routeDistanceMeters,
    endedAtMs = endedAtMs,
    publishedSpot = publishedSpot,
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
    spotType = spotType,
    // Provenance now round-trips through Firestore (armEvidence + detectionPath); an inbound pin
    // keeps who/what placed it. tripMaxSpeedMps stays local-only → null here. [DET-PIN-PROVENANCE-001]
    armEvidence = armEvidence,
    detectionPath = detectionPath,
    // [DET-DOUBT-REACHES-REMOTE-001] …and comes back, so a restored pin is still honest about
    // being an area. Legacy docs carry no field → null → an exact pin, which is what they were.
    zoneRadiusMeters = zoneRadiusMeters,
    // The driven route comes back from Firestore so a new device renders the trip, with the
    // inferred-stretch provenance + verdict. [DET-ROUTE-TRACK-001][ROUTE-GAP-HONEST-001]
    routePolyline = routePolyline,
    routeSnapped = routeSnapped,
    routeInferredSpans = routeInferredSpans,
    routeInferredResolution = routeInferredResolution,
    // Legacy docs predate the persisted length → recompute from the polyline they DO carry, so a
    // restore self-heals instead of undercounting km forever. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
    routeDistanceMeters = routeDistanceMeters ?: PolylineCodec.lengthMeters(routePolyline),
    endedAtMs = endedAtMs,
    publishedSpot = publishedSpot,
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
