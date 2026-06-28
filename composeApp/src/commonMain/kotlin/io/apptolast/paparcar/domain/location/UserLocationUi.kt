package io.apptolast.paparcar.domain.location

/**
 * Live device location **for the UI map puck only** — carries the GPS [bearingDegrees] the
 * persisted [io.apptolast.paparcar.domain.model.GpsPoint] deliberately omits. Kept UI-scoped (never
 * stored) so adding heading doesn't ripple through the Firestore/Room/WorkManager serializers.
 * [MAP-ICONS-V2]
 *
 * @param bearingDegrees course over ground in degrees clockwise from north, or null when the fix
 *   has no reliable bearing (stationary / no movement) — the puck then renders without rotation.
 */
data class UserLocationUi(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearingDegrees: Float?,
)
