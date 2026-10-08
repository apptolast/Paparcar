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
        // [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001] El NOMBRE y el ID salen del MISMO vehículo y en
        // la misma lectura. Antes se tomaba sólo el nombre y el id se tiraba aquí mismo, que es el
        // punto exacto donde la pregunta dejaba de saber de qué coche hablaba — y con varios coches
        // eso deja la noticia sin card a la que mudarse.
        val askedVehicle = vehicleRepository.observeActiveVehicle().firstOrNull()
        val vehicleName = askedVehicle?.displayName()
        PaparcarLogger.d(DIAG, "  observeActiveVehicle resolved vehicleName=$vehicleName id=${askedVehicle?.id}")
        // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Resolved BEFORE the post, never after —
        // see the use case for why a later re-post would restart the response window.
        val street = resolveAskedStreet(candidate)
        when (confidence) {
            is ParkingConfidence.Low ->
                notificationPort.showParkingConfirmation(0f, vehicleName, askedVehicle?.id, candidate, street)
            is ParkingConfidence.Medium ->
                notificationPort.showParkingConfirmation(confidence.score, vehicleName, askedVehicle?.id, candidate, street)
            is ParkingConfidence.High ->
                notificationPort.showParkingConfirmation(confidence.score, vehicleName, askedVehicle?.id, candidate, street)
            is ParkingConfidence.NotYet -> Unit
        }
        PaparcarLogger.d(DIAG, "■ NotifyParkingConfirmation.invoke DONE")
    }

    private fun Vehicle.displayName(): String? = displayName(fallback = "").takeIf { it.isNotBlank() }

    private companion object {
        const val DIAG = "PARKDIAG/Notify"
    }
}
