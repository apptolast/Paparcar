package io.apptolast.paparcar.presentation.mycar

sealed class MyCarIntent {
    data class SetActiveVehicle(val vehicleId: String) : MyCarIntent()
    data object AddVehicle : MyCarIntent()
}