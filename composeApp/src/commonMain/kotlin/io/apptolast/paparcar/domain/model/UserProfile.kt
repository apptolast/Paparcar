package io.apptolast.paparcar.domain.model

data class UserProfile(
    val userId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * Cached pointer to the user's currently active vehicle. Mirrors the
     * `isDefault = true` row in the vehicles subcollection.
     *
     * Used by the splash bootstrap as a fast "does this account have a vehicle"
     * check — avoids paying for a list query just to decide the start route.
     * Stays null until the user registers their first vehicle.
     */
    val defaultVehicleId: String? = null,
)
