package io.apptolast.paparcar.domain.usecase.notification

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class NotifyParkingConfirmationUseCase(
    private val notificationPort: AppNotificationManager,
    private val vehicleRepository: VehicleRepository,
) {
    operator fun invoke(confidence: ParkingConfidence) {
        val vehicleName = runBlocking { vehicleRepository.observeDefaultVehicle().firstOrNull() }
            ?.displayName()
        when (confidence) {
            is ParkingConfidence.Low -> notificationPort.showParkingConfirmation(0f, vehicleName)
            is ParkingConfidence.Medium -> notificationPort.showParkingConfirmation(confidence.score, vehicleName)
            is ParkingConfidence.High -> notificationPort.showParkingConfirmation(confidence.score, vehicleName)
            is ParkingConfidence.NotYet -> Unit
        }
    }

    private fun Vehicle.displayName(): String? =
        listOfNotNull(brand, model).joinToString(" ").ifBlank { null }
}