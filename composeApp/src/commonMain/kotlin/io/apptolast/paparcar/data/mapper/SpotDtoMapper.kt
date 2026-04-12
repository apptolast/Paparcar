package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.SpotEntity
import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize

fun SpotDto.toDomain(): Spot = Spot(
    id = id,
    location = GpsPoint(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = reportedAt,
        speed = speed,
    ),
    reportedBy = reportedBy,
    address = address?.let {
        AddressInfo(street = it.street, city = it.city, region = it.region, country = it.country)
    },
    placeInfo = placeInfo?.let { dto ->
        runCatching { PlaceInfo(dto.name, PlaceCategory.valueOf(dto.category)) }.getOrNull()
    },
    type = runCatching { SpotType.valueOf(type) }.getOrDefault(SpotType.AUTO_DETECTED),
    confidence = confidence.coerceIn(0f, 1f),
    sizeCategory = sizeCategory?.let { runCatching { VehicleSize.valueOf(it) }.getOrNull() },
    enRouteCount = enRouteCount.coerceAtLeast(0),
    expiresAt = expiresAt,
)

fun Spot.toDto(): SpotDto = SpotDto(
    id = id,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracy = location.accuracy,
    reportedAt = location.timestamp,
    reportedBy = reportedBy,
    speed = location.speed,
    address = address?.let {
        AddressDto(street = it.street, city = it.city, region = it.region, country = it.country)
    },
    placeInfo = placeInfo?.let { PlaceInfoDto(name = it.name, category = it.category.name) },
    type = type.name,
    confidence = confidence,
    sizeCategory = sizeCategory?.name,
    enRouteCount = enRouteCount,
    expiresAt = expiresAt,
)

// ─── SpotDto ↔ SpotEntity ────────────────────────────────────────────────────

fun SpotDto.toEntity(): SpotEntity = SpotEntity(
    id = id,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    reportedAt = reportedAt,
    reportedBy = reportedBy,
    speed = speed,
    addressStreet = address?.street,
    addressCity = address?.city,
    addressRegion = address?.region,
    addressCountry = address?.country,
    placeInfoName = placeInfo?.name,
    placeInfoCategory = placeInfo?.category,
    type = type,
    confidence = confidence,
    sizeCategory = sizeCategory,
    enRouteCount = enRouteCount,
    expiresAt = expiresAt,
)

fun SpotEntity.toDomain(): Spot = Spot(
    id = id,
    location = GpsPoint(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = reportedAt,
        speed = speed,
    ),
    reportedBy = reportedBy,
    address = if (addressStreet != null || addressCity != null || addressRegion != null || addressCountry != null)
        AddressInfo(street = addressStreet, city = addressCity, region = addressRegion, country = addressCountry)
    else null,
    placeInfo = if (placeInfoName != null && placeInfoCategory != null)
        runCatching { PlaceInfo(placeInfoName, PlaceCategory.valueOf(placeInfoCategory)) }.getOrNull()
    else null,
    type = runCatching { SpotType.valueOf(type) }.getOrDefault(SpotType.AUTO_DETECTED),
    confidence = confidence.coerceIn(0f, 1f),
    sizeCategory = sizeCategory?.let { runCatching { VehicleSize.valueOf(it) }.getOrNull() },
    enRouteCount = enRouteCount.coerceAtLeast(0),
    expiresAt = expiresAt,
)
