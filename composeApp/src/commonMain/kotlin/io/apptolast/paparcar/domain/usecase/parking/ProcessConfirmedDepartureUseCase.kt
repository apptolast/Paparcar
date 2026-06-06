@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Executes all side-effects of a confirmed car departure:
 *
 * 1. Resolves the active [UserParking] session for the given geofence.
 * 2. Reports the freed spot to the community (or sends a private-zone notification).
 * 3. Clears the session from the local store.
 * 4. Resets [DepartureEventBus] so the next trip starts clean.
 * 5. Removes the geofence from Play Services.
 *
 * Returns [Result.failure] if the session clear fails — the caller (WorkManager)
 * should retry so the session is never left open after a confirmed departure.
 *
 * Report is scheduled BEFORE the clear: the WorkManager job is durably enqueued
 * even if the process is killed mid-flight, and REPLACE policy prevents duplicates
 * on retry.
 */
class ProcessConfirmedDepartureUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val zoneRepository: ZoneRepository,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val geofenceService: GeofenceManager,
    private val departureEventBus: DepartureEventBus,
    private val notificationPort: AppNotificationManager,
) {
    private companion object {
        const val TAG = "ProcessConfirmedDeparture"
    }

    suspend operator fun invoke(geofenceId: String): Result<Unit> {
        val session = userParkingRepository.getActiveSessionByGeofence(geofenceId)
        val spotId = session?.id ?: "auto_${Clock.System.now().toEpochMilliseconds()}"
        val lat = session?.location?.latitude
        val lon = session?.location?.longitude

        if (lat != null && lon != null) {
            if (session.privateZoneId == null) {
                reportSpotReleased(
                    lat = lat,
                    lon = lon,
                    spotId = spotId,
                    spotType = session.spotType,
                    confidence = session.detectionReliability ?: 1f,
                    sizeCategory = session.sizeCategory,
                )
            } else {
                val zoneName = runCatching { zoneRepository.getPrivateZonesSnapshot() }
                    .getOrElse { emptyList() }
                    .firstOrNull { it.id == session.privateZoneId }?.name
                    ?: session.privateZoneId
                notificationPort.showHomeParkingLeft(zoneName, lat, lon)
            }
        }

        if (session != null) {
            userParkingRepository.clearActiveParkingSession(session.id)
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "clearActiveParkingSession failed for session=${session.id}", e)
                    return Result.failure(e)
                }
        }

        departureEventBus.reset()
        geofenceService.removeGeofence(geofenceId)
        return Result.success(Unit)
    }
}
