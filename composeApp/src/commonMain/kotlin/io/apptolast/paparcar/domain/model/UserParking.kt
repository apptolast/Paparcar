package io.apptolast.paparcar.domain.model

/**
 * Represents the user's own active or past parking session.
 *
 * Distinct from [Spot], which represents spots shared with other users.
 * Only one session should be active ([isActive] = true) at a time.
 *
 * [address] and [placeInfo] are enriched asynchronously after the session is saved
 * (via Overpass + geocoder). Both may be null for legacy records or if the network
 * was unavailable at parking time.
 *
 * Phase 4 additions:
 * - [spotType]      – how the parking was detected / confirmed.
 * - [sizeCategory]  – size of the user's vehicle at confirmation time (null = unknown).
 */
data class UserParking(
    val id: String,
    val userId: String = "",
    val vehicleId: String? = null,
    val location: GpsPoint,
    val spotId: String? = null,
    val geofenceId: String? = null,
    val isActive: Boolean = true,
    val address: AddressInfo? = null,
    val placeInfo: PlaceInfo? = null,
    /** Probability [0.0, 1.0] that this is a genuine parking event.
     *  1.0 = user manually confirmed; ~0.90 = vehicle-exit signal observed;
     *  ~0.75 = slow-path auto-detection only. */
    val detectionReliability: Float? = null,
    /** How the parking was detected / confirmed. Propagated to the released [Spot]. */
    val spotType: SpotType = SpotType.AUTO_DETECTED,
    /** Size of the user's vehicle — null until vehicle integration wires it in. */
    val sizeCategory: VehicleSize? = null,
)
