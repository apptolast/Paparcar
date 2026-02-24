package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.data.notification.AppNotificationManager
import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingSession
import io.apptolast.paparcar.domain.model.ParkingSignals
import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.domain.service.GeofenceService
import io.apptolast.paparcar.domain.usecase.notification.ShowDebugNotificationUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.takeWhile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class DetectAndReportParkingUseCase(
    private val calculateParkingConfidence: CalculateParkingConfidenceUseCase,
    private val saveUserParking: SaveUserParkingUseCase,
    private val geofenceService: GeofenceService,
    private val showDebugNotification: ShowDebugNotificationUseCase,
    private val notificationManager: AppNotificationManager,
) {
    private var stoppedSince: Long? = null
    private val vehicleExitConfirmed = MutableStateFlow(false)
    private val activityStillDetected = MutableStateFlow(false)
    private val userConfirmedParking = MutableStateFlow(false)
    private var mediumNotificationShown = false

    suspend operator fun invoke(locations: Flow<SpotLocation>) {
        reset()
        var completed = false

        locations.takeWhile { !completed }.collectLatest { location ->
            val now = Clock.System.now().toEpochMilliseconds()

            val stoppedDuration = if (location.speed < 1f) {
                if (stoppedSince == null) stoppedSince = now
                now - (stoppedSince ?: 0L)
            } else {
                stoppedSince = null
                mediumNotificationShown = false
                0L
            }

            // User confirmed via notification action button
            if (userConfirmedParking.value) {
                confirmParking(location)
                completed = true
                return@collectLatest
            }

            val signals = ParkingSignals(
                speed = location.speed,
                stoppedDurationMs = stoppedDuration,
                gpsAccuracy = location.accuracy,
                activityExit = vehicleExitConfirmed.value,
                activityStill = activityStillDetected.value,
            )

            when (val confidence = calculateParkingConfidence(signals)) {
                is ParkingConfidence.NotYet, is ParkingConfidence.Low -> Unit
                is ParkingConfidence.Medium -> {
                    if (!mediumNotificationShown) {
                        mediumNotificationShown = true
                        notificationManager.showParkingConfirmationNotification(confidence.score)
                    }
                }
                is ParkingConfidence.High -> {
                    confirmParking(location)
                    completed = true
                }
            }
        }
    }

    private suspend fun confirmParking(location: SpotLocation) {
        val sessionId = Uuid.random().toString()
        val session = ParkingSession(
            id = sessionId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            geofenceId = sessionId,
            isActive = true,
        )
        saveUserParking(session)
        geofenceService.createGeofence(sessionId, location.latitude, location.longitude, 80f)
        showDebugNotification("Plaza guardada (${location.latitude}, ${location.longitude})")
    }

    private fun reset() {
        stoppedSince = null
        vehicleExitConfirmed.value = false
        activityStillDetected.value = false
        userConfirmedParking.value = false
        mediumNotificationShown = false
    }

    fun onVehicleExit() {
        vehicleExitConfirmed.value = true
    }

    fun onStillDetected() {
        activityStillDetected.value = true
    }

    fun onUserConfirmedParking() {
        userConfirmedParking.value = true
    }

    fun onUserDeniedParking() {
        vehicleExitConfirmed.value = false
        activityStillDetected.value = false
        stoppedSince = null
        mediumNotificationShown = false
        userConfirmedParking.value = false
    }
}
