package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ParkingHistoryDto(
    val id: String = "",
    val userId: String = "",
    val vehicleId: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = 0L,
    val isActive: Boolean = false,
    val spotId: String? = null,
    val geofenceId: String? = null,
    val address: AddressDto? = null,
    val placeInfo: PlaceInfoDto? = null,
    val detectionReliability: Float? = null,
    /** [VehicleSize] enum name captured at park time. Null when unknown. */
    val sizeCategory: String? = null,
    /** [CarbodyType] enum name captured at park time. Null for non-CAR or unknown. */
    val carbodyType: String? = null,
)
