package com.rndeveloper.paparcar.detection

import platform.Foundation.NSUserDefaults

/**
 * NSUserDefaults twin of Android's `UserStopStore` (SharedPreferences): the durable stamp behind
 * the user-stop quiet period. [DET-STOP-BUTTON-001] The DECISION is common
 * (`UserStopQuietPeriod.kt`); this only holds the moment. Same key semantics as its twin so the
 * two platforms' diagnostics read alike. [IOS-F1-A-CONTROLLER-FOR-THE-HAPPY-PATH-001]
 */
class IosUserStopStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) {
    /** Persist the stop moment (epoch-ms). */
    fun stamp(stoppedAtMs: Long) {
        defaults.setObject(stoppedAtMs.toString(), KEY)
    }

    /** The last stop moment, or null when none is on record. */
    fun stoppedAtMs(): Long? = (defaults.objectForKey(KEY) as? String)?.toLongOrNull()

    /** The user manually re-armed (the MANUAL trigger) — the quiet period ends early. */
    fun clear() {
        defaults.removeObjectForKey(KEY)
    }

    private companion object {
        // Namespaced like the F0 side-records; the trailing segment mirrors the Android key.
        const val KEY = "detection.user_stopped_at"
    }
}
