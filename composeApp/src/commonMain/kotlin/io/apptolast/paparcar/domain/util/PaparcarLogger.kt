package io.apptolast.paparcar.domain.util

import io.apptolast.paparcar.core.crash.CrashReporter
import io.github.aakira.napier.Napier

/**
 * Minimal structured logger for Paparcar backed by Napier.
 *
 * Napier must be initialised before first use:
 * - Android: call [initLogging] in `Application.onCreate()`
 * - iOS:     call `NapierProxyKt.debugBuild()` in the Swift entry point (debug only)
 *
 * Error-level logs with a [Throwable] are also forwarded to [CrashReporter]
 * for non-fatal tracking in Firebase Crashlytics (Android).
 */
object PaparcarLogger {

    fun d(tag: String, message: String) {
        Napier.d(message, tag = tag)
    }

    fun i(tag: String, message: String) {
        Napier.i(message, tag = tag)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Napier.w(message, throwable = throwable, tag = tag)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Napier.e(message, throwable = throwable, tag = tag)
        throwable?.let { CrashReporter.recordNonFatal(tag, message, it) }
    }
}
