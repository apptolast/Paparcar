package io.apptolast.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState

data class SpotStateColors(val bg: Color, val on: Color)

private val isDark: Boolean
    @Composable get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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

/**
 * Per-vehicle accent for the "this is *yours*, parked" visual molde — dark interior
 * + accent ring + accent-tinted icon. Mirrors the Paparcar logo language.
 *
 * Semantic mapping:
 *  - [isBluetoothPaired] = true → **blue** ring (BT slot). One consistent "blue = BT"
 *    language across the app.
 *  - [isBluetoothPaired] = false → cycles through the 7 non-blue slots keyed by
 *    [stableRank] (from [io.apptolast.paparcar.domain.model.ParkedVehicleSummary]).
 *    Null [stableRank] falls back to the amber slot.
 */
@Composable
fun parkedVehicleAccent(
    stableRank: Int? = null,
    isBluetoothPaired: Boolean = false,
): VehicleAccent = when {
    isBluetoothPaired -> VehicleAccentPalette.bluetooth()
    else              -> VehicleAccentPalette.nonBluetooth(stableRank ?: 0)
}
