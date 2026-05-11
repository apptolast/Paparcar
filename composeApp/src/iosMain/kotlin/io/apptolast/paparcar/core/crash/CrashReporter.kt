package io.apptolast.paparcar.core.crash

import kotlin.native.concurrent.ThreadLocal

/**
 * Bridge contract Swift implements to forward crash reports into the Firebase
 * iOS SDK. Implemented in Swift because Firebase distributes its iOS SDK as
 * native frameworks (CocoaPods / SPM) — not currently wired into the KMP build,
 * so direct Kotlin/Native interop is not yet available.
 *
 * Wiring on Mac (one-time, in iosApp/iOSApp.swift):
 *
 *   import FirebaseCore
 *   import FirebaseCrashlytics
 *
 *   class SwiftCrashReporterBridge: CrashReporterBridge {
 *       func recordNonFatal(tag: String, message: String, error: String) {
 *           let userInfo: [String: Any] = ["tag": tag, "message": message]
 *           let err = NSError(
 *               domain: tag,
 *               code: 0,
 *               userInfo: userInfo.merging(["NSLocalizedDescription": error]) { a, _ in a }
 *           )
 *           Crashlytics.crashlytics().record(error: err)
 *       }
 *       func setUserId(userId: String?) {
 *           Crashlytics.crashlytics().setUserID(userId ?? "")
 *       }
 *   }
 *
 *   // in App init, after FirebaseApp.configure():
 *   CrashReporter.shared.bridge = SwiftCrashReporterBridge()
 */
interface CrashReporterBridge {
    fun recordNonFatal(tag: String, message: String, error: String)
    fun setUserId(userId: String?)
}

/**
 * iOS [CrashReporter] forwards every call to the [CrashReporterBridge] set by
 * Swift at app launch. Until the bridge is set (or on test/preview launches),
 * every call is a silent no-op — never throws.
 *
 * `@ThreadLocal` is intentional: keeps the bridge slot writeable from the main
 * thread without falling foul of Kotlin/Native's frozen-singleton rules (the
 * memory model relaxation in 1.7+ no longer requires this, but the annotation
 * is harmless and makes the intent explicit).
 */
@ThreadLocal
actual object CrashReporter {

    var bridge: CrashReporterBridge? = null

    actual fun recordNonFatal(tag: String, message: String, throwable: Throwable) {
        val description = throwable.message ?: throwable::class.simpleName ?: "Unknown error"
        bridge?.recordNonFatal(tag, message, description)
    }

    actual fun setUserId(userId: String?) {
        bridge?.setUserId(userId)
    }
}
