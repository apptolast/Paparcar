package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.ParkingSessionEntity
import io.apptolast.paparcar.domain.model.ParkingSession

fun ParkingSessionEntity.toDomain(): ParkingSession = ParkingSession(
    id = id,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    timestamp = timestamp,
    spotId = spotId,
    geofenceId = geofenceId,
    isActive = isActive,
)

fun ParkingSession.toEntity(): ParkingSessionEntity = ParkingSessionEntity(
    id = id,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    timestamp = timestamp,
    spotId = spotId,
    geofenceId = geofenceId,
    isActive = isActive,
)
