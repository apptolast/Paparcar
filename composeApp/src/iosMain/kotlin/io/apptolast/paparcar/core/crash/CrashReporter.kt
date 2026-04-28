package io.apptolast.paparcar.core.crash

// No-op until Firebase iOS SDK is integrated (FND-006).
actual object CrashReporter {
    actual fun recordNonFatal(tag: String, message: String, throwable: Throwable) = Unit
    actual fun setUserId(userId: String?) = Unit
}
