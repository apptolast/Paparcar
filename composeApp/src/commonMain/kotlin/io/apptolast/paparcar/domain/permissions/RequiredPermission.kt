package io.apptolast.paparcar.domain.permissions

/**
 * Tier that decides how much of the app a missing permission blocks. [DET-READY-001a]
 *
 *  - [CORE]: hard requirement to use the app at all. Without it the spot map (consumer side)
 *    cannot function, so it gates entry to Home.
 *  - [PRODUCER]: required only for automatic detection (producer side — publishing your spot,
 *    remembering where you parked). Requested contextually at the moment of value; its absence
 *    only degrades detection and is surfaced in the Home readiness banner, never blocks Home.
 */
enum class PermissionTier {
    CORE,
    PRODUCER,
}

/**
 * A runtime permission the app depends on, tagged with the [tier] that decides whether its
 * absence blocks the whole app (CORE) or only disables automatic detection (PRODUCER).
 */
enum class RequiredPermission(val tier: PermissionTier) {
    FOREGROUND_LOCATION(PermissionTier.CORE),
    // Notifications belong to the PRODUCER (detection) experience — without them auto-detection
    // can't tell you anything (parked saved, "did you park?", spot published). They do NOT gate the
    // consumer side (browsing spots works fine without them), so they're no longer CORE. [DET-READY-001i]
    NOTIFICATIONS(PermissionTier.PRODUCER),
    BACKGROUND_LOCATION(PermissionTier.PRODUCER),
    ACTIVITY_RECOGNITION(PermissionTier.PRODUCER),
}

/** All required permissions (CORE + PRODUCER) not yet granted in this state. */
fun AppPermissionState.missingPermissions(): Set<RequiredPermission> = buildSet {
    if (!hasLocationPermission) add(RequiredPermission.FOREGROUND_LOCATION)
    if (!hasNotificationPermission) add(RequiredPermission.NOTIFICATIONS)
    if (!hasBackgroundLocationPermission) add(RequiredPermission.BACKGROUND_LOCATION)
    if (!hasActivityRecognitionPermission) add(RequiredPermission.ACTIVITY_RECOGNITION)
}

/** Missing CORE-tier permissions — their absence blocks app entry. */
fun AppPermissionState.missingCorePermissions(): Set<RequiredPermission> =
    missingPermissions().filterTo(mutableSetOf()) { it.tier == PermissionTier.CORE }

/** Missing PRODUCER-tier permissions — their absence only disables automatic detection. */
fun AppPermissionState.missingProducerPermissions(): Set<RequiredPermission> =
    missingPermissions().filterTo(mutableSetOf()) { it.tier == PermissionTier.PRODUCER }
