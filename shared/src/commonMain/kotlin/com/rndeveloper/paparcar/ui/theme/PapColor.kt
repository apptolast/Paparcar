package com.rndeveloper.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Paparcar colour ROLES — the single vocabulary the feature layer asks colour by.
 *
 * ## Why roles, when there is already a `colorScheme`
 *
 * Because `colorScheme.primary` was measured doing **nine different jobs** across 50 call sites in
 * 26 files: the action of a CTA, the selection of an option, the focus of a text field, an achieved
 * permission tier, brand furniture in charts, the identity of an actively-watched vehicle, the map's
 * user dot, a spinner — and, in `VehicleColorLabels`, an *absent* datum. A token asked for by nine
 * names it does not have cannot be reasoned about, and cannot be changed for one job without
 * changing it for the other eight.
 *
 * This is the colour twin of the type system's rule. There, *the role owns its weight*. Here:
 *
 * > **The colour is decided by the JOB, not by the widget. Two jobs may share a hex only when this
 * > file says they are the same promise — and never when they can be seen at the same time.**
 *
 * Several roles below deliberately resolve to the same value today. That is not redundancy: a named
 * role can diverge later without archaeology, and naming it forces the sharing to be a decision
 * someone wrote down instead of an accident nobody can date.
 *
 * ## What does NOT live here
 *
 * - **Vehicle identity** — `vehicleIdentityColor(watch)` in `VehicleIdentity.kt`. A vehicle's colour
 *   is its watch METHOD and has exactly one resolver.
 * - **Spot freshness** — `SpotReliabilityUiState.stateColors()` in `SpotStateColors.kt`. The ramp is
 *   exclusive to community spots' expiry.
 * - **Neutrals** — `onSurface` / `onSurfaceVariant` + the `PapAlpha` scale. They are unambiguous
 *   already; wrapping them would be ceremony.
 *
 * Full doctrine and the token table: `docs/design/COLOR-SYSTEM.md`.
 * [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
 */
object PapColor {

    // ── Brand family ─────────────────────────────────────────────────────────────────────────
    // These five share the brand green today. They are the app doing its job, in five different
    // grammatical moods — and none of them is ever a vehicle or a spot.

    /** Something you can PRESS: filled CTA, link, footer button, dialog confirm. */
    val action: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

    /** Content ON a filled [action] surface. */
    val onAction: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimary

    /**
     * The brand green when it spells WORDS on a light surface — a link, a figure, a label, the text
     * of a borderless button. Same story as [action]; different job, and jobs carry the contrast
     * floor: filling a shape needs 3:1, being read needs 4.5:1.
     *
     * In the dark theme this IS [action] — a vivid green on a near-black bed already clears the bar,
     * so there is nothing to trade. Only the light theme pays, and it pays in exactly one place
     * instead of dulling the brand everywhere, which is what was tried and revoked on device.
     * [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]
     */
    val actionText: Color
        @Composable get() =
            if (MaterialTheme.colorScheme.surface.luminance() < SURFACE_DARK_LUMINANCE) {
                MaterialTheme.colorScheme.primary
            } else {
                PapGreenTextLight
            }

    /** "This option is the CHOSEN one": type/size/colour selectors, segmented rows, page dots.
     *  Selection is said with colour + border + check — never with type weight. */
    val selected: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

    /** The field that currently holds the CURSOR. */
    val focus: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

    /** A step that is ACHIEVED: a granted permission, a reached detection tier. Its unreached
     *  sibling is [unknown], and its blocked sibling is [danger] — never a dimmer green. */
    val progress: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

    /** Brand FURNITURE over data: chart bars and figures, section icon tiles, list overlines,
     *  stat numbers. The page is green in every vehicle's ficha — the car's identity lives only in
     *  its own method anatomy (border + badge + dot + pill), a call re-affirmed on device 28-08. */
    val brandData: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

    // ── Things that need you ─────────────────────────────────────────────────────────────────

    /** Something is PENDING and you can fix it: a permission not yet granted, poor GPS. */
    val attention: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary

    /** Content ON a filled [attention] surface. */
    val onAttention: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSecondary

    /** BLOCKED, destructive or failed. Never a CTA — `PapRed` in a call to action is banned. */
    val danger: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error

    /** Content ON a filled [danger] surface. */
    val onDanger: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onError

    // ── Map ──────────────────────────────────────────────────────────────────────────────────

    /** MOVEMENT over map tiles — the trip trail, the origin dot, the en-route pin, the follow-car
     *  FAB. Fixed across themes because it sits on street imagery, not on our surface. It is not an
     *  identity: a moving car is told by ANIMATION in its own identity colour. */
    val live: Color get() = PapLiveMap

    // ── Absence ──────────────────────────────────────────────────────────────────────────────

    /** A datum we do NOT have: a vehicle with no colour recorded, an empty slot. Neutral on
     *  purpose. Painting an absent value with the brand green dresses a hole as identity — which is
     *  exactly what `VehicleColorLabels.swatchColor()` did until this ticket. */
    val unknown: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
}
