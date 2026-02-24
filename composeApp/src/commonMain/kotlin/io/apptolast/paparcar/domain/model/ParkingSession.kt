package io.apptolast.paparcar.domain.model

data class ParkingSession(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val spotId: String? = null,
    val geofenceId: String? = null,
    val isActive: Boolean = true,
)
