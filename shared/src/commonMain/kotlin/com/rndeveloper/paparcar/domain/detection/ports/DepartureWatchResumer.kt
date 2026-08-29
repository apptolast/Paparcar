package com.rndeveloper.paparcar.domain.detection.ports

import com.rndeveloper.paparcar.domain.detection.ServicePresence

/**
 * Rebuilds the resident departure watcher (the Coordinator foreground service in
 * [ServicePresence.Sentry]) after the OS killed it or it never came up. [DET-WATCH-REACTIVATE-001]
 *
 * A background worker cannot legally start a foreground service on Android 12+, so resurrection only
 * happens in a foreground moment: the app being visible, or the user tapping "Reactivate" on the
 * honest watch row. Both go through here — one place decides whether a resume is warranted, using the
 * SAME rule the service's own idle epilogue applies ([resolvePostDetectionLifecycle]), so the two can
 * never disagree about whether the watcher should exist.
 *
 * No-op on iOS (no equivalent resident service).
 */
interface DepartureWatchResumer {
    /**
     * Ask the departure watcher to come back up.
     *
     * @param source short tag for diagnostics — who asked (`foreground-gap`, `home-cta`).
     * @param force the user asked EXPLICITLY (a CTA tap): bypasses the cooldown that keeps automatic
     *   retries from looping. An explicit tap must always do something visible. [DET-WATCH-REACTIVATE-001]
     * @return true when a resume was actually issued (the watcher should come up within a moment);
     *   false when there was nothing to watch, detection is off, or the platform refused the start.
     */
    suspend fun resume(source: String, force: Boolean = false): Boolean
}
