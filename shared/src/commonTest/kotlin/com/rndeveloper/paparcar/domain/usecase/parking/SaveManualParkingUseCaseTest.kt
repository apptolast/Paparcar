package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeParkingEnrichmentScheduler
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeManualParkingDetection
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
    fun should_stamp_manual_report_when_placing_a_spontaneous_pin() = runTest {
        val parkingRepo = FakeUserParkingRepository()
        val (useCase, _, _) = build(parkingRepo)

        val result = useCase(lat = 40.0, lon = -3.0, accuracy = 12f)

        assertTrue(result.isSuccess)
        val session = parkingRepo.observeActiveSessions().first().single()
        assertEquals(SpotType.MANUAL_REPORT, session.spotType)
        assertEquals("manual", session.detectionPath)
    }

    @Test
    fun should_keep_detection_provenance_when_pin_answers_a_detection_nudge() = runTest {
        // Detection nominated the park (nudge) and the user confirmed the pin: the session keeps
        // detection provenance so the freed spot later publishes as AUTO_DETECTED, not a 15-min
        // manual report. [DET-NUDGE-PIN-PROVENANCE-001]
        val parkingRepo = FakeUserParkingRepository()
        val (useCase, notifications, detection) = build(parkingRepo)

        val result = useCase(lat = 40.0, lon = -3.0, accuracy = 12f, nudgeSource = "")

        assertTrue(result.isSuccess)
        val session = parkingRepo.observeActiveSessions().first().single()
        assertEquals(SpotType.AUTO_DETECTED, session.spotType)
        assertEquals("nudge", session.detectionPath)
        assertEquals(1.0f, session.detectionReliability)
        // Same CREATE side-effects as any user-confirmed pin.
        assertEquals(1, notifications.parkingSpotSavedCallCount)
        assertEquals(1, detection.stopCallCount)
    }

    @Test
    fun should_stampWhichLaneAsked_when_theNudgeCarriesItsSource() = runTest {
        // [DET-A-NUDGE-MUST-SAY-WHO-ASKED-001] The pin is the only artefact that survives a wipe,
        // and `nudge` alone could not tell a Bluetooth ask from a coordinator one.
        val parkingRepo = FakeUserParkingRepository()
        val (useCase, _, _) = build(parkingRepo)

        val result = useCase(lat = 40.0, lon = -3.0, accuracy = 12f, nudgeSource = "bt_no_move")

        assertTrue(result.isSuccess)
        val session = parkingRepo.observeActiveSessions().first().single()
        assertEquals("nudge_bt_no_move", session.detectionPath)
        // Still a nudge in every other respect — provenance added, nothing reclassified.
        assertEquals(SpotType.AUTO_DETECTED, session.spotType)
        assertEquals(1.0f, session.detectionReliability)
    }

    @Test
    fun should_stayAManualPin_when_nobodyAsked() = runTest {
        // The null/blank distinction carries the whole meaning: blank = asked, asker unknown;
        // null = nobody asked. A hand-placed pin must never claim detection nominated it.
        val parkingRepo = FakeUserParkingRepository()
        val (useCase, _, _) = build(parkingRepo)

        useCase(lat = 40.0, lon = -3.0, accuracy = 12f, nudgeSource = null)

        val session = parkingRepo.observeActiveSessions().first().single()
        assertEquals("manual", session.detectionPath)
        assertEquals(SpotType.MANUAL_REPORT, session.spotType)
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
