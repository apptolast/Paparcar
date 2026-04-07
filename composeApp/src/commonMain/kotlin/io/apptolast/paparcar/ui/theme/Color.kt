package io.apptolast.paparcar.ui.theme

import androidx.compose.ui.graphics.Color

// ── Pap brand palette ─────────────────────────────────────────────────────────

// Primary accent
val PapGreen         = Color(0xFF25F48C)   // neon green — brand primary (dark theme)
val PapGreenDark     = Color(0xFF1AC070)   // pressed / hover variant

// Dark surfaces
val PapForest        = Color(0xFF0D1C14)   // deepest background
val PapForestMid     = Color(0xFF0F2218)   // card / sheet surface
val PapForestDark    = Color(0xFF0D3D2E)   // active-session hero card background
val PapForestMedium  = Color(0xFF1A5C40)   // icon box inside hero card
val PapGreenMuted    = Color(0xFF133D28)   // container / surface variant
val PapGreenElement  = Color(0xFF226D49)   // interactive elements, outline

// On-dark text
val PapOnDark        = Color(0xFFE8F5EC)   // primary text on dark surfaces
val PapOnDarkMuted   = Color(0xFF8EB5A0)   // secondary / disabled text
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

// ── Light theme counterparts ──────────────────────────────────────────────────

val PapGreenLight            = Color(0xFF006D38)
val PapOnGreenLight          = Color(0xFFFFFFFF)
val PapGreenContainerLight   = Color(0xFFB8F5CE)
val PapOnGreenContainerLight = Color(0xFF00210E)
val PapSurfaceLight          = Color(0xFFF5FBF4)  // page background (tinted)
val PapCardLight             = Color(0xFFFFFFFF)  // card / sheet surface — white
val PapOnSurfaceLight        = Color(0xFF00391A)
val PapVariantLight          = Color(0xFFDDE8DA)
val PapOnVariantLight        = Color(0xFF3A4A3C)
val PapOutlineLight          = Color(0xFF226D49)
val PapOutlineVariantLight   = Color(0xFFBECBC0)  // subtle dividers
val PapInverseSurfaceLight   = Color(0xFF0F2218)  // dark surface for Snackbar/Toast
val PapInverseOnSurfaceLight = Color(0xFFE8F5EC)  // text on inverse surface
val PapAmberLight            = Color(0xFFB56000)
val PapAmberContainerLight   = Color(0xFFFFDDB3)
val PapOnAmberContainerLight = Color(0xFF3D2A10)

// Light semantic counterparts
val PapRedLight       = Color(0xFFBA1A1A)
val PapRedContainerLight = Color(0xFFFFDAD6)
val PapBlueLight      = Color(0xFF0057CA)
val PapBlueContainerLight = Color(0xFFD8E2FF)
