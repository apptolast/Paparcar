package io.apptolast.paparcar.core.crash

/**
 * Platform bridge for non-fatal error reporting.
 *
 * Android: reports to Firebase Crashlytics.
 * iOS: no-op until Firebase iOS SDK is integrated (FND-006).
 */
expect object CrashReporter {
    fun recordNonFatal(tag: String, message: String, throwable: Throwable)
}
