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
    /** Body shape of the parked vehicle. Null when the underlying vehicle is non-CAR or unknown. */
    val carbodyType: CarbodyType? = null,
    val stableRank: Int,
    val privateZoneId: String? = null,
    /** On-device license plate — used to label the map marker. Never synced to Firestore. */
    val licensePlate: String? = null,
    /**
     * True when the underlying vehicle has a paired Bluetooth device. Drives the
     * accent colour: BT-paired vehicles always get the blue slot so "blue ring =
     * tracked via BT" reads as a consistent language. Non-BT vehicles cycle through
     * the remaining 7 palette colours by [stableRank].
     */
    val isBluetoothPaired: Boolean = false,
    /** Paint colour of the parked vehicle — recolours the badge marker body. Null = default green. */
    val color: VehicleColor? = null,
    /**
     * Monitoring state of the underlying vehicle ([Vehicle.isActive]). Drives the marker tone for
     * non-BT cars: active → green, inactive → grey. BT-paired cars always read blue regardless.
     */
    val isActive: Boolean = true,
)
