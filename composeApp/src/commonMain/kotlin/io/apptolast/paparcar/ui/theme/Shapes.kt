package io.apptolast.paparcar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material 3 default shapes wired into PaparcarTheme. Components that use
// `MaterialTheme.shapes.medium` etc. resolve through this table.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Semantic corner-radius tokens for Paparcar surfaces.
 *
 * Tiered by visual hierarchy so every screen reads as one family:
 *
 *  - [chip]:      999dp pill — filter chips, tabs, badges, status pills
 *  - [cardSmall]: 14dp       — inner cards / tight rows / empty-state surfaces
 *  - [card]:      16dp       — standard card (matches M3 medium)
 *  - [cardLarge]: 18dp       — hero card (vehicle, permissions, weekly chart)
 *  - [sheet]:     top 20dp   — bottom sheet surface
 *  - [dialog]:    22dp       — modal dialog ([PapAlertDialog])
 *
 * Use these instead of `RoundedCornerShape(N.dp)` whenever a surface fits
 * one of the tiers. Reserve raw values for situational shapes (icon
 * containers, skeleton placeholders, decorative inner boxes).
 */
object PapShapes {
    val chip = RoundedCornerShape(999.dp)
    val cardSmall = RoundedCornerShape(14.dp)
    val card = RoundedCornerShape(16.dp)
    val cardLarge = RoundedCornerShape(18.dp)
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val dialog = RoundedCornerShape(22.dp)
}
