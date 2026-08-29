package com.rndeveloper.paparcar.domain.util

import com.rndeveloper.paparcar.core.crash.CrashReporter
import io.github.aakira.napier.LogLevel
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

    /**
     * The same line, built only if anything is listening.
     *
     * The detection loop logs ~47 lines per fix, every one of them an interpolated string assembled
     * whether or not any antilog is installed — a few dozen throwaway `StringBuilder`s per GPS
     * sample, for the whole length of a drive, on a device the feature is already asking to keep
     * its radio warm.
     *
     * ⚠️ Since [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001] the parkdiag file sink is installed in
     * RELEASE too, so this guard no longer short-circuits there — the lines are built and written
     * because that file is what a user's problem report ships. The guard still earns its keep on
     * builds with no sink at all (iOS release), and the write path it feeds was made cheap for
     * exactly this reason (one open stream, one flush per entry — see `FileAntilog.write`).
     *
     * `inline` matters: without it the lambda is an allocation of its own and the fix is a wash.
     */
    inline fun d(tag: String, message: () -> String) {
        if (Napier.isEnable(LogLevel.DEBUG, tag)) Napier.d(message(), tag = tag)
    }

    fun i(tag: String, message: String) {
        Napier.i(message, tag = tag)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Napier.w(message, throwable = throwable, tag = tag)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Napier.e(message, throwable = throwable, tag = tag)
        throwable?.let { runCatching { CrashReporter.recordNonFatal(tag, message, it) } }
    }
}
