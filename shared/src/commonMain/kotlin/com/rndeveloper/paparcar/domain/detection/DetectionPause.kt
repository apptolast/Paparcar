package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.permissions.RequiredPermission

/**
 * Why automatic detection will NOT measure the next trip — or null when it will.
 * [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
 *
 * ⛔ **Two entries and not one flag**, for the reason [DetectionStateSnapshot] already gives about
 * its own fields: "the user turned it off" and "the user wants it and a permission is missing" are
 * two populations with **opposite remedies**, and one boolean collapses them into the same silence
 * — the same silence the figures themselves were keeping.
 *
 * ⛔ A degraded environment is NOT a pause [DOZE-001]: the battery whitelist and the OEM cards make
 * detection less dependable, they never stop it, and calling that "we are not measuring" would be
 * an app crying wolf on a screen where it has nothing to offer but a number.
 */
enum class DetectionPause {
    /** The Settings master switch is off. The user's own decision — the remedy is turning it on. */
    TURNED_OFF,

    /** Wanted, but a PRODUCER permission is missing: the population that is broken without knowing.
     *  The remedy is the permissions flow, and flipping a switch would achieve nothing. */
    MISSING_PERMISSIONS,
}

/**
 * The verdict, from the two facts that decide it.
 *
 * Precedence — a deliberate OFF wins over a missing permission: somebody who does not want
 * detection is not broken, and asking them for background location would be answering a question
 * they did not ask. It is the SAME order [DetectionStateSnapshot.label] reports to analytics,
 * because it is now the same function: the ladder was written there first and a second copy for
 * the UI would be a second opinion about one fact.
 *
 * @param producerBlockers what `AppPermissionState.missingProducerPermissions()` returns.
 */
fun detectionPause(
    autoDetectParking: Boolean,
    producerBlockers: Set<RequiredPermission>,
): DetectionPause? = when {
    !autoDetectParking -> DetectionPause.TURNED_OFF
    producerBlockers.isNotEmpty() -> DetectionPause.MISSING_PERMISSIONS
    else -> null
}
