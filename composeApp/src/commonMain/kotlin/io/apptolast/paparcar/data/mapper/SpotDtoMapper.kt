package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint

fun SpotDto.toDomain(): Spot = Spot(
    id = id,
    location = GpsPoint(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = reportedAt,
        speed = speed,
    ),
    reportedBy = reportedBy,
    address = address?.let {
        AddressInfo(street = it.street, city = it.city, region = it.region, country = it.country)
    },
    placeInfo = placeInfo?.let { dto ->
        runCatching { PlaceInfo(dto.name, PlaceCategory.valueOf(dto.category)) }.getOrNull()
    },
)

fun Spot.toDto(): SpotDto = SpotDto(
    id = id,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracy = location.accuracy,
    reportedAt = location.timestamp,
    reportedBy = reportedBy,
    speed = location.speed,
    address = address?.let {
        AddressDto(street = it.street, city = it.city, region = it.region, country = it.country)
    },
    placeInfo = placeInfo?.let { PlaceInfoDto(name = it.name, category = it.category.name) },
)
