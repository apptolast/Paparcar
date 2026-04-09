@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
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
    ) {
        val userId = authRepository.getCurrentSession()?.userId ?: ""
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
            location = gpsPoint,
            geofenceId = sessionId,
            isActive = true,
            detectionReliability = detectionReliability,
            spotType = spotType,
            sizeCategory = sizeCategory,
        )

        userParkingRepository.saveSession(session).onFailure { return }

        enrichmentScheduler.schedule(sessionId, gpsPoint.latitude, gpsPoint.longitude)

        geofenceService.createGeofence(
            geofenceId = sessionId,
            latitude = gpsPoint.latitude,
            longitude = gpsPoint.longitude,
            radiusMeters = config.geofenceRadiusMeters,
        )
        notificationPort.showParkingSpotSaved(gpsPoint.latitude, gpsPoint.longitude)
    }
}
