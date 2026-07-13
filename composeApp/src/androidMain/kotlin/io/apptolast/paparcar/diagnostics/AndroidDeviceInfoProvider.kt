package io.apptolast.paparcar.diagnostics

import android.content.Context
import android.os.Build
import io.apptolast.paparcar.domain.diagnostics.DeviceInfoProvider

/**
 * Android [DeviceInfoProvider]: manufacturer+model, app version (from the package), and OS release.
 * [DIAG-READABLE-001]
 */
class AndroidDeviceInfoProvider(context: Context) : DeviceInfoProvider {

    override val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    override val appVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: UNKNOWN

    override val osVersion: String = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    private companion object {
        const val UNKNOWN = "unknown"
    }
}
