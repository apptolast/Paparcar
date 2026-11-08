package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.DepartureConfirmationListener
import com.rndeveloper.paparcar.domain.detection.StillParkedQuestion
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeSpotRepository
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfirmStillParkedUseCaseTest {
    private val question = StillParkedQuestion("saved-fence", 40.0, -3.0, 1_000L)
    private val parking = UserParking(
        id = "saved", vehicleId = "second-car",
        location = GpsPoint(40.0, -3.0, 8f, 500L, 0f),
        geofenceId = question.geofenceId,
    )
    private val preferences = FakeAppPreferences().apply { setStillParkedQuestion(question) }
    private val repository = FakeUserParkingRepository(initialSession = parking)
    private val spots = FakeSpotRepository()
    private val notifications = FakeAppNotificationManager()
    private val dismissedDepartures = mutableListOf<String>()
    private val listener = object : DepartureConfirmationListener {
        override fun notifyDepartureConfirmed() = error("Ratifying a parking cannot confirm departure")
        override fun notifyDepartureDismissed(geofenceId: String) { dismissedDepartures += geofenceId }
    }

    private fun useCase(
        parkingRepository: UserParkingRepository = repository,
        spotRepository: SpotRepository = spots,
    ) = ConfirmStillParkedUseCase(
        preferences, parkingRepository, spotRepository, listener, notifications, nowMs = { 2_000L },
    )

    @Test
    fun should_preserve_the_pin_session_and_birth_time_when_the_saved_car_is_still_here() = runTest {
        assertTrue(useCase()(question).getOrThrow())

        assertEquals(parking, repository.getSessionById(parking.id))
        assertEquals(question.copy(confirmedHereAtMs = 2_000L), preferences.stillParkedQuestion())
        assertEquals(listOf(question.geofenceId), dismissedDepartures)
        assertEquals(listOf(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID), notifications.dismissedIds)
        assertEquals(0, repository.saveNewParkingSessionCallCount)
        assertEquals(0, repository.clearActiveParkingSessionCallCount)
        assertEquals(0, spots.reportCallCount)
    }

    @Test
    fun should_ignore_the_old_epoch_when_a_new_question_reuses_the_fence() = runTest {
        val newer = question.copy(shownAtMs = 1_500L)
        preferences.setStillParkedQuestion(newer)

        assertFalse(useCase()(question).getOrThrow())

        assertEquals(newer, preferences.stillParkedQuestion())
        assertEquals(parking, repository.getSessionById(parking.id))
        assertTrue(dismissedDepartures.isEmpty())
        assertTrue(notifications.dismissedIds.isEmpty())
    }

    @Test
    fun should_ignore_another_cars_question_when_the_pending_fence_differs() = runTest {
        val other = question.copy(geofenceId = "another-car")
        preferences.setStillParkedQuestion(other)

        assertFalse(useCase()(question).getOrThrow())

        assertEquals(other, preferences.stillParkedQuestion())
        assertEquals(parking, repository.getSessionById(parking.id))
        assertTrue(notifications.dismissedIds.isEmpty())
    }

    @Test
    fun should_acknowledge_only_once_when_yes_is_delivered_twice() = runTest {
        val confirm = useCase()
        assertTrue(confirm(question).getOrThrow())
        assertFalse(confirm(question).getOrThrow())

        assertEquals(1, dismissedDepartures.size)
        assertEquals(1, notifications.dismissedIds.size)
        assertEquals(question.copy(confirmedHereAtMs = 2_000L), preferences.stillParkedQuestion())
    }

    @Test
    fun should_retire_only_the_obsolete_question_when_its_session_was_replaced() = runTest {
        val replacement = parking.copy(id = "new", geofenceId = "new-fence")
        val repo = FakeUserParkingRepository(initialSession = replacement)

        assertFalse(useCase(parkingRepository = repo)(question).getOrThrow())

        assertNull(preferences.stillParkedQuestion())
        assertEquals(replacement, repo.getSessionById(replacement.id))
        assertEquals(0, repo.clearActiveParkingSessionCallCount)
        assertTrue(dismissedDepartures.isEmpty())
    }

    @Test
    fun should_preserve_the_new_question_when_it_arrives_during_the_session_lookup() = runTest {
        val newer = question.copy(shownAtMs = 1_500L)
        val repo = object : UserParkingRepository by repository {
            override suspend fun getActiveSessionByGeofence(geofenceId: String): UserParking? {
                preferences.setStillParkedQuestion(newer)
                return null
            }
        }

        assertFalse(useCase(parkingRepository = repo)(question).getOrThrow())

        assertEquals(newer, preferences.stillParkedQuestion())
        assertTrue(notifications.dismissedIds.isEmpty())
    }

    @Test
    fun should_withdraw_the_provisional_spot_before_ratifying_the_saved_car() = runTest {
        val provisional = parking.copy(provisionalDepartureAtMs = 800L)
        val repo = FakeUserParkingRepository(initialSession = provisional)

        assertTrue(useCase(parkingRepository = repo)(question).getOrThrow())

        assertEquals(listOf(parking.id), spots.retractedSpotIds)
        assertEquals(parking, repo.getSessionById(parking.id))
        assertEquals(2_000L, preferences.stillParkedQuestion()?.confirmedHereAtMs)
        assertEquals(0, spots.reportCallCount)
    }

    @Test
    fun should_keep_the_question_retryable_when_the_provisional_spot_cannot_be_withdrawn() = runTest {
        val provisional = parking.copy(provisionalDepartureAtMs = 800L)
        val repo = FakeUserParkingRepository(initialSession = provisional)
        spots.retractResult = Result.failure(IllegalStateException("offline"))

        assertTrue(useCase(parkingRepository = repo)(question).isFailure)

        assertEquals(question, preferences.stillParkedQuestion())
        assertEquals(provisional, repo.getSessionById(parking.id))
        assertTrue(dismissedDepartures.isEmpty())
        assertTrue(notifications.dismissedIds.isEmpty())
    }

    @Test
    fun should_preserve_the_new_question_and_provisional_marker_after_a_suspended_old_answer() = runTest {
        val provisional = parking.copy(provisionalDepartureAtMs = 800L)
        val repo = FakeUserParkingRepository(initialSession = provisional)
        val newer = question.copy(shownAtMs = 1_500L)
        val delayedSpots = object : SpotRepository by spots {
            override suspend fun retractSpot(spotId: String): Result<Unit> {
                preferences.setStillParkedQuestion(newer)
                return spots.retractSpot(spotId)
            }
        }

        assertFalse(useCase(repo, delayedSpots)(question).getOrThrow())

        assertEquals(newer, preferences.stillParkedQuestion())
        assertEquals(provisional, repo.getSessionById(parking.id))
        assertTrue(dismissedDepartures.isEmpty())
        assertTrue(notifications.dismissedIds.isEmpty())
    }

    @Test
    fun should_propagate_cancellation_without_answering_the_question() = runTest {
        val cancelledRepo = object : UserParkingRepository by repository {
            override suspend fun getActiveSessionByGeofence(geofenceId: String): UserParking? =
                throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> { useCase(parkingRepository = cancelledRepo)(question) }

        assertEquals(question, preferences.stillParkedQuestion())
        assertTrue(notifications.dismissedIds.isEmpty())
    }
}
