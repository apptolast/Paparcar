package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.detection.StillParkedQuestion
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.presentation.home.VehicleCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StillParkedStoryTest {
    private val question = StillParkedQuestion("second-fence", 40.0, -3.0, 1_000L)
    private val car = Vehicle(
        id = "second-car", userId = "user", brand = "Skoda", model = "Kamiq",
        sizeCategory = VehicleSize.MEDIUM_SUV,
    )
    private val saved = UserParking(
        id = "saved", vehicleId = car.id, location = GpsPoint(40.0, -3.0, 8f, 500L, 0f),
        geofenceId = question.geofenceId,
    )
    private val card = VehicleCard(car, saved)

    @Test
    fun should_ask_about_the_saved_second_car_instead_of_the_active_first_car() {
        val first = VehicleCard(car.copy(id = "first-car", isActive = true), saved.copy(id = "first", geofenceId = "first-fence"))
        val story = assertIs<DetectionStory.ReconfirmingParking>(resolveDetectionStory(
            DetectionUiState.Parked, listOf(first, card), stillParkedQuestion = question,
        ))

        assertEquals(question, story.question)
        assertEquals(saved, story.parking)
        assertEquals("Skoda Kamiq", story.vehicleName)
        assertEquals(car.id, story.subjectVehicleId(parkNudgeVehicleId = "first-car", activeVehicleId = "first-car"))
        assertTrue(story.isTheCarsQuestion())
    }

    @Test
    fun should_keep_arrival_and_permanence_as_distinct_questions() {
        val arrival = PendingPromptWindow(shownAtMs = 2_000L, vehicleId = car.id)
        assertEquals(DetectionStory.AwaitingAnswer(arrival), resolveDetectionStory(
            DetectionUiState.Parked, listOf(card), promptWindow = arrival,
        ))
        assertIs<DetectionStory.ReconfirmingParking>(resolveDetectionStory(
            DetectionUiState.Parked, listOf(card), stillParkedQuestion = question,
        ))
    }

    @Test
    fun should_give_the_expiring_arrival_question_precedence_when_both_are_pending() {
        val arrival = PendingPromptWindow(shownAtMs = 2_000L, vehicleId = car.id)
        assertEquals(DetectionStory.AwaitingAnswer(arrival), resolveDetectionStory(
            DetectionUiState.Parked, listOf(card), promptWindow = arrival, stillParkedQuestion = question,
        ))
    }

    @Test
    fun should_allow_ratifying_a_saved_pin_without_a_current_phone_location_permission() {
        assertIs<DetectionStory.ReconfirmingParking>(resolveDetectionStory(
            DetectionUiState.BlockedCore, listOf(card), stillParkedQuestion = question,
        ))
    }

    @Test
    fun should_preserve_a_real_question_even_if_the_car_has_a_paired_bluetooth_device() {
        // Pairing is not a live connection. The engine owns nomination; the UI displays its doubt.
        val paired = card.copy(vehicle = car.copy(bluetoothDeviceId = "fixture-device"))
        assertIs<DetectionStory.ReconfirmingParking>(resolveDetectionStory(
            DetectionUiState.Parked, listOf(paired), stillParkedQuestion = question,
        ))
    }

    @Test
    fun should_stop_asking_after_yes_without_erasing_the_saved_parking() {
        val story = resolveDetectionStory(
            DetectionUiState.Parked, listOf(card), stillParkedQuestion = question.copy(confirmedHereAtMs = 2_000L),
        )
        assertFalse(story is DetectionStory.ReconfirmingParking)
        assertEquals(saved, card.session)
    }

    @Test
    fun should_never_attach_an_obsolete_question_to_a_different_or_closed_parking() {
        val another = card.copy(session = saved.copy(geofenceId = "new-fence"))
        val closed = card.copy(session = saved.copy(isActive = false))
        for (cards in listOf(emptyList(), listOf(another), listOf(closed))) {
            assertFalse(resolveDetectionStory(
                DetectionUiState.Parked, cards, stillParkedQuestion = question,
            ) is DetectionStory.ReconfirmingParking)
        }
    }

    @Test
    fun should_keep_the_question_when_system_health_notices_are_silenced() {
        val story = resolveDetectionStory(DetectionUiState.Parked, listOf(card), stillParkedQuestion = question)
        assertNull(story.notice())
        assertEquals(story, story.silencedBy(DetectionNotice.entries.toSet()))
    }
}
