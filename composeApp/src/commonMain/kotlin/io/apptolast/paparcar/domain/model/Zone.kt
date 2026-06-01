package io.apptolast.paparcar.domain.model

/**
 * Habitual place saved by the user for one-tap navigation from Home
 * (Casa, Trabajo, Padres…).
 *
 * Zones live in the user's private subcollection — they are NEVER shared
 * with the community. Persistence is dual: Room locally for instant
 * access offline, Firestore (`users/{uid}/zones`) for cross-device sync.
 *
 * @property iconKey opaque preset identifier — UI maps it to an
 *   ImageVector (HOME → home icon, WORK → briefcase, etc.). Stored as a
 *   string so adding new presets does not require a schema migration.
 */
data class Zone(
    val id: String,
    val userId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val iconKey: String,
    val createdAt: Long,
    val radiusMeters: Float = DEFAULT_RADIUS_METERS,
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 150f
        const val MIN_RADIUS_METERS = 50f
        const val MAX_RADIUS_METERS = 300f
    }
}
