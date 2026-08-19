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

    private companion object {
        /** A seal minutes old — the shape of every legit close. [DET-TRIP-WITNESS-001] */
        const val FRESH_SEAL_AGE_MS = 10 * 60 * 1_000L
    }

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
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            // Sealed beside the Melgarejo pin — same origin as the pin distance. [DET-STEP-BUDGET-ORIGIN-001]
            stepSealPoint = GpsPoint(36.6002, -6.2512, accuracy = 10f, timestamp = 0L, speed = 0f),
        )

        assertEquals(RunHonestCloseUseCase.OUTCOME_APPROXIMATE_ZONE, outcome.outcomeLabel)
        assertEquals(HonestCloseVerdict.REASON_TRIP_PROVEN, outcome.verdict.reason)
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
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = GpsPoint(36.6054, -6.2727, accuracy = 10f, timestamp = 0L, speed = 0f),
        )

        assertNull(outcome.outcomeLabel, "a walk must never trigger the ladder")
        assertEquals(HonestCloseVerdict.REASON_WALK_EXPLAINS, outcome.verdict.reason)
        assertNotNull(
            f.parkingRepo.getActiveSessionByGeofence("rosa-fence"),
            "the stale pin must stay intact — the car is still there",
        )
        assertEquals(0, f.notification.markParkingNudgeCallCount, "no nudge on a silent close")
        assertEquals(0, f.parkingRepo.saveNewParkingSessionCallCount, "nothing saved")
    }

    @Test
    fun frozen_counter_stays_fully_silent_and_keeps_the_correct_pin() = runTest {
        // Jerez restaurant FP (field 2026-07-25 22:29, Redmi): correct Calle Cobre pin, ~150 m
        // walk to the restaurant, MIUI cumulative counter FROZEN (delta 2) while the session's own
        // detector counted 8 pedestrian steps. The old ladder read the missing steps as a ride and
        // planted an approximate pin inside the restaurant, deposing the correct pin. The liveness
        // cross-check must keep the ladder silent. [DET-FROZEN-COUNTER-001]
        val f = Fixture(stalePinLat = 36.69944, stalePinLon = -6.10992, staleGeofence = "cobre-fence")
        val outcome = f.useCase(
            vehicleId = "v-1",
            abortFix = GpsPoint(36.70078, -6.10972, accuracy = 10f, timestamp = 1_000L, speed = 0f),
            stepsSinceStalePin = 2L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = GpsPoint(36.69944, -6.10992, accuracy = 10f, timestamp = 0L, speed = 0f),
            sessionStepEvents = 8,
        )

        assertNull(outcome.outcomeLabel, "a frozen counter must never testify a ride")
        assertEquals(HonestCloseVerdict.REASON_FROZEN_COUNTER, outcome.verdict.reason)
        assertNotNull(
            f.parkingRepo.getActiveSessionByGeofence("cobre-fence"),
            "the correct pin must survive — the car is still at Calle Cobre",
        )
        assertEquals(0, f.notification.markParkingNudgeCallCount, "no nudge on a silent close")
        assertEquals(0, f.parkingRepo.saveNewParkingSessionCallCount, "nothing saved")
    }

    @Test
    fun exit_echo_hours_later_stays_fully_silent_and_keeps_the_correct_pin() = runTest {
        // Glorieta home FP (field 2026-07-30 17:53, Redmi): 16 h after the real Angelita park, a
        // MIUI EXIT echo hit the phone sitting at home. Cumulative delta 0 (frozen through the
        // night), session witnessed 0 steps → the frozen-counter cross-check was blind and the
        // old ladder proved a "trip", released the Angelita pin and planted an approximate pin
        // ON THE HOME ("Glorieta Juan de Austria", doc 00d513ed). The seal-age gate must keep it
        // silent. [DET-TRIP-WITNESS-001]
        val f = Fixture(stalePinLat = 36.60583, stalePinLon = -6.23159, staleGeofence = "angelita-fence")
        val outcome = f.useCase(
            vehicleId = "v-1",
            abortFix = GpsPoint(36.60387, -6.23029, accuracy = 16f, timestamp = 1_000L, speed = 0f),
            stepsSinceStalePin = 0L,
            sealAgeMs = 16 * 60 * 60 * 1_000L,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = GpsPoint(36.60583, -6.23159, accuracy = 5f, timestamp = 0L, speed = 0f),
            sessionStepEvents = 0,
        )

        assertNull(outcome.outcomeLabel, "a stale step delta must never testify a ride")
        assertEquals(HonestCloseVerdict.REASON_STALE_SEAL, outcome.verdict.reason)
        assertNotNull(
            f.parkingRepo.getActiveSessionByGeofence("angelita-fence"),
            "the correct pin must survive — the car is still at Angelita",
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
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = GpsPoint(36.6002, -6.2512, accuracy = 10f, timestamp = 0L, speed = 0f),
        )

        assertEquals(RunHonestCloseUseCase.OUTCOME_APPROXIMATE_PIN, outcome.outcomeLabel)
        assertEquals(1, f.notification.markParkingNudgeCallCount)
        val saved = f.parkingRepo.getActiveSession()
        assertNotNull(saved)
        assertTrue(!saved.isApproximate, "a pin-grade honest close is an exact point, not an area")
        assertNull(saved.zoneRadiusMeters)
    }

    @Test
    fun teleported_abort_fix_stays_fully_silent_and_keeps_the_correct_pin() = runTest {
        // Cantarranas FP (field 2026-08-19 03:26, Oppo asleep at home): indoor multipath
        // teleported the abort fix ~995 m from the phone witnessed stationary at home 32 s
        // earlier. The old ladder proved a "trip" (delta 36 alive but ≪ 511 required), planted a
        // pin at the mirage AND registered its fence — which instantly saw the phone 949 m
        // outside and cascaded a SECOND approximate pin onto the user's home 33 min later. The
        // coherence gate keeps everything silent, so the cascade's stimulus (the fence at the
        // mirage) is never created. [DET-UNWITNESSED-DISPLACEMENT-001]
        val f = Fixture(stalePinLat = 36.608515, stalePinLon = -6.27778, staleGeofence = "bermeja-fence")
        val home = GpsPoint(36.60793, -6.27807, accuracy = 14f, timestamp = 0L, speed = 0f)
        val outcome = f.useCase(
            vehicleId = "v-1",
            abortFix = GpsPoint(36.6164806, -6.2748436, accuracy = 7.016f, timestamp = 1_000L, speed = 0f),
            stepsSinceStalePin = 36L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = home,
            witnessAgeMs = 32_000L,
            stepSealPoint = home,
            sessionStepEvents = 13,
        )

        assertNull(outcome.outcomeLabel, "a teleported fix must never testify a ride")
        assertEquals(HonestCloseVerdict.REASON_UNWITNESSED_DISPLACEMENT, outcome.verdict.reason)
        assertNotNull(
            f.parkingRepo.getActiveSessionByGeofence("bermeja-fence"),
            "the correct pin must survive — the car never moved from La Bermeja",
        )
        assertEquals(0, f.notification.markParkingNudgeCallCount, "no nudge on a silent close")
        assertEquals(0, f.parkingRepo.saveNewParkingSessionCallCount, "nothing saved — no fence, no cascade")
    }
}
