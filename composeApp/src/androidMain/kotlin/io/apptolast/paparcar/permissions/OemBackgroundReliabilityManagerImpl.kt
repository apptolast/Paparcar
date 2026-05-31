package io.apptolast.paparcar.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager
import io.apptolast.paparcar.domain.util.PaparcarLogger

class OemBackgroundReliabilityManagerImpl(
    private val context: Context,
) : OemBackgroundReliabilityManager {

    private val manufacturer = Build.MANUFACTURER.lowercase()

    override val requiresAutostartWhitelist: Boolean =
        manufacturer in OEM_MANUFACTURERS

    // OplusHansManager (ColorOS) freezes processes with SIGSTOP every ~10 s regardless
    // of the autostart whitelist. Only present on OPPO and Realme. [OEM-002]
    override val requiresOemBatterySettings: Boolean =
        manufacturer in OEM_HANS_MANUFACTURERS

    override suspend fun launchOemBatterySettings(): Boolean {
        val candidates = BATTERY_INTENT_CANDIDATES[manufacturer].orEmpty()
        for ((pkg, cls) in candidates) {
            if (tryStart(pkg, cls)) {
                PaparcarLogger.d(TAG, "Opened OEM battery settings via $pkg/$cls")
                return true
            }
        }
        PaparcarLogger.w(TAG, "All battery intents failed for '$manufacturer'; falling back to standard battery settings")
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            false
        } catch (e: Exception) {
            openAppInfoFallback()
            false
        }
    }

    override suspend fun launchAutostartSettings(): Boolean {
        val candidates = INTENT_CANDIDATES[manufacturer].orEmpty()
        for ((pkg, cls) in candidates) {
            if (tryStart(pkg, cls)) {
                PaparcarLogger.d(TAG, "Opened autostart settings via $pkg/$cls")
                return true
            }
        }
        PaparcarLogger.w(TAG, "All vendor intents failed for '$manufacturer'; falling back to app info")
        openAppInfoFallback()
        return false
    }

    private fun tryStart(pkg: String, cls: String): Boolean = try {
        val intent = Intent().apply {
            component = ComponentName(pkg, cls)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        PaparcarLogger.d(TAG, "  ✗ $pkg/$cls not available (${e.javaClass.simpleName})")
        false
    }

    private fun openAppInfoFallback() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            PaparcarLogger.e(TAG, "Fallback ACTION_APPLICATION_DETAILS_SETTINGS also failed", e)
        }
    }

    private companion object {
        const val TAG = "OemAutostart"

        // Lower-cased manufacturer ids known to require an autostart whitelist.
        // Honor was spun off from Huawei in 2020 but kept the same system manager.
        val OEM_MANUFACTURERS = setOf(
            "xiaomi", "redmi", "poco",
            "oppo", "realme", "oneplus",
            "vivo", "iqoo",
            "huawei", "honor",
        )

        // Subset that ships OplusHansManager (ColorOS) — the process-freeze daemon
        // that sends SIGSTOP to background apps even when whitelisted for autostart.
        val OEM_HANS_MANUFACTURERS = setOf("oppo", "realme")

        // Per-OEM power-management intents to guide the user past the Hans freeze list.
        // OplusHansManager is exposed via the standard ColorOS Battery/Power manager.
        val BATTERY_INTENT_CANDIDATES: Map<String, List<Pair<String, String>>> = mapOf(
            "oppo" to listOf(
                "com.coloros.oppoguardelf" to "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
                "com.coloros.oppoguardelf" to "com.coloros.powermanager.PowerAppListActivity",
                "com.oplus.battery" to "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
                "com.coloros.healthcheck" to "com.coloros.healthcheck.ui.activity.LaunchActivity",
            ),
            "realme" to listOf(
                "com.coloros.oppoguardelf" to "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
                "com.coloros.oppoguardelf" to "com.coloros.powermanager.PowerAppListActivity",
            ),
        )

        // Per-OEM list of (pkg, activity) candidates. We try each in order until
        // one succeeds — OEMs rename / move these activities between major ROM
        // versions, so a fixed list per vendor is fragile and a *list* of fallbacks
        // is the realistic shape.
        val INTENT_CANDIDATES: Map<String, List<Pair<String, String>>> = mapOf(
            "xiaomi" to listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
                "com.miui.securitycenter" to "com.miui.permcenter.MainActivity",
            ),
            "redmi" to listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            "poco" to listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            "oppo" to listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            ),
            "realme" to listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            ),
            "oneplus" to listOf(
                "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            ),
            "vivo" to listOf(
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            ),
            "iqoo" to listOf(
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            ),
            "huawei" to listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            ),
            "honor" to listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
        )
    }
}
