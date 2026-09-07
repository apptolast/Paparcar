package com.rndeveloper.paparcar.data.mapper

import com.rndeveloper.paparcar.data.datasource.local.room.UserProfileEntity
import com.rndeveloper.paparcar.data.datasource.remote.dto.UserProfileDto
import com.rndeveloper.paparcar.domain.model.UserProfile

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
    firstStepsDismissedAt = firstStepsDismissedAt,
)

// ── UserProfileEntity → Domain ─────────────────────────────────────────────────
//
// [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] `firstStepsDismissedAt`
// is NOT carried here, and that is the design: the Room row does not store it (see the field's
// KDoc). It reads as null from the cached path, which can only fail to restore a dismissal —
// never clear one.

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
    firstStepsDismissedAt = firstStepsDismissedAt,
)
