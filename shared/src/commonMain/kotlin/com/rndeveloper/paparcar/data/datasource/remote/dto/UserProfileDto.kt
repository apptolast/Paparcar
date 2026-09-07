package com.rndeveloper.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    val userId: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val defaultVehicleId: String? = null,
    /** When the user closed the guided checklist, or null. Absent on every doc written before
     *  [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] → null → "still
     *  wants the tutorial", which is what those accounts were. */
    val firstStepsDismissedAt: Long? = null,
)
