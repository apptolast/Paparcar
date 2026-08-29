package com.rndeveloper.paparcar.data.mapper

import com.rndeveloper.paparcar.data.datasource.local.room.SpotEntity
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.geohash.encodeGeohash
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SpotDto
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.VehicleSize

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
        AddressInfo(street = it.street, city = it.city, region = it.region, country = it.country, countryCode = it.countryCode)
    },
    placeInfo = placeInfo?.let { dto ->
        dto.category.toEnumOrNull<PlaceCategory>()?.let { PlaceInfo(dto.name, it) }
    },
    type = type.toEnumOrDefault(SpotType.AUTO_DETECTED),
    confidence = communityConfidence(
        storedConfidence = confidence.coerceIn(0f, 1f),
        acceptCount = acceptCount,
        rejectCount = rejectCount,
    ),
    sizeCategory = sizeCategory.toEnumOrNull<VehicleSize>(),
    carbodyType = carbodyType.toEnumOrNull<CarbodyType>(),
    enRouteCount = enRouteCount.coerceAtLeast(0),
    expiresAt = expiresAt,
    status = status.toEnumOrDefault(SpotStatus.CONFIRMED),
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
        AddressDto(street = it.street, city = it.city, region = it.region, country = it.country, countryCode = it.countryCode)
    },
    countryCode = address?.countryCode,
    citySlug = address?.city?.toCitySlug(),
    geohash = encodeGeohash(location.latitude, location.longitude),
    placeInfo = placeInfo?.let { PlaceInfoDto(name = it.name, category = it.category.name) },
    type = type.name,
    confidence = confidence,
    sizeCategory = sizeCategory?.name,
    carbodyType = carbodyType?.name,
    enRouteCount = enRouteCount,
    expiresAt = expiresAt,
    status = status.name,
    // Note: acceptCount/rejectCount are NOT written back through Spot.toDto()
    // — signals are written via FirebaseDataSource.sendSpotSignal() with FieldValue.increment.
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
    addressCountryCode = address?.countryCode,
    geohash = geohash,
    placeInfoName = placeInfo?.name,
    placeInfoCategory = placeInfo?.category,
    type = type,
    confidence = confidence,
    sizeCategory = sizeCategory,
    carbodyType = carbodyType,
    enRouteCount = enRouteCount,
    expiresAt = expiresAt,
    acceptCount = acceptCount,
    rejectCount = rejectCount,
    status = status,
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
        AddressInfo(street = addressStreet, city = addressCity, region = addressRegion, country = addressCountry, countryCode = addressCountryCode)
    else null,
    placeInfo = placeInfoName?.let { name ->
        placeInfoCategory.toEnumOrNull<PlaceCategory>()?.let { PlaceInfo(name, it) }
    },
    type = type.toEnumOrDefault(SpotType.AUTO_DETECTED),
    confidence = communityConfidence(
        storedConfidence = confidence.coerceIn(0f, 1f),
        acceptCount = acceptCount,
        rejectCount = rejectCount,
    ),
    sizeCategory = sizeCategory.toEnumOrNull<VehicleSize>(),
    carbodyType = carbodyType.toEnumOrNull<CarbodyType>(),
    enRouteCount = enRouteCount.coerceAtLeast(0),
    expiresAt = expiresAt,
    status = status.toEnumOrDefault(SpotStatus.CONFIRMED),
)

// ─── Community confidence ─────────────────────────────────────────────────────
//
// What the COMMUNITY thinks of this report, and nothing else: a Laplace-smoothed vote ratio,
// falling back to storedConfidence while total votes < MIN_VOTES_FOR_SIGNAL (avoids flip-flopping
// on a single vote).
//
// [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001] This used to be multiplied by a `timeFactor` that
// decayed linearly to zero at TTL expiry, so one Float carried both "do people believe this
// report" and "how old is it". The age half now lives in SpotFreshness, which is the single ramp
// the UI colours itself from — leaving it here too would count the clock twice.

internal fun communityConfidence(
    storedConfidence: Float,
    acceptCount: Int,
    rejectCount: Int,
): Float {
    val totalVotes = acceptCount + rejectCount
    val confidence = if (totalVotes >= MIN_VOTES_FOR_SIGNAL) {
        (acceptCount.toFloat() + LAPLACE_PRIOR) / (totalVotes.toFloat() + 2f * LAPLACE_PRIOR)
    } else {
        storedConfidence
    }
    return confidence.coerceIn(0f, 1f)
}

private const val MIN_VOTES_FOR_SIGNAL = 3
private const val LAPLACE_PRIOR = 1f

// Produces a stable, lowercase ASCII slug from a city name for Firestore indexing.
// Accented characters are stripped (not transliterated) — consistent on write and read.
// Examples: "Madrid" → "madrid", "New York" → "new-york", "São Paulo" → "so-paulo"
internal fun String.toCitySlug(): String =
    lowercase().trim()
        .replace(Regex("\\s+"), "-")
        .replace(Regex("[^a-z0-9\\-]"), "")
