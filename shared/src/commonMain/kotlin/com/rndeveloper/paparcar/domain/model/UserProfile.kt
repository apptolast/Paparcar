package com.rndeveloper.paparcar.domain.model

data class UserProfile(
    val userId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * Cached pointer to the user's currently active vehicle. Mirrors the
     * `isActive = true` row in the vehicles subcollection.
     *
     * Used by the splash bootstrap as a fast "does this account have a vehicle"
     * check — avoids paying for a list query just to decide the start route.
     * Stays null until the user registers their first vehicle.
     */
    val defaultVehicleId: String? = null,
    /**
     * When this USER closed the guided first-steps checklist — skipped it, or acknowledged its
     * closing card. Null while they have not.
     * [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
     *
     * The one half of the tutorial's state that belongs to the ACCOUNT and not to the phone: "I do
     * not want this checklist" is a fact about the person, and it used to die with the device's
     * DataStore — so reinstalling, or being restored from a Google backup, brought the tutorial
     * back mid-way to someone who had already finished it.
     *
     * ⛔ Deliberately NOT mirrored into the Room `user_profile` row: the only reader is the splash
     * bootstrap, which already holds the REMOTE dto, and the one case where Room would answer
     * instead (an offline cold start) is an install whose own preferences already know. Reading it
     * as null from the Room-cached path is therefore harmless BY CONSTRUCTION — the restore is
     * monotonic, a null can only fail to restore a dismissal, never clear one.
     */
    val firstStepsDismissedAt: Long? = null,
)
