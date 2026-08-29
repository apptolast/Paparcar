package com.rndeveloper.paparcar.data.mapper

import com.rndeveloper.paparcar.data.datasource.local.room.ZoneEntity
import com.rndeveloper.paparcar.data.datasource.remote.dto.ZoneDto
import com.rndeveloper.paparcar.domain.model.Zone

// [updatedAt]/[pendingSync] are persistence-only reconcile metadata (not on the domain model): a
// local mutation stamps updatedAt=now + pendingSync=true so the inbound sync won't clobber it.
// [SYNC-RECONCILE-001]
fun Zone.toEntity(updatedAt: Long = 0, pendingSync: Boolean = false): ZoneEntity = ZoneEntity(
    id = id,
    userId = userId,
    name = name,
    lat = lat,
    lon = lon,
    iconKey = iconKey,
    createdAt = createdAt,
    radiusMeters = radiusMeters,
    isPrivate = isPrivate,
    updatedAt = updatedAt,
    pendingSync = pendingSync,
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
    // Remote is authoritative & confirmed → clean row carrying the server stamp. [SYNC-RECONCILE-001]
    updatedAt = updatedAt,
    pendingSync = false,
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
