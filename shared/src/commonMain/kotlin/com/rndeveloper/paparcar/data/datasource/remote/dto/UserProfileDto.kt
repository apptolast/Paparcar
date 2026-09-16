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

    // ── Detection state. [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] ──────────────────────────
    //
    // ⛔ WRITE-ONLY, and the asymmetry is the design. These four are read back here so the DTO stays
    // a faithful mirror of the document (a `set(profile)` from a DTO that dropped them would wipe
    // them — the exact class of bug `FirestoreDeserializerParityTest` exists to catch), but they do
    // NOT reach `UserProfile` in domain and nothing in the app consumes them. The moment something
    // did, a stale remote value would start competing with the live permission state of the device,
    // which is the one source of truth that cannot be wrong.
    //
    // All four nullable with the same meaning of absence as `firstStepsDismissedAt`: missing on
    // every doc written before this ticket → null → **unknown**, never "off".

    /** Stored intent of the Settings master switch. Never read as "detection works" — cross it with
     *  [detectionBlockers] or the two populations collapse.
     *  [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001] */
    val autoDetectParking: Boolean? = null,
    /** PRODUCER permissions still missing, by `RequiredPermission` name. Empty list = nothing
     *  blocks detection; null = never synced. This is what separates "turned it off" from
     *  "cannot run". */
    val detectionBlockers: List<String>? = null,
    /** [DOZE-001] Battery whitelist granted. Absent on iOS, where the concept does not exist. */
    val batteryOptimizationExempt: Boolean? = null,
    /** When this snapshot was taken. A snapshot with no date cannot be told apart from a permission
     *  revoked three months ago, which would make the other three unreadable. */
    val detectionStateUpdatedAt: Long? = null,
)
