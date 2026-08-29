package com.rndeveloper.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.rndeveloper.paparcar.domain.model.SpotFreshness

/**
 * @param bg the tier as a FILL — a meter segment, a puck, a badge background. Floor 3:1.
 * @param on content drawn ON [bg].
 * @param text the tier when it spells a WORD on the app surface ("FIABLE", the peek eyebrow, an
 *   accent row). Floor 4.5:1, which a fill-bright colour cannot meet on a white card — in the light
 *   theme this is a deeper leg of the same tier, and in the dark theme it IS [bg], because a vivid
 *   colour on a near-black bed already clears the bar. [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]
 */
data class SpotStateColors(val bg: Color, val on: Color, val text: Color)

private val isDark: Boolean
    @Composable get() = MaterialTheme.colorScheme.surface.luminance() < SURFACE_DARK_LUMINANCE

@Composable
fun SpotFreshness.stateColors(): SpotStateColors = when (this) {
    // FRESH was the BRAND green (PapGreen / PapGreenLight) — a stranger's free spot painted in the
    // colour of our own CTAs. The ramp is exclusive to spots, so its head gets its own hue.
    //
    // The light legs are the ramp's OWN tokens now, not the theme's warning-amber and error-red.
    // Borrowing those made the whole set read muddy, because a colour that has to mean "error" is
    // dark by necessity. One ramp, the same three tones here and on the map.
    // [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
    SpotFreshness.FRESH  -> if (isDark) SpotStateColors(PapSpotFresh, PapInk, PapSpotFresh)
                            else        SpotStateColors(PapSpotFreshLight, PapInk, PapSpotFreshDeep)
    SpotFreshness.RECENT -> if (isDark) SpotStateColors(PapAmber, PapInk, PapAmber)
                            else        SpotStateColors(PapSpotCoolingLight, PapInk, PapAmberLight)
    SpotFreshness.STALE  -> if (isDark) SpotStateColors(PapRed, PapOnRed, PapRed)
                            else        SpotStateColors(PapSpotExpiringLight, Color.White, PapRedLight)
}
