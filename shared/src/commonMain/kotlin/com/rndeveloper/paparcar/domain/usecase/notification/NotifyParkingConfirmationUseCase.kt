package com.rndeveloper.paparcar.domain.usecase.notification

import com.rndeveloper.paparcar.domain.model.ParkingConfidence
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.firstOrNull

class NotifyParkingConfirmationUseCase(
    private val notificationPort: AppNotificationManager,
    private val vehicleRepository: VehicleRepository,
) {
    suspend operator fun invoke(confidence: ParkingConfidence) {
        PaparcarLogger.d(DIAG, "▶ NotifyParkingConfirmation.invoke confidence=$confidence")
        val vehicleName = vehicleRepository.observeActiveVehicle().firstOrNull()?.displayName()
        PaparcarLogger.d(DIAG, "  observeActiveVehicle resolved vehicleName=$vehicleName")
        when (confidence) {
            is ParkingConfidence.Low -> notificationPort.showParkingConfirmation(0f, vehicleName)
            is ParkingConfidence.Medium -> notificationPort.showParkingConfirmation(confidence.score, vehicleName)
            is ParkingConfidence.High -> notificationPort.showParkingConfirmation(confidence.score, vehicleName)
            is ParkingConfidence.NotYet -> Unit
        }
        PaparcarLogger.d(DIAG, "■ NotifyParkingConfirmation.invoke DONE")
    }

    private fun Vehicle.displayName(): String? = displayName(fallback = "").takeIf { it.isNotBlank() }

    private companion object {
        const val DIAG = "PARKDIAG/Notify"
    }
}
