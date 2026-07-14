package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import io.apptolast.paparcar.fakes.FakeZoneRepository
import io.apptolast.paparcar.fakes.data.repository.FakeManualParkingDetection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveManualParkingUseCaseTest {

    private val location = GpsPoint(40.416775, -3.703790, 10f, 1_000L, 0f)

    private fun build(
        parkingRepo: FakeUserParkingRepository = FakeUserParkingRepository(),
        authRepo: FakeAuthRepository = FakeAuthRepository(FakeAuthRepository.authenticatedSession()),
    ): Triple<SaveManualParkingUseCase, FakeAppNotificationManager, FakeManualParkingDetection> {
        val notifications = FakeAppNotificationManager()
        val detection = FakeManualParkingDetection()
        // ConfirmParking aborts without a resolvable vehicle (NoDefaultVehicle) —
        // seed the repo with a default one so the create path can succeed.
        val vehicleRepo = FakeVehicleRepository(
            defaultVehicle = Vehicle(
                id = "veh-A",
                userId = "user-1",
                brand = "Toyota",
                model = "Corolla",
                sizeCategory = VehicleSize.MEDIUM_SUV,
            ),
        )
        val confirmParking = ConfirmParkingUseCase(
            userParkingRepository = parkingRepo,
            vehicleRepository = vehicleRepo,
            zoneRepository = FakeZoneRepository(),
            geofenceService = FakeGeofenceManager(),
            enrichmentScheduler = FakeParkingEnrichmentScheduler(),
            authRepository = authRepo,
            config = ParkingDetectionConfig(),
            departureEventBus = FakeDepartureEventBus(),
        )
        val updateParkingLocation = UpdateParkingLocationUseCase(
            userParkingRepository = parkingRepo,
            geofenceService = FakeGeofenceManager(),
            enrichmentScheduler = FakeParkingEnrichmentScheduler(),
            config = ParkingDetectionConfig(),
            departureEventBus = FakeDepartureEventBus(),
        )
        return Triple(
            SaveManualParkingUseCase(confirmParking, updateParkingLocation, notifications, detection),
            notifications,
            detection,
        )
    }

    @Test
    fun should_create_session_and_fire_side_effects_when_placing_a_new_pin() = runTest {
        val parkingRepo = FakeUserParkingRepository()
        val (useCase, notifications, detection) = build(parkingRepo)

        val result = useCase(lat = 40.0, lon = -3.0, accuracy = 12f)

        assertTrue(result.isSuccess)
        val sessions = parkingRepo.observeActiveSessions().first()
        assertEquals(1, sessions.size)
        assertEquals(40.0, sessions.first().location.latitude)
        // Side effects of a user-confirmed CREATE: notification + detection teardown
        // so a late auto-confirm can't overwrite the pin. [DET-MANUAL-CANCEL-001]
        assertEquals(1, notifications.parkingSpotSavedCallCount)
        assertEquals(1, detection.stopCallCount)
    }

    @Test
    fun should_move_the_session_in_place_without_side_effects_when_editing() = runTest {
        val session = UserParking(
            id = "session-1",
            userId = "user-1",
            vehicleId = "veh-A",
            location = location,
            isActive = true,
        )
        val parkingRepo = FakeUserParkingRepository(initialSession = session)
        val (useCase, notifications, detection) = build(parkingRepo)

        val result = useCase(lat = 41.0, lon = -3.5, accuracy = 8f, editingParkingId = "session-1")

        assertTrue(result.isSuccess)
        val moved = parkingRepo.observeActiveSessions().first().first { it.id == "session-1" }
        assertEquals(41.0, moved.location.latitude)
        // A MOVE is a correction of an existing session — no "saved" notification,
        // no detection teardown.
        assertEquals(0, notifications.parkingSpotSavedCallCount)
        assertEquals(0, detection.stopCallCount)
    }

    @Test
    fun should_create_with_side_effects_when_confirming_a_detected_parking() = runTest {
        val parkingRepo = FakeUserParkingRepository()
        val (useCase, notifications, detection) = build(parkingRepo)

        val result = useCase.confirmDetected(location)

        assertTrue(result.isSuccess)
        assertEquals(1, parkingRepo.observeActiveSessions().first().size)
        assertEquals(1, notifications.parkingSpotSavedCallCount)
        assertEquals(1, detection.stopCallCount)
    }

    @Test
    fun should_fail_without_side_effects_when_not_authenticated() = runTest {
        val (useCase, notifications, detection) = build(authRepo = FakeAuthRepository(initialSession = null))

        val result = useCase(lat = 40.0, lon = -3.0, accuracy = 12f)

        assertTrue(result.isFailure)
        assertEquals(0, notifications.parkingSpotSavedCallCount)
        assertEquals(0, detection.stopCallCount)
    }
}
