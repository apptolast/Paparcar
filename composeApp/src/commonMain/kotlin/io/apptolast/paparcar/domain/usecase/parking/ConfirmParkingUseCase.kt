@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.util.haversineMeters
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persists a confirmed parking spot, registers a geofence, notifies the user,
 * and schedules background enrichment with geocoder address + POI data.
 *
 * All steps after [UserParkingRepository.saveNewParkingSession] are non-blocking:
 * - Enrichment is dispatched to [ParkingEnrichmentScheduler] (WorkManager on Android)
 *   and runs when network is available, with automatic retry.
 * - Geofence and notification fire immediately after the session is saved.
 */
@OptIn(ExperimentalUuidApi::class)
class ConfirmParkingUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
    private val zoneRepository: ZoneRepository,
    private val geofenceService: GeofenceManager,
    private val enrichmentScheduler: ParkingEnrichmentScheduler,
    private val authRepository: AuthRepository,
    private val config: ParkingDetectionConfig,
    private val departureEventBus: DepartureEventBus,
) {

    /**
     * Persists the parking spot, registers the geofence, schedules enrichment, and
     * resets [DepartureEventBus]. Pure data operation — the caller is responsible for
     * any user-facing notification (legacy `showParkingSaved` or REFACTOR-300's
     * unified `showParkingSavedConfirm` card with REVERT). This separation keeps the
     * use case single-purpose and lets each caller pick the right UX without a
     * boolean flag argument. [CONFIRM-NO-NOTIF-CLEANUP]
     */
    suspend operator fun invoke(
        location: GpsPoint,
        detectionReliability: Float,
        spotType: SpotType = SpotType.AUTO_DETECTED,
        sizeCategory: VehicleSize? = null,
        carbodyType: CarbodyType? = null,
        vehicleId: String? = null,
    ): Result<UserParking> {
        PaparcarLogger.d(
            DIAG,
            "▶ ConfirmParking.invoke reliability=$detectionReliability spotType=$spotType vehicleId=$vehicleId"
        )

        PaparcarLogger.d(DIAG, "  → authRepository.getCurrentSession() BEFORE")
        val userId = authRepository.getCurrentSession()?.userId
            ?: run {
                PaparcarLogger.d(
                    DIAG,
                    "  ✗ getCurrentSession returned null — abort NotAuthenticated"
                )
                return Result.failure(PaparcarError.Auth.NotAuthenticated)
            }
        PaparcarLogger.d(DIAG, "  ← getCurrentSession AFTER userId=$userId")

        // Vehicle resolution:
        //   - explicit [vehicleId] → caller already knows which vehicle owns the session
        //     (BT strategy resolves it from the disconnected device address). Lookup must
        //     succeed; failing-to-resolve is a precondition violation, not a fallback case.
        //   - null → Coordinator-strategy or manual path: fall back to the user's default
        //     vehicle (legacy single-vehicle behaviour). [AUTH-001] [VEHICLE-SYNC-001]
        val vehicle = if (vehicleId != null) {
            PaparcarLogger.d(DIAG, "  → getVehicleById(userId, $vehicleId) BEFORE")
            vehicleRepository.getVehicleById(userId, vehicleId).also {
                PaparcarLogger.d(DIAG, "  ← getVehicleById AFTER vehicleId=${it?.id}")
            }
        } else {
            PaparcarLogger.d(DIAG, "  → getActiveVehicle(userId) BEFORE")
            vehicleRepository.getActiveVehicle(userId).also {
                PaparcarLogger.d(DIAG, "  ← getActiveVehicle AFTER vehicleId=${it?.id}")
            }
        }
        if (vehicle == null) {
            PaparcarLogger.e(DIAG, "  ✗ vehicle not resolvable (explicit=$vehicleId) — abort")
            return Result.failure(PaparcarError.Parking.NoDefaultVehicle)
        }

        val resolvedSizeCategory = sizeCategory ?: vehicle.sizeCategory
        val resolvedCarbodyType = carbodyType ?: vehicle.carbodyType
        val resolvedVehicleId = vehicle.id

        // Check if the parking location falls inside one of the user's private zones.
        // If so, the session is stored locally but the community Spot is never published.
        val matchedPrivateZoneId = zoneRepository.getPrivateZonesSnapshot().firstOrNull { zone ->
            haversineMeters(location.latitude, location.longitude, zone.lat, zone.lon) <= zone.radiusMeters
        }?.id
        PaparcarLogger.d(DIAG, "  privateZoneId=$matchedPrivateZoneId")

        // Private zone → HOME_GEOFENCE: the user is parking in their own saved private spot.
        // Only applies to AUTO_DETECTED — manual reports and explicit callers keep their type.
        val resolvedSpotType = if (spotType == SpotType.AUTO_DETECTED) {
            if (matchedPrivateZoneId != null) {
                PaparcarLogger.d(DIAG, "  private zone match zoneId=$matchedPrivateZoneId → HOME_GEOFENCE")
                SpotType.HOME_GEOFENCE
            } else {
                SpotType.AUTO_DETECTED
            }
        } else {
            spotType
        }

        val sessionId = Uuid.random().toString()
        val gpsPoint = GpsPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            speed = location.speed,
        )
        if (location.accuracy > POOR_ACCURACY_WARN_METERS) {
            PaparcarLogger.w(DIAG, "  ⚠ poor GPS accuracy=${location.accuracy}m (threshold=${POOR_ACCURACY_WARN_METERS}m) — spot position may be imprecise, geofence will be padded")
        }
        val session = UserParking(
            id = sessionId,
            userId = userId,
            vehicleId = resolvedVehicleId,
            location = gpsPoint,
            geofenceId = sessionId,
            isActive = true,
            detectionReliability = detectionReliability,
            spotType = resolvedSpotType,
            sizeCategory = resolvedSizeCategory,
            carbodyType = resolvedCarbodyType,
            privateZoneId = matchedPrivateZoneId,
        )

        PaparcarLogger.d(DIAG, "  → saveNewParkingSession BEFORE sessionId=$sessionId")
        val saved = userParkingRepository.saveNewParkingSession(session)
        PaparcarLogger.d(DIAG, "  ← saveNewParkingSession AFTER isSuccess=${saved.isSuccess}")
        if (saved.isFailure) {
            PaparcarLogger.e(DIAG, "  ✗ saveNewParkingSession failed", saved.exceptionOrNull())
            return Result.failure(PaparcarError.Parking.SaveFailed)
        }

        // Clear the IN_VEHICLE_ENTER timestamp from the arrival trip so that departure
        // detection only triggers on a *new* IN_VEHICLE_ENTER that happens after parking
        // is saved. Without this reset, walking away from the car within the 30-min
        // vehicleEnterWindowMs would falsely confirm a departure. [BUG-WALK-DEPART-001]
        departureEventBus.reset()

        PaparcarLogger.d(DIAG, "  → enrichmentScheduler.schedule BEFORE")
        enrichmentScheduler.enqueueEnrichSession(sessionId, gpsPoint.latitude, gpsPoint.longitude)
        PaparcarLogger.d(DIAG, "  ← enrichmentScheduler.schedule AFTER")

        PaparcarLogger.d(DIAG, "  → geofenceService.createGeofence BEFORE")
        geofenceService.createGeofence(
            geofenceId = sessionId,
            latitude = gpsPoint.latitude,
            longitude = gpsPoint.longitude,
            radiusMeters = computeGeofenceRadius(resolvedSizeCategory, gpsPoint.accuracy),
        )
        PaparcarLogger.d(DIAG, "  ← geofenceService.createGeofence AFTER")

        PaparcarLogger.d(DIAG, "■ ConfirmParking.invoke SUCCESS (notif is caller's responsibility)")
        return Result.success(session)
    }

    private companion object {
        const val DIAG = "PARKDIAG/Confirm"
        const val POOR_ACCURACY_WARN_METERS = 50f
    }

    private fun computeGeofenceRadius(sizeCategory: VehicleSize?, accuracyMeters: Float): Float {
        val base = when (sizeCategory) {
            VehicleSize.MOTORCYCLE -> config.geofenceRadiusMotoMeters
            VehicleSize.LARGE_SEDAN -> config.geofenceRadiusLargeMeters
            VehicleSize.VAN_HIGH -> config.geofenceRadiusVanMeters
            else -> config.geofenceRadiusMeters  // MICRO_SMALL, MEDIUM_SUV, null
        }
        val padded = base + (accuracyMeters * config.geofenceAccuracyPadFactor)
        return padded.coerceAtMost(config.geofenceMaxRadiusMeters)
    }
}
