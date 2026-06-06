package io.apptolast.paparcar.domain.model

/**
 * Read model (CQRS projection) combining an active parking session with the
 * minimum vehicle metadata needed by the map layer.
 *
 * [stableRank] is a deterministic ordinal derived from the lexicographic sort
 * of all vehicleIds belonging to the user. Stable across restarts regardless
 * of insertion order — the UI maps it to an accent colour via VehicleAccentPalette.
 */
data class ParkedVehicleSummary(
    val sessionId: String,
    val vehicleId: String,
    val displayName: String,
    val location: GpsPoint,
    val sizeCategory: VehicleSize?,
    val stableRank: Int,
    val privateZoneId: String? = null,
    /** On-device license plate — used to label the map marker. Never synced to Firestore. */
    val licensePlate: String? = null,
)
