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
 */
data class UserParking(
    val id: String,
    val userId: String = "",
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
)
