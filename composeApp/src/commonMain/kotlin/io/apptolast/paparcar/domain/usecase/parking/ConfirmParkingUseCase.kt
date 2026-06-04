@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.util.haversineMeters
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persists a confirmed parking spot, registers a geofence, notifies the user,
 * and schedules background enrichment with geocoder address + POI data.
 *
 * All steps after [UserParkingRepository.saveSession] are non-blocking:
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
    private val notificationPort: AppNotificationManager,
    private val enrichmentScheduler: ParkingEnrichmentScheduler,
    private val parkingSyncScheduler: ParkingSyncScheduler,
    private val authRepository: AuthRepository,
    private val config: ParkingDetectionConfig,
) {
    suspend operator fun invoke(
        location: GpsPoint,
        detectionReliability: Float,
        spotType: SpotType = SpotType.AUTO_DETECTED,
        sizeCategory: VehicleSize? = null,
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
        val resolvedVehicleId = vehicle.id

        // Check if the parking location falls inside one of the user's private zones.
        // If so, the session is stored locally but the community Spot is never published.
        val matchedPrivateZoneId = zoneRepository.getPrivateZonesSnapshot().firstOrNull { zone ->
            haversineMeters(location.latitude, location.longitude, zone.lat, zone.lon) <= zone.radiusMeters
        }?.id
        PaparcarLogger.d(DIAG, "  privateZoneId=$matchedPrivateZoneId")

        val sessionId = Uuid.random().toString()
        val gpsPoint = GpsPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            speed = location.speed,
        )
        val session = UserParking(
            id = sessionId,
            userId = userId,
            vehicleId = resolvedVehicleId,
            location = gpsPoint,
            geofenceId = sessionId,
            isActive = true,
            detectionReliability = detectionReliability,
            spotType = spotType,
            sizeCategory = resolvedSizeCategory,
            privateZoneId = matchedPrivateZoneId,
        )

        PaparcarLogger.d(DIAG, "  → saveSession BEFORE sessionId=$sessionId")
        val saved = userParkingRepository.saveSession(session)
        PaparcarLogger.d(DIAG, "  ← saveSession AFTER isSuccess=${saved.isSuccess}")
        if (saved.isFailure) {
            PaparcarLogger.e(DIAG, "  ✗ saveSession failed", saved.exceptionOrNull())
            return Result.failure(PaparcarError.Parking.SaveFailed)
        }
        val previousSessionId = saved.getOrNull()

        // Worker
        parkingSyncScheduler.schedule(session, previousSessionId)
        PaparcarLogger.d(
            DIAG,
            "  ↳ parkingSyncScheduler.schedule scheduled (previousSessionId=$previousSessionId)"
        )

        PaparcarLogger.d(DIAG, "  → enrichmentScheduler.schedule BEFORE")
        enrichmentScheduler.schedule(sessionId, gpsPoint.latitude, gpsPoint.longitude)
        PaparcarLogger.d(DIAG, "  ← enrichmentScheduler.schedule AFTER")

        PaparcarLogger.d(DIAG, "  → geofenceService.createGeofence BEFORE")
        geofenceService.createGeofence(
            geofenceId = sessionId,
            latitude = gpsPoint.latitude,
            longitude = gpsPoint.longitude,
            radiusMeters = computeGeofenceRadius(resolvedSizeCategory, gpsPoint.accuracy),
        )
        PaparcarLogger.d(DIAG, "  ← geofenceService.createGeofence AFTER")

        PaparcarLogger.d(DIAG, "  → notificationPort.showParkingSaved BEFORE")
        notificationPort.showParkingSaved(gpsPoint.latitude, gpsPoint.longitude)
        PaparcarLogger.d(DIAG, "  ← showParkingSaved AFTER")

        PaparcarLogger.d(DIAG, "■ ConfirmParking.invoke SUCCESS")
        return Result.success(session)
    }

    private companion object {
        const val DIAG = "PARKDIAG/Confirm"
    }

    private fun computeGeofenceRadius(sizeCategory: VehicleSize?, accuracyMeters: Float): Float {
        val base = when (sizeCategory) {
            VehicleSize.MOTO -> config.geofenceRadiusMotoMeters
            VehicleSize.LARGE -> config.geofenceRadiusLargeMeters
            VehicleSize.VAN -> config.geofenceRadiusVanMeters
            else -> config.geofenceRadiusMeters  // SMALL, MEDIUM, null
        }
        val padded = base + (accuracyMeters * config.geofenceAccuracyPadFactor)
        return padded.coerceAtMost(config.geofenceMaxRadiusMeters)
    }
}
