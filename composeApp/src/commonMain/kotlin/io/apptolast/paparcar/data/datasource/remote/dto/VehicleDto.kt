package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class VehicleDto(
    val id: String = "",
    val userId: String = "",
    /** Private — never read from Firestore on-device-only, but deserialized gracefully if present. */
    val name: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val sizeCategory: String = "",
    /** [CarbodyType] enum name. Empty string when the vehicle is non-CAR or pre-feature. */
    val carbodyType: String = "",
    /** [VehicleType] enum name. Empty string for pre-feature rows; mappers default to "CAR". */
    val vehicleType: String = "",
    val bluetoothDeviceId: String? = null,
    val showBrandModelOnSpot: Boolean = false,
    val isActive: Boolean = false,
)
