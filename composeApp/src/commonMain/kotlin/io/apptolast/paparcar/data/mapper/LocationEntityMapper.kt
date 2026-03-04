package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.LocationEntity
import io.apptolast.paparcar.domain.model.GpsPoint

fun LocationEntity.toDomain(): GpsPoint = GpsPoint(
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    timestamp = timestamp,
    speed = speed
)

fun GpsPoint.toEntity(): LocationEntity = LocationEntity(
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    timestamp = timestamp,
    speed = speed
)
