package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.LocationEntity
import io.apptolast.paparcar.domain.model.SpotLocation

fun LocationEntity.toDomain(): SpotLocation = SpotLocation(
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    timestamp = timestamp
)

fun SpotLocation.toEntity(): LocationEntity = LocationEntity(
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    timestamp = timestamp
)
