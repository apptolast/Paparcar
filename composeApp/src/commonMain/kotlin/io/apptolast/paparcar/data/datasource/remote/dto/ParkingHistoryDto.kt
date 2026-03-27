package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ParkingHistoryDto(
    val id: String = "",
    val userId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = 0L,
    val isActive: Boolean = false,
    val spotId: String? = null,
    val geofenceId: String? = null,
    val address: AddressDto? = null,
    val placeInfo: PlaceInfoDto? = null,
)
