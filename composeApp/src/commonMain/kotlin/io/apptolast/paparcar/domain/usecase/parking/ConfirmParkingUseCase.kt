package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.service.GeofenceService
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingSpotSavedUseCase
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persists a confirmed parking spot, registers a geofence around it, and notifies the user.
 *
 * Extracted from [DetectAndReportParkingUseCase] so that the "what happens when parking
 * is confirmed" logic has a single reason to change, independent of the detection loop.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class ConfirmParkingUseCase(
    private val saveUserParking: SaveUserParkingUseCase,
    private val geofenceService: GeofenceService,
    private val notifyParkingSpotSaved: NotifyParkingSpotSavedUseCase,
    private val config: ParkingDetectionConfig,
) {
    suspend operator fun invoke(location: GpsPoint) {
        val sessionId = Uuid.random().toString()
        val session = UserParking(
            id = sessionId,
            location = GpsPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                speed = location.speed,
            ),
            geofenceId = sessionId,
            isActive = true,
        )
        saveUserParking(session)
        geofenceService.createGeofence(
            geofenceId = sessionId,
            latitude = location.latitude,
            longitude = location.longitude,
            radiusMeters = config.geofenceRadiusMeters,
        )
        notifyParkingSpotSaved(location.latitude, location.longitude)
    }
}
