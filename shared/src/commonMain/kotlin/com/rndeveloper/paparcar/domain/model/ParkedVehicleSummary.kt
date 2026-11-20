package com.rndeveloper.paparcar.domain.model

/**
 * Read model (CQRS projection) combining an active parking session with the
 * minimum vehicle metadata needed by the map layer.
 *
 * [stableRank] is a deterministic ordinal derived from the lexicographic sort
 * of all vehicleIds belonging to the user. Stable across restarts regardless
 * of insertion order — the UI maps it to an accent colour.
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
    // ⛔ No `privateZoneId` here. [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001]
    // It rode along for the map layer and NOBODY ever read it — a stamped verdict offered to the
    // UI is an invitation to gate on the answer the car arrived with instead of the one that
    // holds now. Whoever needs to know asks `SpotReleasePolicy.privateZoneAt(location, zones)`.
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
    /**
     * Radius of the approximate zone this session was closed with, or null when the pin is exact.
     * Mirrors [UserParking.zoneRadiusMeters] so the map can DRAW the doubt the domain already
     * records — a 154 m area and a 3 m pin must not paint identically.
     * [UI-APPROXIMATE-PARKING-DRAWS-ITS-DOUBT-001]
     */
    val zoneRadiusMeters: Float? = null,
    /**
     * Carried, not derived: the projection has no `detectionPath`, and giving it one so the map
     * could re-run the type lookup would put the same decision in two places.
     * [UserParking.isApproximate] decides; this mirrors its answer for the path half.
     * [DET-AN-HONEST-CLOSE-THAT-GOT-IT-RIGHT-MUST-NOT-APOLOGISE-001]
     */
    val placementIsApproximate: Boolean = false,
) {
    /** True when the app does not claim to know exactly where this car stopped — an AREA, or a
     *  point it inferred rather than measured. Same union, and the same meaning, as
     *  [UserParking.isApproximate]. */
    val isApproximate: Boolean get() = zoneRadiusMeters != null || placementIsApproximate
}
