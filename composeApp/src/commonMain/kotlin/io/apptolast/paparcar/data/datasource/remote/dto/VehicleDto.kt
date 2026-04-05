package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

/**
 * Firestore DTO for Vehicle.
 * bluetoothDeviceId is intentionally absent — it stays on-device only.
 */
@Serializable
data class VehicleDto(
    val id: String = "",
    val userId: String = "",
    val brand: String? = null,
    val model: String? = null,
    val sizeCategory: String = "MEDIUM",
    val showBrandModelOnSpot: Boolean = false,
    val isDefault: Boolean = false,
)
