package io.apptolast.paparcar.domain.permissions

/**
 * Abstracts manufacturer-specific background-execution gates that sit on top of
 * stock Android permissions. Xiaomi (MIUI) and Oppo (ColorOS) — among others —
 * expose an "Autostart" / "Background activity" toggle that defaults to OFF and,
 * when off, prevents the OS from ever reviving the app after it has been killed
 * in background. Without that whitelist, `ParkingDetectionService` cannot survive
 * a long doze cycle no matter what runtime permissions are granted. [BUG-DETECT-OEM-KILLER-001]
 *
 * The implementation is platform-specific:
 *  - androidMain: detects [android.os.Build.MANUFACTURER] and launches the OEM
 *    settings activity (with vendor-specific fallbacks).
 *  - iosMain: no equivalent gate exists; [requiresAutostartWhitelist] is `false`.
 */
interface OemBackgroundReliabilityManager {

    /**
     * `true` if the current device's manufacturer requires the user to enable a
     * proprietary autostart / background-activity whitelist for the app to survive
     * background-kill. Used by the UI to decide whether to show the autostart card
     * at all — on stock Android (Pixel/Samsung) and on iOS this returns `false`.
     */
    val requiresAutostartWhitelist: Boolean

    /**
     * Launches the manufacturer's autostart settings screen so the user can flip
     * the toggle. Returns `true` if a vendor-specific intent succeeded, `false`
     * if all candidates failed and we fell back to the generic app-info screen.
     *
     * Safe to call on devices where [requiresAutostartWhitelist] is `false` — the
     * caller should still gate on that flag to avoid showing the card in the first
     * place.
     */
    suspend fun launchAutostartSettings(): Boolean
}
