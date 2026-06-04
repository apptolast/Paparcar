package io.apptolast.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.apptolast.paparcar.presentation.util.SpotReliabilityLevel
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState

data class SpotStateColors(val bg: Color, val on: Color)

private val isDark: Boolean
    @Composable get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
fun SpotReliabilityLevel.stateColors(): SpotStateColors = when (this) {
    SpotReliabilityLevel.HIGH   -> if (isDark) SpotStateColors(PapGreen,     PapInk)
                                   else        SpotStateColors(PapGreenLight, Color.White)
    SpotReliabilityLevel.MEDIUM -> if (isDark) SpotStateColors(PapAmber,     PapInk)
                                   else        SpotStateColors(PapAmberLight, Color.White)
    SpotReliabilityLevel.LOW    -> if (isDark) SpotStateColors(PapRed,       PapOnRed)
                                   else        SpotStateColors(PapRedLight,   Color.White)
    SpotReliabilityLevel.MANUAL -> if (isDark) SpotStateColors(PapBlue,      PapOnBlue)
                                   else        SpotStateColors(PapBlueLight,  Color.White)
}

@Composable
fun SpotReliabilityUiState.stateColors(): SpotStateColors = when (this) {
    SpotReliabilityUiState.HIGH   -> if (isDark) SpotStateColors(PapGreen,     PapInk)
                                     else        SpotStateColors(PapGreenLight, Color.White)
    SpotReliabilityUiState.MEDIUM -> if (isDark) SpotStateColors(PapAmber,     PapInk)
                                     else        SpotStateColors(PapAmberLight, Color.White)
    SpotReliabilityUiState.LOW    -> if (isDark) SpotStateColors(PapRed,       PapOnRed)
                                     else        SpotStateColors(PapRedLight,   Color.White)
    SpotReliabilityUiState.MANUAL -> if (isDark) SpotStateColors(PapBlue,      PapOnBlue)
                                     else        SpotStateColors(PapBlueLight,  Color.White)
}

@Composable
fun vehicleStateColors(): SpotStateColors =
    if (isDark) SpotStateColors(PapAmber, PapInk)
    else        SpotStateColors(PapAmberLight, Color.White)
