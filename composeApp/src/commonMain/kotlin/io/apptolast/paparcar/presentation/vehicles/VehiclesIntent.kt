package io.apptolast.paparcar.presentation.vehicles

sealed class VehiclesIntent {
    data class SetActiveVehicle(val vehicleId: String) : VehiclesIntent()
    data class BluetoothVehicleConnected(val vehicleId: String?) : VehiclesIntent()
    data class RequestDeleteVehicle(val vehicleId: String) : VehiclesIntent()
    data class ConfirmDeleteVehicle(val vehicleId: String) : VehiclesIntent()
    data object DismissDeleteConfirmation : VehiclesIntent()
    data class SelectVehicle(val index: Int) : VehiclesIntent()
    data class EditVehicle(val vehicleId: String) : VehiclesIntent()
    data object AddVehicle : VehiclesIntent()
}
