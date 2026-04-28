package io.apptolast.paparcar.core.crash

/**
 * Platform bridge for crash/error reporting.
 *
 * Android: delegates to Firebase Crashlytics.
 * iOS:     no-op stub; replace with FirebaseCrashlytics iOS SDK once integrated.
 */
expect object CrashReporter {
    /** Records a non-fatal exception so it appears in the Crashlytics dashboard. */
    fun recordNonFatal(tag: String, message: String, throwable: Throwable)

    /**
     * Associates subsequent crash reports with [userId].
     * Call on every successful sign-in so issues can be triaged per user.
     * Pass `null` to clear the association (e.g. after sign-out).
     */
    fun setUserId(userId: String?)
}
