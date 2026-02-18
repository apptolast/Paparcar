package io.apptolast.paparcar.domain.model

data class Spot(
    val id: String,
    val location: SpotLocation,
    val reportedBy: String,
    val isActive: Boolean
)
