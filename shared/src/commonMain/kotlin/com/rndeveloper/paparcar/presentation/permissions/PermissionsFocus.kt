package com.rndeveloper.paparcar.presentation.permissions

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
