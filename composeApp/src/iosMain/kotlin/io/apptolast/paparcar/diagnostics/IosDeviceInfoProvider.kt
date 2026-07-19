package io.apptolast.paparcar.diagnostics

import io.apptolast.paparcar.domain.diagnostics.DeviceInfoProvider
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

/**
 * iOS [DeviceInfoProvider]: UIDevice model/system version + the bundle short version string.
 * [DIAG-READABLE-001]
 */
class IosDeviceInfoProvider : DeviceInfoProvider {

    override val deviceModel: String = UIDevice.currentDevice.model

    override val appVersion: String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
            ?: "unknown"

    override val osVersion: String = "iOS ${UIDevice.currentDevice.systemVersion}"

    // iOS has no autostart whitelist / OEM freeze daemon and no per-app battery-optimization toggle
    // the app can read — background survival is governed by BGTask budgets, not these switches.
    override val isBatteryUnrestricted: Boolean = true
    override val requiresAutostartWhitelist: Boolean = false
    override val requiresOemBatteryFreezeExemption: Boolean = false
}
