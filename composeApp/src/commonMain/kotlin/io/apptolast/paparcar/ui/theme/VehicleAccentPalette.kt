package io.apptolast.paparcar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class VehicleAccent(val fill: Color, val on: Color)

/**
 * Stable per-vehicle colour palette for map badges and UI chips.
 *
 * Index is assigned by sorting vehicleIds lexicographically (see ObserveParkedVehiclesUseCase)
 * so the colour is consistent across restarts and list reorders.
 * Wrap-around with mod ensures any number of vehicles is supported.
 */
object VehicleAccentPalette {
    private val entries = listOf(
        VehicleAccent(fill = Color(0xFF5B9EFF), on = Color(0xFF061021)), // blue
        VehicleAccent(fill = Color(0xFFF4A825), on = Color(0xFF1C0900)), // amber
        VehicleAccent(fill = Color(0xFFE040FB), on = Color(0xFF1A0020)), // violet
        VehicleAccent(fill = Color(0xFF00BCD4), on = Color(0xFF001C20)), // cyan
        VehicleAccent(fill = Color(0xFFFF7043), on = Color(0xFF1C0700)), // deep-orange
        VehicleAccent(fill = Color(0xFF66BB6A), on = Color(0xFF002005)), // green
        VehicleAccent(fill = Color(0xFFEC407A), on = Color(0xFF1C0010)), // pink
        VehicleAccent(fill = Color(0xFFFFEE58), on = Color(0xFF1A1700)), // yellow
    )

    fun get(paletteIndex: Int): VehicleAccent = entries[paletteIndex % entries.size]
}
