@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DET-HONEST-CLOSE-001] Orchestration coverage: the honest close releases the stale pin, saves
 * the approximate artifact, and nudges — or stays fully silent. The two field aborts are pinned
 * (Camelias hop → zone, D2 return → silent).
 */
class RunHonestCloseUseCaseTest {

    private val config = ParkingDetectionConfig()

    private class Fixture(
        // The stale pin's real location differs per scenario: Melgarejo for the driven hop, Rosa
        // (where the CAR still is) for the walked-away return.
        stalePinLat: Double = 36.6002,
        stalePinLon: Double = -6.2512,
        staleGeofence: String = "stale-fence",
    ) {
        val config = ParkingDetectionConfig()
        val parkingRepo = FakeUserParkingRepository(
            initialSession = UserParking(
                id = "stale-old",
                vehicleId = "v-1",
                location = GpsPoint(stalePinLat, stalePinLon, accuracy = 12f, timestamp = 0L, speed = 0f),
                geofenceId = staleGeofence,
                isActive = true,
            ),
        )
        val notification = FakeAppNotificationManager()
        private val auth =
            FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession(userId = "user-1"))
        private val vehicleRepo = FakeVehicleRepository(
            defaultVehicle = Vehicle(id = "v-1", userId = "user-1", sizeCategory = VehicleSize.MEDIUM_SUV),
        )
        private val confirmParking = ConfirmParkingUseCase(
            userParkingRepository = parkingRepo,
            vehicleRepository = vehicleRepo,
            zoneRepository = FakeZoneRepository(),
            geofenceService = FakeGeofenceManager(),
            enrichmentScheduler = FakeParkingEnrichmentScheduler(),
            authRepository = auth,
            config = config,
            departureEventBus = FakeDepartureEventBus(),
        )
        val useCase = RunHonestCloseUseCase(
            userParkingRepository = parkingRepo,
            confirmParking = confirmParking,
            notificationPort = notification,
            evaluateHonestClose = EvaluateHonestCloseUseCase(config),
            config = config,
        )
    }

    @Test
    fun camelias_hop_releases_the_stale_pin_and_opens_an_approximate_zone_with_a_nudge() = runTest {
        val f = Fixture(stalePinLat = 36.6002, stalePinLon = -6.2512, staleGeofence = "melgarejo-fence")
        // ~318 m from Melgarejo, only 23 steps since the seal, urban accuracy → driven, no pin-grade.
        val outcome = f.useCase(
            vehicleId = "v-1",
            abortFix = GpsPoint(36.5974, -6.2505, accuracy = 60f, timestamp = 1_000L, speed = 0f),
            stepsSinceStalePin = 23L,
        )

        assertEquals(RunHonestCloseUseCase.OUTCOME_APPROXIMATE_ZONE, outcome)
        assertNull(
            f.parkingRepo.getActiveSessionByGeofence("melgarejo-fence"),
            "the stale pin the car drove away from must be released",
        )
        assertEquals(1, f.notification.markParkingNudgeCallCount, "honest close nudges — never silent")
        val saved = f.parkingRepo.getActiveSession()
        assertNotNull(saved, "the approximate session must be the new active one")
        assertTrue(saved.isApproximate, "the new session must be an AREA, not an exact point")
        assertTrue(saved.zoneRadiusMeters!! >= 60f, "the zone must read as an area, not a dot")
        assertEquals(f.config.reliabilityUnattendedSave, saved.detectionReliability, "never community-published")
    }

    @Test
    fun d2_return_on_foot_stays_fully_silent_and_keeps_the_stale_pin() = runTest {
        // The car is still at Rosa; the user walked ~1.1 km away and the stale exit was delivered
        // at rest at the destination.
        val f = Fixture(stalePinLat = 36.6054, stalePinLon = -6.2727, staleGeofence = "rosa-fence")
        // The walk explains the ~1.1 km (steps ≈ distance) → the car never moved.
        val outcome = f.useCase(
            vehicleId = "v-1",
            abortFix = GpsPoint(36.6088, -6.2843, accuracy = 3f, timestamp = 1_000L, speed = 0f),
            stepsSinceStalePin = 1099L,
        )

        assertNull(outcome, "a walk must never trigger the ladder")
        assertNotNull(
            f.parkingRepo.getActiveSessionByGeofence("rosa-fence"),
            "the stale pin must stay intact — the car is still there",
        )
        assertEquals(0, f.notification.markParkingNudgeCallCount, "no nudge on a silent close")
        assertEquals(0, f.parkingRepo.saveNewParkingSessionCallCount, "nothing saved")
    }

    @Test
    fun driven_with_a_pin_grade_fix_drops_an_approximate_pin_not_a_zone() = runTest {
        val f = Fixture()
        // Trip proven (10 steps ≪ ~300 m) AND a pin-grade fix (acc 8) → rung 1: a soft POINT.
        val outcome = f.useCase(
            vehicleId = "v-1",
            abortFix = GpsPoint(36.6029, -6.2512, accuracy = 8f, timestamp = 1_000L, speed = 0f),
            stepsSinceStalePin = 10L,
        )

        assertEquals(RunHonestCloseUseCase.OUTCOME_APPROXIMATE_PIN, outcome)
        assertEquals(1, f.notification.markParkingNudgeCallCount)
        val saved = f.parkingRepo.getActiveSession()
        assertNotNull(saved)
        assertTrue(!saved.isApproximate, "a pin-grade honest close is an exact point, not an area")
        assertNull(saved.zoneRadiusMeters)
    }
}
