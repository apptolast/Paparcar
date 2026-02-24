package io.apptolast.paparcar.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SpotLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val speed: Float,
)

