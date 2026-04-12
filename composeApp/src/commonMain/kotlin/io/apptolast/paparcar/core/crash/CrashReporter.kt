package io.apptolast.paparcar.core.crash

/**
 * Platform bridge for non-fatal error reporting.
 *
 * Android: logs to Firebase Crashlytics via [FirebaseCrashlytics.recordException].
 * iOS:     no-op stub; replace with FirebaseCrashlytics iOS SDK once integrated,
 *          or use NSLog / OSLog as a lightweight alternative.
 */
expect object CrashReporter {
    fun recordNonFatal(tag: String, message: String, throwable: Throwable)
}
