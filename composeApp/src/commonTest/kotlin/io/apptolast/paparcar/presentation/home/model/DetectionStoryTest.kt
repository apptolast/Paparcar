package io.apptolast.paparcar.presentation.home.model

import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.presentation.home.DrivingMeta
import io.apptolast.paparcar.presentation.home.VehicleCard
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Precedence tests for [resolveDetectionStory] — the single detection story Home tells.
 * [UX-DETECTION-STORY-001]
 */
class DetectionStoryTest {

    private fun vehicle(id: String, isActive: Boolean = false, brand: String = "Skoda", model: String = "Kamiq") =
        Vehicle(
            id = id,
            userId = "user-1",
            brand = brand,
            model = model,
            sizeCategory = VehicleSize.MEDIUM_SUV,
            isActive = isActive,
        )

    private fun session(vehicleId: String) = UserParking(
        id = "s-$vehicleId",
        userId = "user-1",
        vehicleId = vehicleId,
        location = GpsPoint(latitude = 40.0, longitude = -3.0, accuracy = 8f, timestamp = 0L, speed = 0f),
        geofenceId = "gf-1",
        isActive = true,
    )

    private val activeCard = VehicleCard(vehicle("v-active", isActive = true), session = null)
    private val activeParkedCard = VehicleCard(vehicle("v-active", isActive = true), session = session("v-active"))
    private val otherCard = VehicleCard(vehicle("v-other", brand = "Seat", model = "Ibiza"), session = null)

    @Test
    fun should_passThrough_the_four_action_states() {
        val cards = listOf(activeCard)
        assertEquals(DetectionStory.BlockedCore, resolveDetectionStory(DetectionUiState.BlockedCore, null, cards))
        assertEquals(DetectionStory.NoVehicle, resolveDetectionStory(DetectionUiState.NoVehicle, null, cards))
        assertEquals(DetectionStory.Inactive, resolveDetectionStory(DetectionUiState.Inactive, null, cards))
        assertEquals(
            DetectionStory.AwaitingFirstPark,
            resolveDetectionStory(DetectionUiState.AwaitingFirstPark, null, cards),
        )
    }

    @Test
    fun should_tell_driving_with_the_trips_own_vehicle_name() {
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Monitoring,
            drivingMeta = DrivingMeta(vehicleId = "v-other", phase = DetectionPhase.Driving),
            vehicleCards = listOf(activeCard, otherCard),
        )
        assertEquals(DetectionStory.Driving(vehicleName = "Seat Ibiza", isCandidate = false), story)
    }

    @Test
    fun should_flag_candidate_when_the_trip_is_confirming_a_spot() {
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Monitoring,
            drivingMeta = DrivingMeta(vehicleId = "v-active", phase = DetectionPhase.Candidate),
            vehicleCards = listOf(activeCard),
        )
        assertEquals(DetectionStory.Driving(vehicleName = "Skoda Kamiq", isCandidate = true), story)
    }

    @Test
    fun should_fall_back_to_the_active_vehicle_when_the_trip_has_no_resolved_vehicle() {
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Monitoring,
            drivingMeta = DrivingMeta(vehicleId = null, phase = DetectionPhase.Driving),
            vehicleCards = listOf(otherCard, activeCard),
        )
        assertEquals(DetectionStory.Driving(vehicleName = "Skoda Kamiq", isCandidate = false), story)
    }

    @Test
    fun should_watch_the_active_vehicle_when_parked() {
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Parked,
            drivingMeta = null,
            vehicleCards = listOf(otherCard, activeParkedCard),
        )
        assertEquals(
            DetectionStory.Watching(vehicleName = "Skoda Kamiq", isParked = true, viaBluetooth = false),
            story,
        )
    }

    @Test
    fun should_watch_via_bluetooth_when_bt_armed() {
        val story = resolveDetectionStory(
            uiState = DetectionUiState.ArmedBluetooth,
            drivingMeta = null,
            vehicleCards = listOf(activeCard, otherCard),
        )
        assertEquals(
            DetectionStory.Watching(vehicleName = "Skoda Kamiq", isParked = false, viaBluetooth = true),
            story,
        )
    }

    @Test
    fun should_hide_watching_when_no_vehicle_is_active() {
        // Watching names the ACTIVE vehicle ONLY — no ranked/first fallback (user decision:
        // it is the one the Coordinator works for). Without an active car, better silence
        // than a lie. [UX-DETECTION-STORY-001]
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Parked,
            drivingMeta = null,
            vehicleCards = listOf(otherCard),
        )
        assertEquals(DetectionStory.Hidden, story)
    }

    @Test
    fun should_hide_when_silent() {
        assertEquals(
            DetectionStory.Hidden,
            resolveDetectionStory(DetectionUiState.Silent, null, listOf(activeCard)),
        )
    }
}
