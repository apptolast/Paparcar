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
    val bluetoothDeviceId: String? = null,
    val showBrandModelOnSpot: Boolean = false,
    val isDefault: Boolean = false,
)
