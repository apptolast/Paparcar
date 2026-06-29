package io.apptolast.paparcar.ui.theme

import androidx.compose.ui.graphics.Color

// ── Pap brand palette ─────────────────────────────────────────────────────────

// Primary accent
val PapGreen         = Color(0xFF25F48C)   // neon green — brand primary (dark theme)
val PapGreenDark     = Color(0xFF1AC070)   // pressed / hover variant

// ── Ink ramp — near-black surfaces (dark theme primary surfaces) ─────────────
// Neutral-cool dark palette; green brand DNA lives in accent tokens, not surfaces.
val PapInkDeep       = Color(0xFF08090E)   // surfaceContainerLowest — behind the map
val PapInk           = Color(0xFF0D1117)   // surface — app base
val PapInkContainer  = Color(0xFF131A24)   // surfaceContainer — sheet, nav
val PapInkHigh       = Color(0xFF1A2232)   // surfaceContainerHigh — cards
val PapInkHighest    = Color(0xFF222C3E)   // surfaceContainerHighest — modals, popovers

// Forest greens — demoted from surface to interactive accents (containers, outlines)
val PapForest        = Color(0xFF0D1C14)   // legacy — kept for light-theme inverseSurface
val PapForestMid     = Color(0xFF0F2218)   // legacy — kept for reference, no longer a surface
val PapForestDark    = Color(0xFF0D3D2E)   // active-session hero card background
val PapForestMedium  = Color(0xFF1A5C40)   // icon box inside hero card
val PapGreenMuted    = Color(0xFF133D28)   // primaryContainer — dark green accent
val PapGreenElement  = Color(0xFF226D49)   // outline — interactive borders

// On-dark text
val PapNeutralOutline      = Color(0xFF3B4A5E)   // neutral-cool outline for dark surfaces
val PapNeutralOutlineLight = Color(0xFF7A8FA0)   // neutral-cool outline for light surfaces

val PapOnDark        = Color(0xFFEBF2EF)   // primary text on dark surfaces
val PapOnDarkMuted   = Color(0xFF8EA0B4)   // secondary / disabled text — neutral cool
val PapOnGreenMuted  = Color(0xFF9CBCAC)   // text on surface variant

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

// Spot reliability: MANUAL (manual reports)
val PapBlue          = Color(0xFF5B9EFF)   // manual report — info / neutral
val PapBlueMuted     = Color(0xFF0F1F3D)   // blue container dark
val PapOnBlue        = Color(0xFF061021)

// Live "driving / en-route" blue — matches the driving puck halo + en-route spot pin (0xFF2F6BFF).
// Fixed brand tone (not theme-inverted) so the "Driving" chip reads identical to its map marker.
val PapDriveBlue     = Color(0xFF2F6BFF)

// ── Light theme counterparts ──────────────────────────────────────────────────

// ── Azure ramp — blue-navy surfaces for light theme (H≈217°, mirrors PapInk DNA) ─
// Same hue family as the dark PapInk ramp but at the opposite luminosity pole,
// so light and dark themes share the same cool blue character at both extremes.
val PapAzureLowest   = Color(0xFFFFFFFF)   // surfaceContainerLowest
val PapAzureLow      = Color(0xFFF4F6FC)   // surfaceContainerLow
val PapAzure         = Color(0xFFECF0F9)   // surfaceContainer — sheet, nav
val PapAzureHigh     = Color(0xFFE0E6F4)   // surfaceContainerHigh — cards, chips
val PapAzureHighest  = Color(0xFFD4DCF0)   // surfaceContainerHighest — modals

// Teal-shifted emerald ramp — same hue DNA as PapGreen (#25F48C ≈ H150°).
// #009F5E → H152°, L31% — electric/vibrant. onPrimary = Color.White in light theme
// so filled surfaces (buttons, chips, icon circles) use white content on the green fill.
val PapGreenLight            = Color(0xFF009F5E)
val PapOnGreenLight          = Color(0xFF001E12)
val PapGreenContainerLight   = Color(0xFFA8F5D0)
val PapOnGreenContainerLight = Color(0xFF002819)
val PapSurfaceLight          = Color(0xFFF0F4FB)  // page background — blue-tinted (H217°)
val PapCardLight             = Color(0xFFFFFFFF)  // card / sheet surface — white
val PapOnSurfaceLight        = Color(0xFF0E1A2E)  // primary text — deep navy
val PapVariantLight          = Color(0xFFD8E0EE)  // surfaceVariant — blue-grey
val PapOnVariantLight        = Color(0xFF374460)  // onSurfaceVariant — blue-dark
val PapOutlineLight          = Color(0xFF226D49)
val PapOutlineVariantLight   = Color(0xFFBDC8DF)  // subtle dividers — blue-grey
val PapInverseSurfaceLight   = Color(0xFF0D1B33)  // dark navy for Snackbar/Toast
val PapInverseOnSurfaceLight = Color(0xFFE8EDF8)  // text on inverse surface
val PapAmberLight            = Color(0xFFB56000)
val PapAmberContainerLight   = Color(0xFFFFDDB3)
val PapOnAmberContainerLight = Color(0xFF3D2A10)

// Light semantic counterparts
val PapRedLight       = Color(0xFFBA1A1A)
val PapRedContainerLight = Color(0xFFFFDAD6)
val PapBlueLight      = Color(0xFF0057CA)
val PapBlueContainerLight = Color(0xFFD8E2FF)
