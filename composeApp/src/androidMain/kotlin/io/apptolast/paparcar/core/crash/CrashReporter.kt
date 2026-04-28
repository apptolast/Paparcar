package io.apptolast.paparcar.core.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics

actual object CrashReporter {
    actual fun recordNonFatal(tag: String, message: String, throwable: Throwable) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("tag", tag)
        crashlytics.log("$tag: $message")
        crashlytics.recordException(throwable)
    }

    actual fun setUserId(userId: String?) {
        runCatching {
            FirebaseCrashlytics.getInstance().setUserId(userId ?: "")
        }
    }
}
