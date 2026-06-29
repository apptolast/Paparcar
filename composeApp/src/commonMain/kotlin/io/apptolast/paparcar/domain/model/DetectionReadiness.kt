package io.apptolast.paparcar.domain.model

import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.permissions.RequiredPermission

/**
 * Global readiness of the automatic parking-detection system for the active vehicle.
 *
 * Reflected in the Home detection banner. **Orthogonal** to `HomeMode` (which is *what the user
 * is doing* — Browse/Reporting/…): this is *what the detection background system is doing*, so the
 * two coexist. Replaces the thin `HomeState.allPermissionsGranted` boolean. [DET-READY-001b]
 *
 * Precedence when resolving (first match wins):
 * Disabled → Blocked → Parked → Monitoring → Ready.
 *
 *  - [Disabled]: detection does not apply (no vehicle, or active vehicle is a non-parking type).
 *  - [Blocked]: a required permission is missing — detection can't run until granted.
 *  - [Ready]: armed and idle, waiting to detect the next parking. The "no geofence yet" state.
 *  - [Monitoring]: a tracking job is actively following the current trip.
 *  - [Parked]: the vehicle is parked with a geofence watching for departure.
 */
sealed class DetectionReadiness {
    data class Disabled(val reason: DisabledReason) : DetectionReadiness()
    data class Blocked(val missing: Set<RequiredPermission>) : DetectionReadiness()
    data class Ready(val strategy: ParkingStrategy) : DetectionReadiness()
    data class Monitoring(val strategy: ParkingStrategy) : DetectionReadiness()
    data class Parked(val session: UserParking) : DetectionReadiness()
}

/** Why automatic detection does not apply for the current active vehicle. */
enum class DisabledReason {
    /** No vehicle registered yet — nothing to detect. */
    NO_VEHICLE,

    /** Active vehicle is a SCOOTER / BIKE that never occupies a parking spot. [BUG-SCOOTER-001] */
    NON_PARKING_VEHICLE,

    /** The user switched auto-detection OFF from Settings — an intent flag, independent of
     *  permissions. Home offers a one-tap "activate detection" to flip it back on. [DET-TOGGLE-001] */
    TURNED_OFF,
}
