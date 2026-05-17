package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class VehicleDto(
    val id: String = "",
    val userId: String = "",
    val brand: String? = null,
    val model: String? = null,
    val sizeCategory: String = "",
    val bluetoothDeviceId: String? = null,
    val showBrandModelOnSpot: Boolean = false,
    val isDefault: Boolean = false,
)
