package io.apptolast.paparcar.domain.diagnostics

/**
 * Identifies the physical device and build that produced a detection trace. [DIAG-READABLE-001]
 *
 * Field diagnostics run the same account on more than one phone at once (Oppo + Redmi in the same
 * car). Firestore session docs carry no device identifier, so attributing a trace used to require
 * triangulating by garage/anchor. Stamping [deviceModel]/[appVersion]/[osVersion] into the session
 * header makes attribution a glance instead of a guess.
 *
 * Platform-provided (Koin binding per source set), not `expect/actual`, to match the repo idiom for
 * platform contracts (`PermissionManager`, `ConnectivityObserver`, …).
 */
interface DeviceInfoProvider {

    /** Human-readable device, e.g. "Xiaomi Redmi Note 12" / "OPPO CPH2451" / "iPhone14,5". */
    val deviceModel: String

    /** App version name, e.g. "1.0.0-beta02". */
    val appVersion: String

    /** OS version, e.g. "Android 14 (SDK 34)" / "iOS 17.4". */
    val osVersion: String
}

/** Fallback used when no platform binding is available (keeps the logger constructible anywhere). */
object UnknownDeviceInfoProvider : DeviceInfoProvider {
    override val deviceModel: String = "unknown"
    override val appVersion: String = "unknown"
    override val osVersion: String = "unknown"
}
