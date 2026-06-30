package io.apptolast.paparcar.domain.model

import io.apptolast.paparcar.domain.detection.DetectionPhase

/**
 * Live "driving" map puck — the user's own car shown in top-down view, rotated to the GPS heading,
 * while detection is actively monitoring a trip. Non-null only in that window; when null the map
 * falls back to the native location dot. [MAP-ICONS-V2]
 *
 * @param vehicleId id of the vehicle whose trip is being monitored — lets the UI flag that exact
 *   vehicle as "driving" elsewhere (e.g. its Home chip). Null when the monitored vehicle is unknown.
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
    /** Paint colour of the active vehicle — recolours the top-down puck body. Null = default green. */
    val color: VehicleColor? = null,
    val vehicleId: String? = null,
    /** Coarse trip phase — drives the distinct "candidate" treatment of the chip/puck when the user
     *  stops and walks away. [DET-PHASE-001] */
    val phase: DetectionPhase = DetectionPhase.Driving,
)
