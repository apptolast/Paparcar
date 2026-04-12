@file:OptIn(kotlin.time.ExperimentalTime::class)

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
import kotlin.time.Clock

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
    confidence = decayedConfidence(
        storedConfidence = confidence.coerceIn(0f, 1f),
        acceptCount = acceptCount,
        rejectCount = rejectCount,
        reportedAt = reportedAt,
        expiresAt = expiresAt,
        nowMs = Clock.System.now().toEpochMilliseconds(),
    ),
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
    placeInfoName = placeInfo?.name,
    placeInfoCategory = placeInfo?.category,
    type = type,
    confidence = confidence,
    sizeCategory = sizeCategory,
    enRouteCount = enRouteCount,
    expiresAt = expiresAt,
    acceptCount = acceptCount,
    rejectCount = rejectCount,
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
    placeInfo = placeInfoName?.let { name ->
        placeInfoCategory?.let { cat ->
            runCatching { PlaceInfo(name, PlaceCategory.valueOf(cat)) }.getOrNull()
        }
    },
    type = runCatching { SpotType.valueOf(type) }.getOrDefault(SpotType.AUTO_DETECTED),
    confidence = decayedConfidence(
        storedConfidence = confidence.coerceIn(0f, 1f),
        acceptCount = acceptCount,
        rejectCount = rejectCount,
        reportedAt = reportedAt,
        expiresAt = expiresAt,
        nowMs = Clock.System.now().toEpochMilliseconds(),
    ),
    sizeCategory = sizeCategory?.let { runCatching { VehicleSize.valueOf(it) }.getOrNull() },
    enRouteCount = enRouteCount.coerceAtLeast(0),
    expiresAt = expiresAt,
)

// ─── Confidence decay ─────────────────────────────────────────────────────────
//
// Final confidence = communityConfidence * timeFactor
//
// communityConfidence: Laplace-smoothed vote ratio. Uses storedConfidence when
//   total votes < MIN_VOTES_FOR_SIGNAL (avoids flip-flopping on a single vote).
//
// timeFactor: linear decay from 1.0 (just reported) → 0.0 (at TTL expiry).
//   Stays 1.0 when expiresAt = 0 (no TTL set).

internal fun decayedConfidence(
    storedConfidence: Float,
    acceptCount: Int,
    rejectCount: Int,
    reportedAt: Long,
    expiresAt: Long,
    nowMs: Long,
): Float {
    val totalVotes = acceptCount + rejectCount
    val communityConfidence = if (totalVotes >= MIN_VOTES_FOR_SIGNAL) {
        (acceptCount.toFloat() + LAPLACE_PRIOR) / (totalVotes.toFloat() + 2f * LAPLACE_PRIOR)
    } else {
        storedConfidence
    }
    val timeFactor = if (reportedAt in 1L until expiresAt) {
        val total = (expiresAt - reportedAt).toFloat()
        val remaining = (expiresAt - nowMs).coerceAtLeast(0L).toFloat()
        remaining / total
    } else {
        1f
    }
    return (communityConfidence * timeFactor).coerceIn(0f, 1f)
}

private const val MIN_VOTES_FOR_SIGNAL = 3
private const val LAPLACE_PRIOR = 1f
