package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.detection.DetectionPath
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.service.DepartureEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.service.ParkingEnrichmentScheduler
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

/**
 * Repositions an existing active parking session to a new lat/lon. Used by the
 * manual "Move location" flow on Home (`HomeMode.AddingParking` with a non-null
 * `editingParkingId`) — the user drags the pin and confirms.
 *
 * Mirrors the side-effects of [ConfirmParkingUseCase], minus the "save new
 * session" step, since we're mutating an existing row in place:
 *
 * 1. Cancel the geofence registered for the existing session.
 * 2. Update the Room row's `location` field (+ clear cached address/POI so
 *    the re-scheduled enrichment overwrites them).
 * 3. Push the updated session to Firestore via `ParkingSyncScheduler`.
 * 4. Schedule a new enrichment pass to geocode the new location.
 * 5. Re-create the geofence at the new location, reusing the same `geofenceId`
 *    (= session id), so departure detection continues to fire for this car.
 *
 * No notification is shown — the user just took the action explicitly, so a
 * "parking saved" notification would be noise.
 */
class UpdateParkingLocationUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val geofenceService: GeofenceManager,
    private val enrichmentScheduler: ParkingEnrichmentScheduler,
    private val config: ParkingDetectionConfig,
    private val departureEventBus: DepartureEventBus,
) {
    suspend operator fun invoke(
        parkingId: String,
        newLocation: GpsPoint,
    ): Result<UserParking> {
        PaparcarLogger.d(DIAG, "▶ UpdateParkingLocation.invoke id=$parkingId")

        // Cancel the old geofence BEFORE the location update. Best-effort —
        // if the cancel fails (e.g. Play Services unavailable) we still want
        // the row update to land. The new geofence will use the same id so
        // any stale registration gets overwritten on `createGeofence`.
        geofenceService.removeGeofence(parkingId)
            .onFailure { e -> PaparcarLogger.w(DIAG, "removeGeofence failed (continuing)", e) }

        // ⛔ El reloj NO se mueve. Corregir cambia DÓNDE está el coche; lleva aparcado desde que se
        // aparcó, y la propia hoja lo promete: «Corregir mantiene el tiempo aparcado; marcar uno
        // nuevo lo pone a cero». El cero es verdad en el otro botón, que crea una sesión nueva.
        //
        // Se estampaba `now` «para que la línea temporal reflejase la corrección», y eso escribía
        // sobre TRES hechos a la vez, porque `location.timestamp` es el único reloj que hay:
        //   · «aparcado desde» que lee el usuario (campo 16-09: un pin de 8 min pasó a «hace <1m»),
        //   · la duración de la estancia en historial, racimos, mapa de calor y recap,
        //   · la LÍNEA DE NACIMIENTO DE SESIÓN de [DET-SESSION-BIRTH-001] — la que decide si una
        //     evidencia (AR ENTER, EXIT re-entregado, conexión BT) es admisible o describe el viaje
        //     que CREÓ este aparcamiento.
        // Mover la tercera era lo único defendible de las tres, y ya está cubierto sin mentir sobre
        // las otras dos: el `departureEventBus.reset()` de abajo borra el ENTER viejo, que es la
        // evidencia que el estampado neutralizaba. La sesión nace cuando el coche se dejó, no cuando
        // el usuario arregló el pin.
        // [PARK-A-CORRECTION-MOVES-THE-PLACE-NOT-THE-CLOCK-001]

        // [PARK-A-PIN-MUST-SAY-WHO-PLACED-IT-001] The drag re-writes the provenance with the
        // coordinates. It used to change only WHERE the pin was, leaving WHO placed it untouched,
        // so a corrected pin still credited the detector that had got it wrong — the one field a
        // user opens to ask exactly that. `user_moved` is deliberately not `manual`: this pin was
        // born by the detector and then corrected, and only that combination is a detection failure
        // worth chasing in a trace.
        val updated = userParkingRepository.updateParkingSessionPosition(
            id = parkingId,
            location = newLocation,
            detectionPath = DetectionPath.UserMovedPin.label,
            detectionReliability = USER_PLACED_RELIABILITY,
        )
            .getOrElse { e ->
                PaparcarLogger.e(DIAG, "updateLocation failed", e)
                return Result.failure(PaparcarError.Parking.SaveFailed)
            }

        enrichmentScheduler.enqueueEnrichSession(parkingId, newLocation.latitude, newLocation.longitude)

        // The geofence is being re-created at a new location. Reset the arrival
        // IN_VEHICLE_ENTER so the old driving timestamp cannot trigger a false
        // departure when the user walks near the new position. [BUG-WALK-DEPART-001]
        departureEventBus.reset()

        geofenceService.createGeofence(
            geofenceId = parkingId,
            latitude = newLocation.latitude,
            longitude = newLocation.longitude,
            radiusMeters = config.geofenceRadiusFor(updated.sizeCategory, newLocation.accuracy),
        )

        PaparcarLogger.d(DIAG, "■ UpdateParkingLocation.invoke SUCCESS id=$parkingId")
        return Result.success(updated)
    }

    private companion object {
        const val DIAG = "PARKDIAG/UpdateLoc"

        /** A pin a human pointed at is ground truth — same value `SaveManualParkingUseCase` gives a
         *  hand-placed pin. [PARK-A-PIN-MUST-SAY-WHO-PLACED-IT-001] */
        const val USER_PLACED_RELIABILITY = 1.0f
    }
}
