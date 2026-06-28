package io.apptolast.paparcar.domain.model

/**
 * Live "driving" map puck — the user's own car shown in top-down view, rotated to the GPS heading,
 * while detection is actively monitoring a trip. Non-null only in that window; when null the map
 * falls back to the native location dot. [MAP-ICONS-V2]
 *
 * @param bearingDegrees course over ground (clockwise from north), or null when not moving.
 * @param carbodyType / [sizeCategory] the active vehicle, picking which top-down silhouette to draw.
 */
data class DrivingPuck(
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Float?,
    val accuracy: Float,
    val carbodyType: CarbodyType?,
    val sizeCategory: VehicleSize?,
)
