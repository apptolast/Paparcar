package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeManualParkingDetection
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorrectDetectedParkingUseCaseTest {
    private val parking = UserParking(
        id = "park", userId = "user", vehicleId = "car",
        location = GpsPoint(40.4, -3.7, 8f, 1_000L, 0f),
        isActive = true,
    )

    private fun useCase(repo: FakeUserParkingRepository, detection: FakeManualParkingDetection) =
        CorrectDetectedParkingUseCase(
            repo, RevertParkingUseCase(repo, FakeGeofenceManager(), FakeAppNotificationManager()), detection,
        )

    @Test
    fun continue_retracts_without_publishing_and_resumes_the_same_car() = runTest {
        val repo = FakeUserParkingRepository(initialSession = parking)
        val detection = FakeManualParkingDetection()

        assertTrue(useCase(repo, detection)("park", ParkingCorrection.CONTINUE_TRIP).isSuccess)

        assertNull(repo.getActiveSession())
        assertTrue(repo.getSessionById("park")!!.isRetracted)
        assertEquals(false, repo.getSessionById("park")!!.publishedSpot)
        assertEquals(listOf("car"), detection.resumedVehicles)
        assertEquals(1, detection.stopCallCount)
        assertEquals(0, detection.stopByUserCallCount)
    }

    @Test
    fun wrong_car_rejects_the_trip_without_restarting_detection() = runTest {
        val repo = FakeUserParkingRepository(initialSession = parking)
        val detection = FakeManualParkingDetection()

        assertTrue(useCase(repo, detection)("park", ParkingCorrection.WRONG_CAR).isSuccess)

        assertNull(repo.getActiveSession())
        assertTrue(repo.getSessionById("park")!!.isRetracted)
        assertEquals(false, repo.getSessionById("park")!!.publishedSpot)
        assertEquals(1, detection.stopByUserCallCount)
        assertTrue(detection.resumedVehicles.isEmpty())
    }

    @Test
    fun failed_stop_leaves_the_parking_untouched() = runTest {
        val repo = FakeUserParkingRepository(initialSession = parking)
        val detection = FakeManualParkingDetection().apply {
            finishTripResult = Result.failure(IllegalStateException("timeout"))
        }

        assertTrue(useCase(repo, detection)("park", ParkingCorrection.CONTINUE_TRIP).isFailure)
        assertFalse(repo.getSessionById("park")!!.isRetracted)
        assertTrue(detection.resumedVehicles.isEmpty())
    }

    @Test
    fun failed_close_does_not_resume_and_can_be_retried() = runTest {
        val repo = FakeUserParkingRepository(initialSession = parking).apply {
            clearActiveParkingSessionResult = Result.failure(IllegalStateException("database"))
        }
        val detection = FakeManualParkingDetection()
        val correct = useCase(repo, detection)

        assertTrue(correct("park", ParkingCorrection.CONTINUE_TRIP).isFailure)
        assertTrue(detection.resumedVehicles.isEmpty())

        repo.clearActiveParkingSessionResult = Result.success(Unit)
        assertTrue(correct("park", ParkingCorrection.CONTINUE_TRIP).isSuccess)
        assertEquals(listOf("car"), detection.resumedVehicles)
    }

    @Test
    fun stale_or_repeated_tap_never_stops_a_new_trip() = runTest {
        val repo = FakeUserParkingRepository(initialSession = parking)
        val detection = FakeManualParkingDetection()
        val correct = useCase(repo, detection)
        correct("park", ParkingCorrection.CONTINUE_TRIP)

        assertTrue(correct("park", ParkingCorrection.WRONG_CAR).isFailure)
        assertEquals(1, detection.stopCallCount)
        assertEquals(0, detection.stopByUserCallCount)
        assertEquals(listOf("car"), detection.resumedVehicles)
    }
}
