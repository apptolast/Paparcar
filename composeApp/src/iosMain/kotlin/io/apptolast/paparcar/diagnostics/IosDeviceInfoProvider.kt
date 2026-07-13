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
}
