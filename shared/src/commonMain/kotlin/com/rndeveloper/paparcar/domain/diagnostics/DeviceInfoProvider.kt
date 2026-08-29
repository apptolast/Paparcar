package com.rndeveloper.paparcar.domain.diagnostics

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

    /**
     * [DET-SESSION-RELIABILITY-STAMP-001] The background-survival state at the moment it is read —
     * stamped into the session header at arm time so a trace that dies silently says WHETHER the
     * exemptions were in place, instead of leaving us to guess why the OS killed it.
     *
     * Standard Android battery exemption (`isIgnoringBatteryOptimizations`). The one survival input
     * the OS lets us READ; read live per access (it can be revoked between drives). `true` when the
     * concept does not apply (iOS, unknown), so a device without the notion is never flagged.
     */
    val isBatteryUnrestricted: Boolean

    /** This manufacturer kills background work unless the app is on its autostart whitelist
     *  (Xiaomi/Redmi/Oppo/Vivo/Huawei families). Static per device; NOT verifiable by API, so a
     *  trace can only say the whitelist was REQUIRED, never that it was granted. */
    val requiresAutostartWhitelist: Boolean

    /** Ships the ColorOS OplusHans freeze daemon (Oppo/Realme) — SIGSTOPs the process every ~10 s
     *  even with a running foreground service AND an autostart whitelist entry. The setting that
     *  disables it is neither readable nor grantable by API. */
    val requiresOemBatteryFreezeExemption: Boolean

    /**
     * [DET-HEARTBEAT-MISS-IS-EVIDENCE-001] The exact-alarm heartbeat has stopped being delivered on
     * this device, so the safety net is running on the 15-minute periodic grid ALONE.
     *
     * Unlike the two flags above this is not a property of the model — it is measured, live, from
     * the app's own armed-and-never-returned ticks. Field 2026-08-21/22, Oppo CPH2371: three hours
     * of periodic ticks on the dot with not one heartbeat, while the process was alive with a
     * running foreground service, the standby bucket was EXEMPTED and the exact-alarm permission
     * was granted. The OS is an acceptable excuse for a lost trip only if it is detectable
     * afterwards; without this flag it took a cable and `dumpsys` to see.
     *
     * `false` when the concept does not apply (iOS, unknown), so a device without the notion is
     * never flagged.
     */
    val isExactHeartbeatLaneDead: Boolean
}

/** Fallback used when no platform binding is available (keeps the logger constructible anywhere). */
object UnknownDeviceInfoProvider : DeviceInfoProvider {
    override val deviceModel: String = "unknown"
    override val appVersion: String = "unknown"
    override val osVersion: String = "unknown"
    override val isBatteryUnrestricted: Boolean = true
    override val requiresAutostartWhitelist: Boolean = false
    override val requiresOemBatteryFreezeExemption: Boolean = false
    override val isExactHeartbeatLaneDead: Boolean = false
}
