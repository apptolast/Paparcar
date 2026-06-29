package io.apptolast.paparcar.presentation.home.model

import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.DisabledReason
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.permissions.RequiredPermission
import kotlin.test.Test
import kotlin.test.assertEquals

class DetectionUiStateTest {

    private val session = UserParking(
        id = "s-1",
        userId = "u-1",
        vehicleId = "v-1",
        location = GpsPoint(latitude = 40.0, longitude = -3.0, accuracy = 8f, timestamp = 0L, speed = 0f),
        geofenceId = "gf-1",
        isActive = true,
    )

    @Test
    fun should_mapNoVehicle_when_disabledNoVehicle() {
        assertEquals(
            DetectionUiState.NoVehicle,
            DetectionReadiness.Disabled(DisabledReason.NO_VEHICLE).toUiState(),
        )
    }

    @Test
    fun should_mapSilent_when_disabledNonParkingVehicle() {
        assertEquals(
            DetectionUiState.Silent,
            DetectionReadiness.Disabled(DisabledReason.NON_PARKING_VEHICLE).toUiState(),
        )
    }

    @Test
    fun should_mapBlockedCore_when_missingCorePermission() {
        assertEquals(
            DetectionUiState.BlockedCore,
            DetectionReadiness.Blocked(setOf(RequiredPermission.FOREGROUND_LOCATION)).toUiState(),
        )
    }

    @Test
    fun should_mapInactive_when_onlyProducerMissing() {
        // Producer-only missing folds into the unified "activate detection" surface. [DET-TOGGLE-001]
        assertEquals(
            DetectionUiState.Inactive,
            DetectionReadiness.Blocked(
                setOf(RequiredPermission.BACKGROUND_LOCATION, RequiredPermission.ACTIVITY_RECOGNITION),
            ).toUiState(),
        )
    }

    @Test
    fun should_mapInactive_when_turnedOff() {
        // Auto-detection off in Settings → same "activate detection" surface as producer-missing.
        assertEquals(
            DetectionUiState.Inactive,
            DetectionReadiness.Disabled(DisabledReason.TURNED_OFF).toUiState(),
        )
    }

    @Test
    fun should_preferBlockedCore_when_bothTiersMissing() {
        // CORE is the more severe failure and must win the surface.
        assertEquals(
            DetectionUiState.BlockedCore,
            DetectionReadiness.Blocked(
                setOf(RequiredPermission.FOREGROUND_LOCATION, RequiredPermission.BACKGROUND_LOCATION),
            ).toUiState(),
        )
    }

    @Test
    fun should_mapParked_when_parked() {
        assertEquals(
            DetectionUiState.Parked,
            DetectionReadiness.Parked(session).toUiState(),
        )
    }

    @Test
    fun should_mapMonitoring_when_monitoring() {
        assertEquals(
            DetectionUiState.Monitoring,
            DetectionReadiness.Monitoring(ParkingStrategy.COORDINATOR).toUiState(),
        )
    }

    @Test
    fun should_mapAwaitingFirstPark_when_readyCoordinator() {
        assertEquals(
            DetectionUiState.AwaitingFirstPark,
            DetectionReadiness.Ready(ParkingStrategy.COORDINATOR).toUiState(),
        )
    }

    @Test
    fun should_mapSilent_when_readyBluetooth() {
        // Bluetooth detection is fully automatic — no cold-start prompt needed.
        assertEquals(
            DetectionUiState.Silent,
            DetectionReadiness.Ready(ParkingStrategy.BLUETOOTH).toUiState(),
        )
    }
}
