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
)

fun UserProfileDto.toDomain() = UserProfile(
    userId = userId,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ── UserProfileEntity → Domain ─────────────────────────────────────────────────

fun UserProfileEntity.toDomain() = UserProfile(
    userId = userId,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ── UserProfile → Dto ──────────────────────────────────────────────────────────

fun UserProfile.toDto() = UserProfileDto(
    userId = userId,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
