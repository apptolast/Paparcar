package com.rndeveloper.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ── Pap brand palette ─────────────────────────────────────────────────────────

// Primary accent
val PapGreen         = Color(0xFF25F48C)   // neon green — brand primary (dark theme)

// ── Ink ramp — near-black surfaces (dark theme primary surfaces) ─────────────
// Neutral-cool dark palette; green brand DNA lives in accent tokens, not surfaces.
val PapInkDeep       = Color(0xFF08090E)   // surfaceContainerLowest — behind the map
val PapInk           = Color(0xFF0D1117)   // surface — app base
val PapInkContainer  = Color(0xFF131A24)   // surfaceContainer — sheet, nav
val PapInkHigh       = Color(0xFF1A2232)   // surfaceContainerHigh — cards
val PapInkHighest    = Color(0xFF222C3E)   // surfaceContainerHighest — modals, popovers

// Forest greens — demoted from surface to interactive accents (containers, outlines)
val PapForest        = Color(0xFF0D1C14)   // legacy — kept for light-theme inverseSurface
val PapGreenMuted    = Color(0xFF133D28)   // primaryContainer — dark green accent
val PapGreenOutline  = Color(0xFF226D49)   // secondary-action border — visible but non-neon green

// On-dark text
val PapNeutralOutline      = Color(0xFF3B4A5E)   // neutral-cool outline for dark surfaces
val PapNeutralOutlineLight = Color(0xFF7A8FA0)   // neutral-cool outline for light surfaces

val PapOnDark        = Color(0xFFEBF2EF)   // primary text on dark surfaces
val PapOnDarkMuted   = Color(0xFF8EA0B4)   // secondary / disabled text — neutral cool

// Amber (secondary / warning)
val PapAmber         = Color(0xFFF4A825)
val PapAmberMuted    = Color(0xFF3D2A10)

// ── Semantic status tokens — dark theme ───────────────────────────────────────

// Spot reliability: HIGH → neon green (reuses PapGreen / PapGreenMuted)

// Spot reliability: MEDIUM → amber (reuses PapAmber / PapAmberMuted)

// Spot reliability: LOW / urgency
val PapRed           = Color(0xFFFF5252)   // urgent / low TTL / error
val PapRedMuted      = Color(0xFF3D1010)   // red container dark
val PapOnRed         = Color(0xFF1C0606)

// ⛔ Framework `tertiary` slot backing ONLY — the tertiary role is RETIRED [UI-COLOR-DOCTRINE-001 F6].
// These tokens exist solely so the MaterialTheme scheme slots hold sane values (same hue family as
// papCarBlue, so anything a framework component paints with tertiary blends into the my-car blue).
// Feature code must never read `colorScheme.tertiary*` — enforced by ColorGuardrailTest.
val PapBlue          = Color(0xFF5B9EFF)
val PapBlueMuted     = Color(0xFF0F1F3D)
val PapOnBlue        = Color(0xFF061021)

// ── Live — MOVEMENT ON THE MAP, fixed across themes. [UI-COLOR-DOCTRINE-001] ──────────────────
// One meaning, map-only: the trip trail, the trip-origin dot, the "reserved · someone en route"
// spot, and the follow-car FAB tint. Markers and polylines sit on street/satellite imagery, not on
// our surface, so this tone never theme-inverts. Replaces PapDriveBlue + SpotPalette.EnRouteBlue +
// LOC_HALO_BLUE. In the UI, movement is told by ANIMATION in the vehicle's identity colour — there
// is no "driving blue" accent anymore.
val PapLiveMap       = Color(0xFF2F6BFF)   // trail, origin dot, en-route pin, follow FAB — fixed

// ── Car blue — the BLUETOOTH-watched vehicle's identity colour. ───────────────────────────────
// A vehicle's colour is its WATCH METHOD (green = active detection, blue = BT, grey = off) and it
// never changes with state; [papCarBlue] is the blue leg, resolved via
// `VehicleIdentity.vehicleIdentityColor`. Values keep the old "BT blue" the user already reads as
// their BT-tracked car. [UI-COLOR-DOCTRINE-001]
val PapCarBlueDark   = Color(0xFF5B9EFF)   // BT identity on dark surfaces — 7.0:1 on PapInk
val PapCarBlueLight  = Color(0xFF0057CA)   // BT identity on light surfaces — 6.5:1 on white

// ── Light theme counterparts ──────────────────────────────────────────────────

// ── Azure ramp — blue-navy surfaces for light theme (H≈217°, mirrors PapInk DNA) ─
// Same hue family as the dark PapInk ramp but at the opposite luminosity pole,
// so light and dark themes share the same cool blue character at both extremes.
val PapAzureLowest   = Color(0xFFFFFFFF)   // surfaceContainerLowest
val PapAzureLow      = Color(0xFFF4F6FC)   // surfaceContainerLow
val PapAzure         = Color(0xFFECF0F9)   // surfaceContainer — sheet, nav

// Teal-shifted emerald ramp — same hue DNA as PapGreen (#25F48C ≈ H150°).
// #009F5E → H152°, L31% — electric/vibrant. onPrimary = Color.White in light theme
// so filled surfaces (buttons, chips, icon circles) use white content on the green fill.
val PapGreenLight            = Color(0xFF009F5E)
val PapGreenOutlineLight     = Color(0xFF009F5E)  // secondary-action border — primary green reads as border in light
val PapGreenContainerLight   = Color(0xFFA8F5D0)
val PapOnGreenContainerLight = Color(0xFF002819)
val PapSurfaceLight          = Color(0xFFF0F4FB)  // page background — blue-tinted (H217°)
val PapCardLight             = Color(0xFFFFFFFF)  // card / sheet surface — white
val PapOnSurfaceLight        = Color(0xFF0E1A2E)  // primary text — deep navy
val PapVariantLight          = Color(0xFFD8E0EE)  // surfaceVariant — blue-grey
val PapOnVariantLight        = Color(0xFF374460)  // onSurfaceVariant — blue-dark
val PapOutlineVariantLight   = Color(0xFFBDC8DF)  // subtle dividers — blue-grey
val PapInverseSurfaceLight   = Color(0xFF0D1B33)  // dark navy for Snackbar/Toast
val PapInverseOnSurfaceLight = Color(0xFFE8EDF8)  // text on inverse surface
val PapAmberLight            = Color(0xFFB56000)
val PapAmberContainerLight   = Color(0xFFFFDDB3)
val PapOnAmberContainerLight = Color(0xFF3D2A10)

// Light semantic counterparts
val PapRedLight       = Color(0xFFBA1A1A)
val PapBlueLight      = Color(0xFF0057CA)
val PapBlueContainerLight = Color(0xFFD8E2FF)

/** Theme-aware secondary-action border green — outlined buttons, the sheet edit icon-button.
 *  Same luminance probe as `SpotStateColors.stateColors()`. [UI-SHEET-001] */
val greenOutline: Color
    @Composable get() =
        if (MaterialTheme.colorScheme.surface.luminance() < SURFACE_DARK_LUMINANCE) PapGreenOutline
        else PapGreenOutlineLight

/**
 * Theme-aware **Bluetooth identity** accent — the colour of a BT-watched vehicle's name, glyph,
 * badge and border. Never for community spots, never for a state, never for anything that is not
 * the user's own BT-watched vehicle. Resolve through `vehicleIdentityColor`, not directly.
 * [UI-COLOR-DOCTRINE-001]
 */
val papCarBlue: Color
    @Composable get() =
        if (MaterialTheme.colorScheme.surface.luminance() < SURFACE_DARK_LUMINANCE) PapCarBlueDark
        else PapCarBlueLight

/** Luminance below which a surface counts as "dark" for picking a theme variant. */
internal const val SURFACE_DARK_LUMINANCE = 0.5f
