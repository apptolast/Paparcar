package io.apptolast.paparcar.presentation.vehicles

sealed class VehiclesIntent {
    data class SetActiveVehicle(val vehicleId: String) : VehiclesIntent()
    data class BluetoothVehicleConnected(val vehicleId: String?) : VehiclesIntent()
    data class SelectVehicle(val index: Int) : VehiclesIntent()
    data class EditVehicle(val vehicleId: String) : VehiclesIntent()
    data object AddVehicle : VehiclesIntent()
    data class SetHistoryFilter(val filter: HistoryFilter) : VehiclesIntent()
    data class ViewOnMap(val lat: Double, val lon: Double, val sessionId: String = "") : VehiclesIntent()
}
