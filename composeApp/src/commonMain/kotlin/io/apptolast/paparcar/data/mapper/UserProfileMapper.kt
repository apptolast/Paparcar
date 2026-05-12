package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.UserProfileEntity
import io.apptolast.paparcar.data.datasource.remote.dto.UserProfileDto
import io.apptolast.paparcar.domain.model.UserProfile

// ── UserProfileDto ↔ UserProfileEntity ────────────────────────────────────────

fun UserProfileDto.toEntity() = UserProfileEntity(
    userId = userId,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    defaultVehicleId = defaultVehicleId,
)

fun UserProfileDto.toDomain() = UserProfile(
    userId = userId,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    defaultVehicleId = defaultVehicleId,
)

// ── UserProfileEntity → Domain ─────────────────────────────────────────────────

fun UserProfileEntity.toDomain() = UserProfile(
    userId = userId,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    defaultVehicleId = defaultVehicleId,
)

// ── UserProfile → Dto ──────────────────────────────────────────────────────────

fun UserProfile.toDto() = UserProfileDto(
    userId = userId,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    defaultVehicleId = defaultVehicleId,
)
