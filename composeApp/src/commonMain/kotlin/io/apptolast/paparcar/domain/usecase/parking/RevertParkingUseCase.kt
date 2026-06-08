package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.util.PaparcarLogger

/**
 * Reverts a previously auto-confirmed parking session.
 *
 * Invoked when the user taps "No, cancelar" on the post-save notification (state B in
 * the unified notification state machine — see docs/detection/PARKING-DETECTION.md).
 *
 * **Composición (v1).** No introduce nuevos esquemas Room/Firestore: solo encadena
 * operaciones ya existentes que son inversas a [ConfirmParkingUseCase]:
 *
 *  1. [UserParkingRepository.clearActiveParkingSession] — marca `isActive=false` en
 *     Room y propaga a Firestore. La sesión queda en el histórico como una sesión
 *     pasada inactiva. **TODO-REVERT-P1:** añadir `deleteSession` para borrarla del
 *     histórico cuando el usuario tira del botón "Cancelar" (semánticamente: "esto
 *     no era un aparcamiento, no quiero verlo en mi historial").
 *  2. [GeofenceManager.removeGeofence] — desregistra la geofence Android/iOS.
 *  3. Dismiss de la notificación 2002 — la única notificación visible para este
 *     evento (ya no se muestra la antigua "showParkingSaved" duplicada).
 *
 * **Spot comunitario.** No hay nada que retractar en este punto: el spot público se
 * publica únicamente cuando el usuario sale del geofence (vía [ReportSpotWorker]),
 * NO en el momento del save. Al haber removido la geofence, esa publicación nunca
 * llegará a dispararse. ✓
 *
 * **DepartureEventBus.** [ConfirmParkingUseCase] llama a `departureEventBus.reset()`
 * para evitar falsos departures al caminar lejos del coche. Tras revert no lo
 * tocamos: si la sesión nunca fue real, el siguiente `IN_VEHICLE_ENTER` repoblará
 * el bus correctamente. Tocarlo aquí sería resucitar un timestamp obsoleto.
 *
 * **Best-effort.** Cada paso loguea su fallo y continúa con el siguiente. La
 * idempotencia de cada operación (Room delete-by-id, GMS removeGeofences) lo
 * permite — un retry manual del usuario no rompe nada.
 */
class RevertParkingUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val geofenceService: GeofenceManager,
    private val notificationPort: AppNotificationManager,
) {

    suspend operator fun invoke(parkingId: String): Result<Unit> {
        PaparcarLogger.d(DIAG, "▶ RevertParking.invoke parkingId=$parkingId")

        val clearResult = userParkingRepository.clearActiveParkingSession(parkingId)
        clearResult.onFailure { e ->
            PaparcarLogger.e(DIAG, "  ✗ clearActiveParkingSession failed", e)
        }.onSuccess {
            PaparcarLogger.d(DIAG, "  ✓ session cleared (isActive=false in Room + queued for Firestore)")
        }

        val geofenceResult = geofenceService.removeGeofence(parkingId)
        geofenceResult.onFailure { e ->
            PaparcarLogger.w(DIAG, "  ⚠ removeGeofence failed (continuing)", e)
        }.onSuccess {
            PaparcarLogger.d(DIAG, "  ✓ geofence removed")
        }

        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        PaparcarLogger.d(DIAG, "■ RevertParking.invoke DONE")

        // Best-effort overall: if the user later sees the session still around because
        // a step failed, manual cleanup from the history screen is the fallback.
        return Result.success(Unit)
    }

    private companion object {
        const val DIAG = "PARKDIAG/Revert"
    }
}
