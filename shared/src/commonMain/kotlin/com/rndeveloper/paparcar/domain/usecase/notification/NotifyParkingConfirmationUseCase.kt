package com.rndeveloper.paparcar.domain.usecase.notification

import com.rndeveloper.paparcar.domain.model.GpsPoint
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
    private val resolveAskedStreet: ResolveAskedStreetUseCase,
) {
    /** @param candidate [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] where the question is about. */
    suspend operator fun invoke(confidence: ParkingConfidence, candidate: GpsPoint? = null) {
        PaparcarLogger.d(DIAG, "▶ NotifyParkingConfirmation.invoke confidence=$confidence")
        val vehicleName = vehicleRepository.observeActiveVehicle().firstOrNull()?.displayName()
        PaparcarLogger.d(DIAG, "  observeActiveVehicle resolved vehicleName=$vehicleName")
        // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Resolved BEFORE the post, never after —
        // see the use case for why a later re-post would restart the response window.
        val street = resolveAskedStreet(candidate)
        when (confidence) {
            is ParkingConfidence.Low ->
                notificationPort.showParkingConfirmation(0f, vehicleName, candidate, street)
            is ParkingConfidence.Medium ->
                notificationPort.showParkingConfirmation(confidence.score, vehicleName, candidate, street)
            is ParkingConfidence.High ->
                notificationPort.showParkingConfirmation(confidence.score, vehicleName, candidate, street)
            is ParkingConfidence.NotYet -> Unit
        }
        PaparcarLogger.d(DIAG, "■ NotifyParkingConfirmation.invoke DONE")
    }

    private fun Vehicle.displayName(): String? = displayName(fallback = "").takeIf { it.isNotBlank() }

    private companion object {
        const val DIAG = "PARKDIAG/Notify"
    }
}
