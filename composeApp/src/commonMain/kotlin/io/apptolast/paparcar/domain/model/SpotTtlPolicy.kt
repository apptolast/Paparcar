package io.apptolast.paparcar.domain.model

/**
 * Single source of truth for how long a published community spot stays live. [AUDIT-ARCH-001 M13]
 *
 * These TTLs were duplicated verbatim in the Android worker and the iOS scheduler (each with a
 * "mirrors the other" comment) — a business rule copy-pasted across platforms drifts the moment
 * one side is edited. It lives here in commonMain so both platforms compute the same lifetime
 * from the same numbers, and the manual-vs-auto rule is unit-testable once.
 */
object SpotTtlPolicy {
    /** Auto-detected (and home-geofence) spots: 2 hours — a parked-then-freed spot stays useful
     *  a while. */
    const val AUTO_SPOT_TTL_MS: Long = 2 * 60 * 60 * 1_000L

    /** Manually reported spots: 15 minutes — a human tap is a "right now" signal that goes stale
     *  fast. */
    const val MANUAL_SPOT_TTL_MS: Long = 15 * 60 * 1_000L

    /** The TTL for a spot of [type]: only an explicit manual report gets the short window;
     *  everything else (auto-detected, home-geofence) uses the long one. */
    fun ttlMsForType(type: SpotType): Long =
        if (type == SpotType.MANUAL_REPORT) MANUAL_SPOT_TTL_MS else AUTO_SPOT_TTL_MS
}
