package com.rndeveloper.paparcar.diagnostics

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.rndeveloper.paparcar.detection.ExactHeartbeatScheduler
import com.rndeveloper.paparcar.domain.diagnostics.DeviceInfoProvider
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.permissions.OemBackgroundReliabilityManager

/**
 * Android [DeviceInfoProvider]: manufacturer+model, app version (from the package), OS release, and
 * the background-survival state stamped into each session header. [DIAG-READABLE-001]
 * [DET-SESSION-RELIABILITY-STAMP-001]
 */
class AndroidDeviceInfoProvider(
    private val context: Context,
    private val oemReliability: OemBackgroundReliabilityManager,
    private val config: ParkingDetectionConfig,
) : DeviceInfoProvider {

    override val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    override val appVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: UNKNOWN

    override val osVersion: String = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    // Read live per access: the exemption can be revoked between drives, so a cached val would lie.
    override val isBatteryUnrestricted: Boolean
        get() = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)

    override val requiresAutostartWhitelist: Boolean = oemReliability.requiresAutostartWhitelist

    override val requiresOemBatteryFreezeExemption: Boolean = oemReliability.requiresOemBatterySettings

    // [DET-HEARTBEAT-MISS-IS-EVIDENCE-001] Measured, not modelled — and read live per access, like
    // the battery exemption: a lane can die (or come back) between two sessions of the same day.
    override val isExactHeartbeatLaneDead: Boolean
        get() = runCatching { ExactHeartbeatScheduler.isLaneDead(context, config) }.getOrDefault(false)

    private companion object {
        const val UNKNOWN = "unknown"
    }
}
