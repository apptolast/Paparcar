package com.rndeveloper.paparcar.data.mapper

import com.rndeveloper.paparcar.data.datasource.local.room.SharedCarEntity
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarInviteDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarMemberDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarSessionDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarStateDto
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarInvite
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.SharedCarRole
import com.rndeveloper.paparcar.domain.model.SharedCarSession
import com.rndeveloper.paparcar.domain.model.SharedCarState
import com.rndeveloper.paparcar.domain.model.VehicleColor
import com.rndeveloper.paparcar.domain.model.VehicleSize

// ── Domain → DTO ──────────────────────────────────────────────────────────────

fun SharedCar.toDto(): SharedCarDto = SharedCarDto(
    carId = carId,
    ownerUid = ownerUid,
    ownerName = ownerName,
    displayName = displayName,
    btMacHash = btMacHash,
    sizeCategory = sizeCategory?.name,
    carbodyType = carbodyType?.name,
    color = color?.name,
    sharingEnabled = sharingEnabled,
    createdAtMs = createdAtMs,
    fuelType = fuelType?.name,
    consumptionPer100 = consumptionPer100,
    consumptionElectricPer100 = consumptionElectricPer100,
    inviteCode = activeInviteCode,
    inviteExpiresAtMs = activeInviteExpiresAtMs,
)

fun SharedCarState.toDto(): SharedCarStateDto = SharedCarStateDto(
    sessionId = sessionId,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracy = location.accuracy,
    parkedAtMs = parkedAtMs,
    authorUid = authorUid,
    authorName = authorName,
    address = address?.toAddressDto(),
    placeInfo = placeInfo?.toPlaceInfoDto(),
    isParked = isParked,
    updatedAtMs = updatedAtMs,
)

fun SharedCarSession.toDto(): SharedCarSessionDto = SharedCarSessionDto(
    sessionId = sessionId,
    authorUid = authorUid,
    authorName = authorName,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracy = location.accuracy,
    parkedAtMs = parkedAtMs,
    endedAtMs = endedAtMs,
    isActive = isActive,
    address = address?.toAddressDto(),
    placeInfo = placeInfo?.toPlaceInfoDto(),
    routeDistanceMeters = routeDistanceMeters,
    searchTimeMs = searchTimeMs,
    publishedSpot = publishedSpot,
    routePolyline = routePolyline,
    routeSnapped = routeSnapped,
    routeInferredSpans = routeInferredSpans,
    updatedAtMs = updatedAtMs,
)

fun SharedCarMember.toDto(inviteCode: String? = null): SharedCarMemberDto = SharedCarMemberDto(
    uid = uid,
    displayName = displayName,
    photoUrl = photoUrl,
    joinedAtMs = joinedAtMs,
    role = role.name,
    sharesRoutes = sharesRoutes,
    inviteCode = inviteCode,
)

fun SharedCarInvite.toDto(carName: String): SharedCarInviteDto = SharedCarInviteDto(
    code = code,
    carId = carId,
    ownerUid = ownerUid,
    carName = carName,
    createdAtMs = createdAtMs,
    expiresAtMs = expiresAtMs,
    claimedByUid = claimedByUid,
)

// ── DTO → Domain ──────────────────────────────────────────────────────────────

fun SharedCarDto.toDomain(state: SharedCarStateDto? = null): SharedCar = SharedCar(
    carId = carId,
    ownerUid = ownerUid,
    ownerName = ownerName,
    displayName = displayName,
    btMacHash = btMacHash,
    sizeCategory = sizeCategory.toEnumOrNull<VehicleSize>(),
    carbodyType = carbodyType.toEnumOrNull<CarbodyType>(),
    color = color.toEnumOrNull<VehicleColor>(),
    sharingEnabled = sharingEnabled,
    createdAtMs = createdAtMs,
    fuelType = fuelType.toEnumOrNull<FuelType>(),
    consumptionPer100 = consumptionPer100,
    consumptionElectricPer100 = consumptionElectricPer100,
    activeInviteCode = inviteCode,
    activeInviteExpiresAtMs = inviteExpiresAtMs,
    state = state?.toDomain(),
)

fun SharedCarStateDto.toDomain(): SharedCarState = SharedCarState(
    sessionId = sessionId,
    location = GpsPoint(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = parkedAtMs,
        speed = 0f,
    ),
    parkedAtMs = parkedAtMs,
    authorUid = authorUid,
    authorName = authorName,
    address = address?.toDomain(),
    placeInfo = placeInfo?.toDomain(),
    isParked = isParked,
    updatedAtMs = updatedAtMs,
)

fun SharedCarSessionDto.toDomain(): SharedCarSession = SharedCarSession(
    sessionId = sessionId,
    authorUid = authorUid,
    authorName = authorName,
    location = GpsPoint(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = parkedAtMs,
        speed = 0f,
    ),
    parkedAtMs = parkedAtMs,
    endedAtMs = endedAtMs,
    isActive = isActive,
    address = address?.toDomain(),
    placeInfo = placeInfo?.toDomain(),
    routeDistanceMeters = routeDistanceMeters,
    searchTimeMs = searchTimeMs,
    publishedSpot = publishedSpot,
    routePolyline = routePolyline,
    routeSnapped = routeSnapped,
    routeInferredSpans = routeInferredSpans,
    updatedAtMs = updatedAtMs,
)

fun SharedCarMemberDto.toDomain(): SharedCarMember = SharedCarMember(
    uid = uid,
    displayName = displayName,
    photoUrl = photoUrl,
    joinedAtMs = joinedAtMs,
    role = role.toEnumOrDefault(SharedCarRole.MEMBER),
    sharesRoutes = sharesRoutes,
)

fun SharedCarInviteDto.toDomain(): SharedCarInvite = SharedCarInvite(
    code = code,
    carId = carId,
    ownerUid = ownerUid,
    createdAtMs = createdAtMs,
    expiresAtMs = expiresAtMs,
    claimedByUid = claimedByUid,
)

/** An address travels whole [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001] — including back. */
fun AddressDto.toDomain(): AddressInfo = AddressInfo(
    street = street,
    city = city,
    region = region,
    country = country,
    countryCode = countryCode,
    province = province,
    postalCode = postalCode,
)

/** A POI without a category we understand is not a POI we can draw — null, not a guess. */
fun PlaceInfoDto.toDomain(): PlaceInfo? {
    val parsed = category.toEnumOrNull<PlaceCategory>() ?: return null
    return PlaceInfo(name = name, category = parsed)
}

// ── Room cache ────────────────────────────────────────────────────────────────

fun SharedCar.toEntity(): SharedCarEntity = SharedCarEntity(
    carId = carId,
    ownerUid = ownerUid,
    ownerName = ownerName,
    displayName = displayName,
    btMacHash = btMacHash,
    sizeCategory = sizeCategory?.name,
    carbodyType = carbodyType?.name,
    color = color?.name,
    sharingEnabled = sharingEnabled,
    createdAtMs = createdAtMs,
    fuelType = fuelType?.name,
    consumptionPer100 = consumptionPer100,
    consumptionElectricPer100 = consumptionElectricPer100,
    activeInviteCode = activeInviteCode,
    activeInviteExpiresAtMs = activeInviteExpiresAtMs,
    stateSessionId = state?.sessionId,
    stateLatitude = state?.location?.latitude,
    stateLongitude = state?.location?.longitude,
    stateAccuracy = state?.location?.accuracy,
    stateParkedAtMs = state?.parkedAtMs,
    stateAuthorUid = state?.authorUid,
    stateAuthorName = state?.authorName,
    stateStreet = state?.address?.street,
    stateCity = state?.address?.city,
    stateRegion = state?.address?.region,
    stateCountry = state?.address?.country,
    stateCountryCode = state?.address?.countryCode,
    stateProvince = state?.address?.province,
    statePostalCode = state?.address?.postalCode,
    statePlaceName = state?.placeInfo?.name,
    statePlaceCategory = state?.placeInfo?.category?.name,
    stateIsParked = state?.isParked ?: false,
    stateUpdatedAtMs = state?.updatedAtMs ?: 0L,
)

fun SharedCarEntity.toDomain(): SharedCar = SharedCar(
    carId = carId,
    ownerUid = ownerUid,
    ownerName = ownerName,
    displayName = displayName,
    btMacHash = btMacHash,
    sizeCategory = sizeCategory.toEnumOrNull<VehicleSize>(),
    carbodyType = carbodyType.toEnumOrNull<CarbodyType>(),
    color = color.toEnumOrNull<VehicleColor>(),
    sharingEnabled = sharingEnabled,
    createdAtMs = createdAtMs,
    fuelType = fuelType.toEnumOrNull<FuelType>(),
    consumptionPer100 = consumptionPer100,
    consumptionElectricPer100 = consumptionElectricPer100,
    activeInviteCode = activeInviteCode,
    activeInviteExpiresAtMs = activeInviteExpiresAtMs,
    state = cachedState(),
)

/** The cached state, or null when this car has never been parked with the group watching.
 *  A position is the ONE thing a state cannot do without: no coordinates ⇒ no state. */
private fun SharedCarEntity.cachedState(): SharedCarState? {
    val sessionId = stateSessionId ?: return null
    val latitude = stateLatitude ?: return null
    val longitude = stateLongitude ?: return null
    val address = AddressInfo(
        street = stateStreet,
        city = stateCity,
        region = stateRegion,
        country = stateCountry,
        countryCode = stateCountryCode,
        province = stateProvince,
        postalCode = statePostalCode,
    ).takeIf { !it.isBlank }
    val place = statePlaceName?.let { name ->
        statePlaceCategory.toEnumOrNull<PlaceCategory>()?.let { PlaceInfo(name, it) }
    }
    return SharedCarState(
        sessionId = sessionId,
        location = GpsPoint(
            latitude = latitude,
            longitude = longitude,
            accuracy = stateAccuracy ?: 0f,
            timestamp = stateParkedAtMs ?: 0L,
            speed = 0f,
        ),
        parkedAtMs = stateParkedAtMs ?: 0L,
        authorUid = stateAuthorUid.orEmpty(),
        authorName = stateAuthorName,
        address = address,
        placeInfo = place,
        isParked = stateIsParked,
        updatedAtMs = stateUpdatedAtMs,
    )
}
