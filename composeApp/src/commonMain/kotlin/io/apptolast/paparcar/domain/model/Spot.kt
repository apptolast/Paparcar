package io.apptolast.paparcar.domain.model

/**
 * A community parking spot reported by a user who just left with their car.
 *
 * Compatibility metadata snapshot from the leaving vehicle:
 * - [type]         – detection method (auto BT-confirmed vs manual report)
 * - [confidence]   – 0..1; 1.0 = BT-confirmed, lower = probabilistic AR detection
 * - [sizeCategory] – length-based category that freed the spot; powers the "fits" check
 * - [carbodyType]  – body-shape of the vehicle that freed the spot; powers the "optimal" badge
 * - [enRouteCount] – users currently navigating here (community pressure signal)
 * - [expiresAt]    – epoch millis; 0 = no TTL set yet
 */
data class Spot(
    val id: String,
    val location: GpsPoint,
    val reportedBy: String,
    val address: AddressInfo? = null,
    val placeInfo: PlaceInfo? = null,
    val type: SpotType = SpotType.AUTO_DETECTED,
    /** Confidence score 0..1. Used to tint the marker ring (HIGH≥0.75 green, MEDIUM≥0.55 amber, LOW red). */
    val confidence: Float = 1f,
    /** Length-based category of the vehicle that freed this spot. Null when unknown. */
    val sizeCategory: VehicleSize? = null,
    /** Body shape of the vehicle that freed this spot. Drives the "Optimal for your X" peek badge. */
    val carbodyType: CarbodyType? = null,
    /** How many users are currently navigating to this spot. */
    val enRouteCount: Int = 0,
    /** Epoch millis when this spot expires. 0 = no TTL set. */
    val expiresAt: Long = 0L,
)
