package io.apptolast.paparcar.detection

sealed class DrivingEvent {
    object VehicleEnter : DrivingEvent()
    object VehicleExit : DrivingEvent()
}