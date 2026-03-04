package io.apptolast.paparcar.domain.model

/**
 * Represents the user's own active or past parking session.
 *
 * Distinct from [Spot], which represents spots shared with other users.
 * Only one session should be active ([isActive] = true) at a time.
 */
data class UserParking(
    val id: String,
    val location: GpsPoint,
    val spotId: String? = null,
    val geofenceId: String? = null,
    val isActive: Boolean = true,
)
