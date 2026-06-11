package io.apptolast.paparcar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class VehicleAccent(val fill: Color, val on: Color)

/**
 * Stable per-vehicle colour palette for map badges and UI chips.
 *
 * Slot 0 is **reserved** for Bluetooth-paired vehicles so "blue ring = tracked via BT"
 * reads as a consistent visual language across the app. Non-BT vehicles cycle through
 * slots 1..7 keyed by [io.apptolast.paparcar.domain.model.ParkedVehicleSummary.stableRank]
 * (lexicographic vehicleId sort) — stable across restarts and list reorders.
 */
object VehicleAccentPalette {
    private val entries = listOf(
        VehicleAccent(fill = Color(0xFF5B9EFF), on = Color(0xFF061021)), // 0 · blue — reserved for BT-paired
        VehicleAccent(fill = Color(0xFFF4A825), on = Color(0xFF1C0900)), // 1 · amber
        VehicleAccent(fill = Color(0xFFE040FB), on = Color(0xFF1A0020)), // 2 · violet
        VehicleAccent(fill = Color(0xFF00BCD4), on = Color(0xFF001C20)), // 3 · cyan
        VehicleAccent(fill = Color(0xFFFF7043), on = Color(0xFF1C0700)), // 4 · deep-orange
        VehicleAccent(fill = Color(0xFF66BB6A), on = Color(0xFF002005)), // 5 · green
        VehicleAccent(fill = Color(0xFFEC407A), on = Color(0xFF1C0010)), // 6 · pink
        VehicleAccent(fill = Color(0xFFFFEE58), on = Color(0xFF1A1700)), // 7 · yellow
    )

    private const val BLUETOOTH_SLOT = 0
    private const val NON_BT_OFFSET = 1
    private val nonBtSize get() = entries.size - NON_BT_OFFSET // 7

    /** Reserved blue accent for Bluetooth-paired vehicles. */
    fun bluetooth(): VehicleAccent = entries[BLUETOOTH_SLOT]

    /**
     * Picks a non-blue accent for a non-BT vehicle, keyed by [stableRank]. Wraps
     * around the 7 non-blue slots so any number of vehicles is supported.
     */
    fun nonBluetooth(stableRank: Int): VehicleAccent =
        entries[NON_BT_OFFSET + (stableRank.coerceAtLeast(0) % nonBtSize)]

    /** Legacy direct accessor — kept for callers that already have an absolute slot index. */
    fun get(paletteIndex: Int): VehicleAccent = entries[paletteIndex % entries.size]
}
