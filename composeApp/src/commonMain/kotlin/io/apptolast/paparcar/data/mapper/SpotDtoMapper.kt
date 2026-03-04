package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint

fun SpotDto.toDomain(id: String): Spot = Spot(
    id = id,
    location = GpsPoint(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = reportedAt,
        speed = speed,
    ),
    reportedBy = reportedBy,
    isActive = isActive
)

fun Spot.toDto(): SpotDto = SpotDto(
    id = id,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracy = location.accuracy,
    reportedAt = location.timestamp,
    reportedBy = reportedBy,
    isActive = isActive,
    speed = location.speed,
)
