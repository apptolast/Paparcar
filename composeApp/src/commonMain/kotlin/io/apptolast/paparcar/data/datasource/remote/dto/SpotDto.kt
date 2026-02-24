package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SpotDto(
    // El ID se genera en el cliente y coincide con el ID del documento
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val reportedAt: Long = 0L,
    val reportedBy: String = "",
    val isActive: Boolean = true,
    val speed: Float = 0f // Añadido
)
