package com.rndeveloper.paparcar.presentation.vehicles

sealed class VehiclesIntent {
    data class SetActiveVehicle(val vehicleId: String) : VehiclesIntent()
    data class BluetoothVehicleConnected(val vehicleId: String?) : VehiclesIntent()
    data class SelectVehicle(val index: Int) : VehiclesIntent()
    data class EditVehicle(val vehicleId: String) : VehiclesIntent()
    data object AddVehicle : VehiclesIntent()
    data class SetHistoryFilter(val filter: HistoryFilter) : VehiclesIntent()
    data class ViewOnMap(val lat: Double, val lon: Double, val sessionId: String = "") : VehiclesIntent()

    /** The "Ver todo con Plus" footer under a gated history. [MONEY-001] */
    data object OpenPlusPaywall : VehiclesIntent()

    /** The Plus row under the figures card. Distinct from [OpenPlusPaywall] so the funnel can tell
     *  which of the two surfaces on this page sold the upgrade. [STATS-UI-001] */
    data object OpenStatsPaywall : VehiclesIntent()

    /** Plus tapping "ver desglose completo" — the panel opens on THIS car. [STATS-PANEL-001] */
    data class OpenStatsPanel(val vehicleId: String) : VehiclesIntent()
}
