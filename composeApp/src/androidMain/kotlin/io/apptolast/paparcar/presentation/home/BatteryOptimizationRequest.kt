package io.apptolast.paparcar.presentation.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.apptolast.customlogin.appContext
import io.apptolast.paparcar.domain.util.PaparcarLogger

/**
 * Opens the system `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog for this package — the same
 * request the onboarding shows, reachable from the honest watch badge. Launched from a non-Activity
 * context (Compose effect), so it needs `FLAG_ACTIVITY_NEW_TASK`. [DET-WATCH-HONEST-001]
 */
actual fun requestIgnoreBatteryOptimizations() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val ctx = appContext
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", ctx.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }.onFailure { PaparcarLogger.w("BatteryOptReq", "could not launch exemption request: ${it.message}") }
}
