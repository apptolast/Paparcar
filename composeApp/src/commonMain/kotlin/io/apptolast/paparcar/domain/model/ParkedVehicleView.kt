package io.apptolast.paparcar.domain.model

/**
 * Read-only projection used by the map layer.
 * Combines an active parking session with just enough vehicle data to render a marker.
 */
data class ParkedVehicleView(
    val sessionId: String,
    val vehicleId: String,
    val displayName: String,
    val location: GpsPoint,
    val sizeCategory: VehicleSize?,
    /** Stable index into the vehicle accent palette (0-based, sorted by vehicleId). */
    val paletteIndex: Int,
    /** Non-null when the session is inside a private zone — map suppresses the vehicle badge marker. */
    val privateZoneId: String? = null,
)
