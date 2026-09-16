package com.rndeveloper.paparcar.presentation.permissions

import com.rndeveloper.paparcar.domain.permissions.AppPermissionState
import com.rndeveloper.paparcar.domain.permissions.missingCorePermissions
import com.rndeveloper.paparcar.domain.permissions.missingPermissions

/**
 * Which permission tier the permissions screen surfaces — exactly one, never "everything".
 * [DET-READY-001i, ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
 *
 * This enum is where the progressive-permissions invariant lives: *first-run shows and asks for
 * CORE only; every PRODUCER permission is asked from a contextual surface that already owns that
 * question* (the Home detection banner, the guided checklist, Settings' detection health). The
 * retired `All` variant was the first-run "wall of permissions" — a screen full of toggles shown
 * before the user had seen any value, burning system-dialog denials the contextual asks then
 * couldn't spend.
 */
enum class PermissionsFocus {
    /** First-run and "turn on location" (Blocked·CORE) — Essential section only. */
    Core,

    /** "Activate detection" (Blocked·PRODUCER) — Auto-detection + reliability sections. */
    Producer;

    companion object {
        /** Parses a nav argument, defaulting to [Core] (the minimal ask) for unknown / null values. */
        fun fromArg(arg: String?): PermissionsFocus =
            entries.firstOrNull { it.name.equals(arg, ignoreCase = true) } ?: Core
    }
}

/**
 * Which tier a "turn detection on" CTA has to open, or null when nothing is in the way.
 * [DET-TOGGLE-001]
 *
 * ⛔ The single action behind every such CTA is *flip the intent AND open whatever is missing*, so
 * every surface that offers it has to answer this the same way. It was a private method of
 * `SettingsViewModel` until the figures card grew its own CTA
 * [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]; a second copy would have been a second
 * opinion about which screen the user lands on, for the same missing permission.
 *
 * CORE outranks PRODUCER: with location itself denied or the GPS off there is nothing for the
 * background tier to be granted ON TOP of.
 */
fun detectionPermissionGap(permissions: AppPermissionState): PermissionsFocus? = when {
    permissions.missingCorePermissions().isNotEmpty() || !permissions.isLocationServicesEnabled ->
        PermissionsFocus.Core
    permissions.missingPermissions().isNotEmpty() -> PermissionsFocus.Producer
    else -> null
}
