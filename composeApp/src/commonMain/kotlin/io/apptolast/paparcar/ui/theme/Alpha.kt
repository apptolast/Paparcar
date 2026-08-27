package io.apptolast.paparcar.ui.theme

/**
 * Text-emphasis alpha scale — the single source for every `onSurface.copy(alpha = …)` used to
 * de-emphasise text and small affordances. Before this object existed the same four numbers
 * lived as private constants in ~16 presentation files, with the same NAME meaning different
 * VALUES in different files (`SUBTITLE_ALPHA` = 0.55 in Settings, 0.65 in CarbodyInfoCard).
 * [SETTINGS-AUDIT-REMEDIATION-001]
 *
 * Levels, strongest to dimmest:
 * - [body]     0.65 — prose the user is meant to READ: dialog bodies, empty-state copy.
 * - [subtitle] 0.55 — the standard subtitle next to a full-strength title in cards/rows.
 * - [muted]    0.50 — supporting meta: hints, Settings row subtitles, trailing values.
 * - [disabled] 0.38 — disabled content (Material's canonical disabled alpha).
 * - [dim]      0.30 — the quietest affordances: chevrons, separators, disabled borders.
 *
 * Alphas for BORDERS/dividers stay in [PapBorders]; this scale is for content emphasis.
 */
object PapAlpha {
    const val body = 0.65f
    const val subtitle = 0.55f
    const val muted = 0.5f
    const val disabled = 0.38f
    const val dim = 0.3f
}
