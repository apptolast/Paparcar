package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.UserParkingSessionEntity
import io.apptolast.paparcar.domain.model.UserParkingSession

fun UserParkingSessionEntity.toDomain(): UserParkingSession = UserParkingSession(
    id = id,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    timestamp = timestamp,
    spotId = spotId,
    geofenceId = geofenceId,
    isActive = isActive,
)

fun UserParkingSession.toEntity(): UserParkingSessionEntity = UserParkingSessionEntity(
    id = id,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    timestamp = timestamp,
    spotId = spotId,
    geofenceId = geofenceId,
    isActive = isActive,
)
