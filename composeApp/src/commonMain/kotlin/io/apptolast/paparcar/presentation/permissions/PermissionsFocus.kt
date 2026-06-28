package io.apptolast.paparcar.presentation.permissions

/**
 * Which permission tier(s) the permissions screen should surface. [DET-READY-001i]
 *
 * Reached from an in-Home detection CTA we focus on just the relevant tier (a small, on-purpose
 * sheet) instead of the full first-run list; first-run uses [All].
 */
enum class PermissionsFocus {
    /** First-run onboarding — show every section. */
    All,

    /** "Turn on location" (Blocked·CORE) — Essential section only. */
    Core,

    /** "Activate detection" (Blocked·PRODUCER) — Auto-detection + reliability sections. */
    Producer;

    companion object {
        /** Parses a nav argument, defaulting to [All] for unknown / null values. */
        fun fromArg(arg: String?): PermissionsFocus =
            entries.firstOrNull { it.name.equals(arg, ignoreCase = true) } ?: All
    }
}
