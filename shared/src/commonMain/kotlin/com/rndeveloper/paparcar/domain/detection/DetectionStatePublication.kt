package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.permissions.RequiredPermission

/**
 * Whether this account's automatic detection can actually run — the fact that, until
 * [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001], existed only on the device.
 *
 * ⛔ **Deliberately NOT a boolean.** `SettingsState.autoDetectActive` is
 * `autoDetectParking && detectionHealthy` for a reason
 * ([DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001]): "the user turned it off" and "the user
 * wants it but is missing background location" are two populations with opposite remedies, and one
 * flag collapses them into the same silence. The intent and the blockers travel apart or the whole
 * measurement is worthless.
 *
 * [reliabilityReduced] is the third axis and the only one that does NOT reach Firestore — see
 * [remoteFingerprint].
 */
data class DetectionStateSnapshot(
    /** The stored intent of the Settings master switch. NOT "detection works". */
    val autoDetectParking: Boolean,
    /** PRODUCER permissions still missing. Empty = nothing blocks it. */
    val blockers: Set<RequiredPermission>,
    /** [DOZE-001] Battery whitelist. Its absence degrades detection on hostile OEMs, never blocks it. */
    val batteryOptimizationExempt: Boolean,
    /** The amber case: permissions fine, device environment untrusted. Analytics-only. */
    val reliabilityReduced: Boolean,
) {

    /**
     * The persisted subset, as one comparable string.
     *
     * Comparing THIS rather than the whole snapshot is the point: [reliabilityReduced] is not a
     * stored field, so letting it into the comparison would spend a Firestore write to change
     * nothing. The blockers are sorted by name because a `Set` has no order and an unstable
     * rendering would read as a change on every emission — which is exactly the write storm this
     * type exists to prevent.
     */
    fun remoteFingerprint(): String = buildString {
        append(if (autoDetectParking) '1' else '0')
        append('|')
        append(blockers.map { it.name }.sorted().joinToString(","))
        append('|')
        append(if (batteryOptimizationExempt) '1' else '0')
    }

    /**
     * Has anything a remote reader could see actually changed?
     *
     * `permissionState` is refreshed on every resume, so the naive wiring writes to Firestore once
     * per app opening, per user. The invariant: **a write happens when the snapshot CHANGES, not
     * when it is observed.** [lastPublishedFingerprint] is null the first time (nothing published
     * yet → publish).
     */
    fun needsPublishing(lastPublishedFingerprint: String?): Boolean =
        lastPublishedFingerprint != remoteFingerprint()
}

/**
 * The population bucket this account falls into, for the analytics user property.
 *
 * Closed vocabulary on purpose: Firebase caps user properties at 25 and a high-cardinality one does
 * not aggregate. The per-account detail (WHICH permission is missing) belongs to the Firestore
 * fields, not here — analytics counts populations.
 */
enum class DetectionStateLabel(val wire: String) {
    /** Wanted, permitted and in a trusted environment. */
    ON("on"),
    /** The user turned the master switch off. */
    OFF_BY_USER("off_by_user"),
    /** Wanted, but a PRODUCER permission is missing — the population that is broken without knowing. */
    BLOCKED_PERMISSIONS("blocked_permissions"),
    /** Wanted and permitted, but the OEM environment is hostile and the battery whitelist is absent. */
    REDUCED_RELIABILITY("reduced_reliability"),
}

/**
 * Precedence, and each step earns its place:
 *
 *  1. A deliberate OFF wins over everything. Someone who does not want detection is not "broken",
 *     and counting their missing permissions would inflate the very number this measures.
 *  2. A missing permission beats a reliability warning — a hard blocker outranks a degradation,
 *     the same order `SettingsState.detectionReliabilityReduced` already uses.
 */
fun DetectionStateSnapshot.label(): DetectionStateLabel = when {
    !autoDetectParking -> DetectionStateLabel.OFF_BY_USER
    blockers.isNotEmpty() -> DetectionStateLabel.BLOCKED_PERMISSIONS
    reliabilityReduced -> DetectionStateLabel.REDUCED_RELIABILITY
    else -> DetectionStateLabel.ON
}
