package com.rndeveloper.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.rndeveloper.paparcar.domain.model.SpotFreshness

data class SpotStateColors(val bg: Color, val on: Color)

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
    SpotFreshness.FRESH  -> if (isDark) SpotStateColors(PapSpotFresh,      PapInk)
                            else        SpotStateColors(PapSpotFreshLight, PapInk)
    SpotFreshness.RECENT -> if (isDark) SpotStateColors(PapAmber,           PapInk)
                            else        SpotStateColors(PapSpotCoolingLight, PapInk)
    SpotFreshness.STALE  -> if (isDark) SpotStateColors(PapRed,               PapOnRed)
                            else        SpotStateColors(PapSpotExpiringLight, Color.White)
}
