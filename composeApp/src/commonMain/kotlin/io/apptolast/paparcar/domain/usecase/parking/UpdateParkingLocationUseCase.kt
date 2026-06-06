@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

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

        // Stamp the timestamp to "now" so the session timeline reflects the
        // user's deliberate correction, not the auto-detect moment. Match
        // ConfirmParkingUseCase's behaviour of normalising timestamps at write
        // time.
        val stamped = newLocation.copy(timestamp = Clock.System.now().toEpochMilliseconds())

        val updated = userParkingRepository.updateParkingSessionPosition(parkingId, stamped)
            .getOrElse { e ->
                PaparcarLogger.e(DIAG, "updateLocation failed", e)
                return Result.failure(PaparcarError.Parking.SaveFailed)
            }

        enrichmentScheduler.enqueueEnrichSession(parkingId, stamped.latitude, stamped.longitude)

        // The geofence is being re-created at a new location. Reset the arrival
        // IN_VEHICLE_ENTER so the old driving timestamp cannot trigger a false
        // departure when the user walks near the new position. [BUG-WALK-DEPART-001]
        departureEventBus.reset()

        geofenceService.createGeofence(
            geofenceId = parkingId,
            latitude = stamped.latitude,
            longitude = stamped.longitude,
            radiusMeters = computeGeofenceRadius(updated.sizeCategory, stamped.accuracy),
        )

        PaparcarLogger.d(DIAG, "■ UpdateParkingLocation.invoke SUCCESS id=$parkingId")
        return Result.success(updated)
    }

    private fun computeGeofenceRadius(sizeCategory: VehicleSize?, accuracyMeters: Float): Float {
        val base = when (sizeCategory) {
            VehicleSize.MOTO -> config.geofenceRadiusMotoMeters
            VehicleSize.LARGE -> config.geofenceRadiusLargeMeters
            VehicleSize.VAN -> config.geofenceRadiusVanMeters
            else -> config.geofenceRadiusMeters
        }
        val padded = base + (accuracyMeters * config.geofenceAccuracyPadFactor)
        return padded.coerceAtMost(config.geofenceMaxRadiusMeters)
    }

    private companion object {
        const val DIAG = "PARKDIAG/UpdateLoc"
    }
}
