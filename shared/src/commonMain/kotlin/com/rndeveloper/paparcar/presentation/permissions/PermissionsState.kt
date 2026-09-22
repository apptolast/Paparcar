package com.rndeveloper.paparcar.presentation.permissions

import com.rndeveloper.paparcar.domain.model.DetectionTier
import com.rndeveloper.paparcar.domain.permissions.SystemPermissionDialog

data class PermissionsState(
    val hasFineLocation: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val hasActivityRecognition: Boolean = false,
    val hasNotifications: Boolean = false,
    val isLocationServicesEnabled: Boolean = false,
    /** Ubicación concedida pero APROXIMADA — solo ocurre en iOS 14+. Se pinta su propia fila.
     *  [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001] */
    val hasReducedLocationAccuracy: Boolean = false,
    /** El dispositivo tiene con qué reconocer actividad. Si no, esa fila no se pinta: no es una
     *  tarea pendiente del usuario, es un permiso que aquí no existe. */
    val isActivityRecognitionAvailable: Boolean = true,
    /** Diálogos que el sistema ya no va a presentar — su fila abre los Ajustes.
     *  Ver [SystemPermissionDialog]. */
    val spentDialogs: Set<SystemPermissionDialog> = emptySet(),
    /** El tier de detección está completo. Se COPIA de `AppPermissionState.hasProducerPermissions`
     *  en vez de recalcularse aquí: reescribir la expresión sobre los campos de la pantalla sería
     *  una segunda opinión sobre la misma pregunta, y las dos derivarían.
     *  [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001] */
    val producerGranted: Boolean = false,
    /** Optional — Bluetooth parking detection only. Does not block navigation. */
    val hasBluetoothConnect: Boolean = false,
    /** Optional — battery optimization exemption. Does not block navigation.
     *  Critical on Doze-aggressive OEMs (MIUI, ColorOS, EMUI). [DOZE-001] */
    val isBatteryOptimizationExempt: Boolean = false,
    /** Optional — OEM autostart / background-activity whitelist. Only shown on
     *  manufacturers that ship the toggle (MIUI, ColorOS, EMUI, OriginOS…).
     *  We can't read its actual state (no public API), so we track whether the
     *  user has tapped the button and opened settings. [BUG-DETECT-OEM-KILLER-001] */
    val showAutostartCard: Boolean = false,
    /** `true` once the user has tapped the autostart button this session, used to
     *  show the card in the "granted" visual state (optimistic — we trust the user
     *  granted it after opening settings). Resets on process death. */
    val hasAcknowledgedAutostart: Boolean = false,
    /** Optional — OEM battery / power management settings for OplusHansManager (ColorOS).
     *  Hans freezes background processes with SIGSTOP independent of the autostart toggle.
     *  Only shown on OPPO/Realme. [OEM-002] */
    val showOemBatteryCard: Boolean = false,
    /** `true` once the user has tapped the OEM battery button this session. */
    val hasAcknowledgedOemBattery: Boolean = false,
    /** Reliability evaluator says REDUCED (aggressive OEM + no BT pairing + no exemption) — the
     *  optional tier swaps its generic battery hint for the honest manufacturer-policy callout.
     *  User-level copy only: cause → consequence → remedies, never internals. [DET-RELIABILITY-001] */
    val isReliabilityReduced: Boolean = false,
    /** The honest detection tier the user is on RIGHT NOW, shown as a status header so they know
     *  what to expect and what unlocks the next tier. Defaults to the lowest promise until the
     *  reliability stream emits. [DET-TIERS-001] */
    val currentTier: DetectionTier = DetectionTier.ASSISTED,
    val showRationale: Boolean = false,
    val showSettingsPrompt: Boolean = false,
    /** Foreground location is denied AND the system will no longer show its dialog (permanently
     *  denied / revoked from settings). Detected on Android via shouldShowRequestPermissionRationale
     *  + a "have we asked" flag, so the CTA points to system settings from the first frame — no need
     *  to tap through the request → rationale → settings escalation. [DET-READY-001m] */
    val locationPermanentlyDenied: Boolean = false,
    /** Show step-by-step guide before opening system Settings for background location.
     *  Android 11+ takes the user directly to Settings with no dialog — without this
     *  guide users don't know they must select "Allow all the time" then press Back. */
    val showBackgroundLocationGuide: Boolean = false,
) {
    /**
     * `true` si pulsar esa fila NO puede levantar un diálogo y tiene que abrir los Ajustes.
     *
     * Une las dos fuentes que saben de esto: el conjunto que rellena la plataforma
     * ([spentDialogs], exacto en iOS) y el flag que empuja la pantalla de Android para la ubicación
     * ([locationPermanentlyDenied], que allí necesita la Activity). Un solo concepto, dos orígenes.
     * [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001] [DET-READY-001m]
     */
    fun dialogSpent(dialog: SystemPermissionDialog): Boolean =
        dialog in spentDialogs ||
            (dialog == SystemPermissionDialog.LOCATION && locationPermanentlyDenied)
}
