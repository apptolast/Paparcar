package io.apptolast.paparcar.domain.usecase.notification

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class NotifyParkingConfirmationUseCase(
    private val notificationPort: AppNotificationManager,
    private val vehicleRepository: VehicleRepository,
) {
    operator fun invoke(confidence: ParkingConfidence) {
        PaparcarLogger.d(DIAG, "▶ NotifyParkingConfirmation.invoke confidence=$confidence")
        PaparcarLogger.d(DIAG, "  → entering runBlocking { observeDefaultVehicle.firstOrNull() }")
        val vehicleName = runBlocking { vehicleRepository.observeDefaultVehicle().firstOrNull() }
            ?.displayName()
        PaparcarLogger.d(DIAG, "  ← runBlocking returned, vehicleName=$vehicleName")
        when (confidence) {
            is ParkingConfidence.Low -> notificationPort.showParkingConfirmation(0f, vehicleName)
            is ParkingConfidence.Medium -> notificationPort.showParkingConfirmation(confidence.score, vehicleName)
            is ParkingConfidence.High -> notificationPort.showParkingConfirmation(confidence.score, vehicleName)
            is ParkingConfidence.NotYet -> Unit
        }
        PaparcarLogger.d(DIAG, "■ NotifyParkingConfirmation.invoke DONE")
    }

    private fun Vehicle.displayName(): String? =
        listOfNotNull(brand, model).joinToString(" ").ifBlank { null }

    private companion object {
        const val DIAG = "PARKDIAG/Notify"
    }
}