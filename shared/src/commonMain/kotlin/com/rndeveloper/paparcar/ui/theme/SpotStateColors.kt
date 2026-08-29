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
    SpotFreshness.FRESH   -> if (isDark) SpotStateColors(PapGreen,     PapInk)
                                     else        SpotStateColors(PapGreenLight, Color.White)
    SpotFreshness.RECENT -> if (isDark) SpotStateColors(PapAmber,     PapInk)
                                     else        SpotStateColors(PapAmberLight, Color.White)
    SpotFreshness.STALE    -> if (isDark) SpotStateColors(PapRed,       PapOnRed)
                                     else        SpotStateColors(PapRedLight,   Color.White)
}
