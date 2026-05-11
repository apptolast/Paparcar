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
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first

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
    private val geofenceService: GeofenceManager,
    private val notificationPort: AppNotificationManager,
    private val enrichmentScheduler: ParkingEnrichmentScheduler,
    private val authRepository: AuthRepository,
    private val config: ParkingDetectionConfig,
) {
    suspend operator fun invoke(
        location: GpsPoint,
        detectionReliability: Float,
        spotType: SpotType = SpotType.AUTO_DETECTED,
        sizeCategory: VehicleSize? = null,
    ): Result<UserParking> {
        PaparcarLogger.d(DIAG, "▶ ConfirmParking.invoke reliability=$detectionReliability spotType=$spotType")

        PaparcarLogger.d(DIAG, "  → authRepository.getCurrentSession() BEFORE")
        val userId = authRepository.getCurrentSession()?.userId
            ?: run {
                PaparcarLogger.d(DIAG, "  ✗ getCurrentSession returned null — abort NotAuthenticated")
                return Result.failure(PaparcarError.Auth.NotAuthenticated)
            }
        PaparcarLogger.d(DIAG, "  ← getCurrentSession AFTER userId=$userId")

        PaparcarLogger.d(DIAG, "  → observeDefaultVehicle().first() BEFORE")
        val defaultVehicle = vehicleRepository.observeDefaultVehicle().first()
        PaparcarLogger.d(DIAG, "  ← observeDefaultVehicle AFTER vehicleId=${defaultVehicle?.id}")

        val resolvedSizeCategory = sizeCategory ?: defaultVehicle?.sizeCategory
        val resolvedVehicleId = defaultVehicle?.id
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
        )

        PaparcarLogger.d(DIAG, "  → saveSession BEFORE sessionId=$sessionId")
        val saved = userParkingRepository.saveSession(session)
        PaparcarLogger.d(DIAG, "  ← saveSession AFTER isSuccess=${saved.isSuccess}")
        if (saved.isFailure) {
            PaparcarLogger.e(DIAG, "  ✗ saveSession failed", saved.exceptionOrNull())
            return Result.failure(PaparcarError.Parking.SaveFailed)
        }

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

        PaparcarLogger.d(DIAG, "  → notificationPort.showParkingSpotSaved BEFORE")
        notificationPort.showParkingSpotSaved(gpsPoint.latitude, gpsPoint.longitude)
        PaparcarLogger.d(DIAG, "  ← showParkingSpotSaved AFTER")

        PaparcarLogger.d(DIAG, "■ ConfirmParking.invoke SUCCESS")
        return Result.success(session)
    }

    private companion object {
        const val DIAG = "PARKDIAG/Confirm"
    }

    private fun computeGeofenceRadius(sizeCategory: VehicleSize?, accuracyMeters: Float): Float {
        val base = when (sizeCategory) {
            VehicleSize.MOTO  -> config.geofenceRadiusMotoMeters
            VehicleSize.LARGE -> config.geofenceRadiusLargeMeters
            VehicleSize.VAN   -> config.geofenceRadiusVanMeters
            else              -> config.geofenceRadiusMeters  // SMALL, MEDIUM, null
        }
        val padded = base + (accuracyMeters * config.geofenceAccuracyPadFactor)
        return padded.coerceAtMost(config.geofenceMaxRadiusMeters)
    }
}
