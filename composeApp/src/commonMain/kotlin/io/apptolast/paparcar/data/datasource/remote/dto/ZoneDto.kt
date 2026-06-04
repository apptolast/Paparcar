package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ZoneDto(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val iconKey: String = "",
    val createdAt: Long = 0L,
    val radiusMeters: Float = 250f,
    val isPrivate: Boolean = false,
)
