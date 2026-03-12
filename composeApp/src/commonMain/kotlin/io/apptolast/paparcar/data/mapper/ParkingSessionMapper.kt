package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.UserParkingEntity
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking

fun UserParkingEntity.toDomain(): UserParking = UserParking(
    id = id,
    location = GpsPoint(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = timestamp,
        speed = 0f,
    ),
    spotId = spotId,
    geofenceId = geofenceId,
    isActive = isActive,
    address = if (addressStreet != null || addressCity != null ||
        addressRegion != null || addressCountry != null
    ) {
        AddressInfo(
            street = addressStreet,
            city = addressCity,
            region = addressRegion,
            country = addressCountry,
        )
    } else null,
    placeInfo = run {
        val name = placeInfoName
        val cat  = placeInfoCategory
        if (name != null && cat != null)
            runCatching { PlaceInfo(name, PlaceCategory.valueOf(cat)) }.getOrNull()
        else null
    },
)

/** Converts an active parking session into a released [Spot] for reporting to Firebase. */
fun UserParking.toSpot(): Spot = Spot(
    id = id,
    location = GpsPoint(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracy = location.accuracy,
        timestamp = location.timestamp,
        speed = 0f,
    ),
    reportedBy = "anonymous",
    address = address,
    placeInfo = placeInfo,
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
    addressStreet = address?.street,
    addressCity = address?.city,
    addressRegion = address?.region,
    addressCountry = address?.country,
    placeInfoName = placeInfo?.name,
    placeInfoCategory = placeInfo?.category?.name,
)
