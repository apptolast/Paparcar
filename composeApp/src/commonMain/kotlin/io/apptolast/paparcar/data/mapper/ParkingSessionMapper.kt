package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.UserParkingEntity
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking

fun UserParkingEntity.toDomain(): UserParking = UserParking(
    id = id,
    location = GpsPoint(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = timestamp,
        speed = 0f, // Entity has no speed field; default to 0
    ),
    spotId = spotId,
    geofenceId = geofenceId,
    isActive = isActive,
)

fun UserParking.toEntity(): UserParkingEntity = UserParkingEntity(
    id = id,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracy = location.accuracy,
    timestamp = location.timestamp,
    spotId = spotId,
    geofenceId = geofenceId,
    isActive = isActive,
)
