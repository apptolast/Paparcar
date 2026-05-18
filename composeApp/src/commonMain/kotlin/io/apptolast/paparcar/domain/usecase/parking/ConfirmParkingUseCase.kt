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
    ): Result<UserParking> {
        PaparcarLogger.d(
            DIAG,
            "▶ ConfirmParking.invoke reliability=$detectionReliability spotType=$spotType"
        )

        PaparcarLogger.d(DIAG, "  → authRepository.getCurrentSession() BEFORE")
        // Obtenemos el id desde firebase (solo con internet)
        val userId = authRepository.getCurrentSession()?.userId
            ?: run {
                PaparcarLogger.d(
                    DIAG,
                    "  ✗ getCurrentSession returned null — abort NotAuthenticated"
                )
                return Result.failure(PaparcarError.Auth.NotAuthenticated)
            }
        PaparcarLogger.d(DIAG, "  ← getCurrentSession AFTER userId=$userId")

        PaparcarLogger.d(DIAG, "  → getDefaultVehicle(userId) BEFORE")
        // Suspending one-shot read — bypasses the auth-flow race that made the previous
        // observeDefaultVehicle().first() return null even with a valid session. Includes
        // a fallback through user_profile.defaultVehicleId. [AUTH-001]
        // Room is the only source of truth here — bootstrap (splash) syncs all user data
        // from Firestore before the app reaches Home. If Room is empty here it means either
        // the user is logged out (cache cleared on logout) or bootstrap sync failed.
        // Either way, a network call from the detection path is wrong — abort. [VEHICLE-SYNC-001]
        val defaultVehicle = vehicleRepository.getDefaultVehicle(userId)
        PaparcarLogger.d(DIAG, "  ← getDefaultVehicle AFTER vehicleId=${defaultVehicle?.id}")
        if (defaultVehicle == null) {
            PaparcarLogger.e(DIAG, "  ✗ no default vehicle in Room — bootstrap sync missing or user logged out — abort")
            return Result.failure(PaparcarError.Parking.NoDefaultVehicle)
        }

        val resolvedSizeCategory = sizeCategory ?: defaultVehicle.sizeCategory
        val resolvedVehicleId = defaultVehicle.id
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
        //Esto guarda en local en Room, no necesita un worker
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
            VehicleSize.MOTO -> config.geofenceRadiusMotoMeters
            VehicleSize.LARGE -> config.geofenceRadiusLargeMeters
            VehicleSize.VAN -> config.geofenceRadiusVanMeters
            else -> config.geofenceRadiusMeters  // SMALL, MEDIUM, null
        }
        val padded = base + (accuracyMeters * config.geofenceAccuracyPadFactor)
        return padded.coerceAtMost(config.geofenceMaxRadiusMeters)
    }
}
