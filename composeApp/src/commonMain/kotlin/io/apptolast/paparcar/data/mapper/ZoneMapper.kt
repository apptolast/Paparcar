package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.ZoneEntity
import io.apptolast.paparcar.data.datasource.remote.dto.ZoneDto
import io.apptolast.paparcar.domain.model.Zone

fun Zone.toEntity(): ZoneEntity = ZoneEntity(
    id = id,
    userId = userId,
    name = name,
    lat = lat,
    lon = lon,
    iconKey = iconKey,
    createdAt = createdAt,
    radiusMeters = radiusMeters,
    isPrivate = isPrivate,
)

fun ZoneEntity.toDomain(): Zone = Zone(
    id = id,
    userId = userId,
    name = name,
    lat = lat,
    lon = lon,
    iconKey = iconKey,
    createdAt = createdAt,
    radiusMeters = radiusMeters,
    isPrivate = isPrivate,
)

// ── ZoneDto → Entity (sync from Firestore) ──────────────────────────────────

fun ZoneDto.toEntity(): ZoneEntity = ZoneEntity(
    id = id,
    userId = userId,
    name = name,
    lat = lat,
    lon = lon,
    iconKey = iconKey,
    createdAt = createdAt,
    radiusMeters = radiusMeters,
    isPrivate = isPrivate,
)

// ── Domain → ZoneDto (write to Firestore) ────────────────────────────────────

fun Zone.toDto(): ZoneDto = ZoneDto(
    id = id,
    userId = userId,
    name = name,
    lat = lat,
    lon = lon,
    iconKey = iconKey,
    createdAt = createdAt,
    radiusMeters = radiusMeters,
    isPrivate = isPrivate,
)
