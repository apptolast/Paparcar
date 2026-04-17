package io.apptolast.paparcar.presentation.mycar

sealed class MyCarIntent {
    data class SetActiveVehicle(val vehicleId: String) : MyCarIntent()
    data class RequestDeleteVehicle(val vehicleId: String) : MyCarIntent()
    data class ConfirmDeleteVehicle(val vehicleId: String) : MyCarIntent()
    data object DismissDeleteConfirmation : MyCarIntent()
    data class EditVehicle(val vehicleId: String) : MyCarIntent()
    data object AddVehicle : MyCarIntent()
}