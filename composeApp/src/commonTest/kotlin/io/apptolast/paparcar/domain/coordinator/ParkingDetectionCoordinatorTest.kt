@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.domain.coordinator

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakeZoneRepository
import io.apptolast.paparcar.fakes.FakeStepDetectorSource
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ParkingDetectionCoordinator].
 *
 * Scope is the deterministic, non-time-dependent behaviour:
 *  - user-confirmed confirmation path
 *  - deny semantics (preserve hasEverMoved)
 *  - hasDetectedMovement requires BOTH speed AND distance
 *  - spurious sessions are gated by the movement guard
 *
 * The observation-window paths (vehicle-exit / slow-path auto-confirmation) are
 * time-driven via `kotlin.time.Clock.System.now()` and intentionally exercised by
 * [io.apptolast.paparcar.domain.usecase.parking.ParkingFlowIntegrationTest] instead
 * of being faked here.
 */
class ParkingDetectionCoordinatorTest {

    private val authSession = FakeAuthRepository.authenticatedSession(userId = "user-1")
    private val config = ParkingDetectionConfig()

    private fun setup(): TestEnv {
        val auth = FakeAuthRepository(initialSession = authSession)
        val vehicleRepo = FakeVehicleRepository(
            defaultVehicle = Vehicle(
                id = "v-1",
                userId = "user-1",
                sizeCategory = VehicleSize.MEDIUM_SUV,
            ),
        )
        val parkingRepo = FakeUserParkingRepository()
        val geofence = FakeGeofenceManager()
        val notification = FakeAppNotificationManager()
        val enrichment = FakeParkingEnrichmentScheduler()
        val confirmParking = ConfirmParkingUseCase(
            userParkingRepository = parkingRepo,
            vehicleRepository = vehicleRepo,
            zoneRepository = FakeZoneRepository(),
            geofenceService = geofence,
            notificationPort = notification,
            enrichmentScheduler = enrichment,
            authRepository = auth,
            config = config,
            departureEventBus = FakeDepartureEventBus(),
        )
        val notifyParking = NotifyParkingConfirmationUseCase(
            notificationPort = notification,
            vehicleRepository = vehicleRepo,
        )
        val calcConfidence = CalculateParkingConfidenceUseCase(config)
        val stepDetector = FakeStepDetectorSource()
        val coordinator = ParkingDetectionCoordinator(
            calculateParkingConfidence = calcConfidence,
            confirmParking = confirmParking,
            notifyParkingConfirmation = notifyParking,
            notificationPort = notification,
            vehicleRepository = vehicleRepo,
            stepDetector = stepDetector,
            config = config,
        )
        return TestEnv(coordinator, parkingRepo, geofence, enrichment, notification, stepDetector)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User-confirmed path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_confirm_parking_with_user_reliability_when_user_taps_yes() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)

            val job = launch { env.coordinator.invoke(locations) }

            // Establish session origin + cross the movement threshold (speed + distance).
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertTrue(env.coordinator.hasDetectedMovement, "movement should be detected after driving fix")

            // User taps "Yes, I parked" before any auto-confirmation timer.
            env.coordinator.onUserConfirmedParking()
            // Drive the loop with one more fix so collectLatest re-enters and sees the flag.
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "ConfirmParking should run exactly once")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved, "active session should be persisted")
            assertEquals(
                config.reliabilityUserConfirmed,
                saved.detectionReliability ?: 0f,
                /* absoluteTolerance = */ 0.0001f,
                "reliability should be the user-confirmed score",
            )
            assertEquals(1, env.geofence.createGeofenceCallCount, "geofence should be registered for the saved session")
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Deny resets state but preserves hasEverMoved
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_preserve_hasEverMoved_after_user_denies_parking() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertTrue(env.coordinator.hasDetectedMovement)

            env.coordinator.onUserDeniedParking()

            // Critical: hasEverMoved must survive the deny reset, otherwise the
            // maxNoMovementMs guard would immediately end the session on the next fix.
            assertTrue(
                env.coordinator.hasDetectedMovement,
                "hasEverMoved must survive onUserDeniedParking",
            )

            job.cancelAndJoin()
        }

    // ─────────────────────────────────────────────────────────────────────────
    // hasDetectedMovement: BOTH speed AND distance required
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_flag_movement_when_speed_meets_threshold_even_without_displacement() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Session origin.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            // Same location, speed above threshold — hasEverReachedDrivingSpeed triggers on speed alone.
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            assertTrue(
                env.coordinator.hasDetectedMovement,
                "hasDetectedMovement (hasEverReachedDrivingSpeed) triggers on speed, not on distance",
            )

            job.cancelAndJoin()
        }

    @Test
    fun should_not_flag_movement_when_distance_meets_threshold_but_speed_does_not() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Session origin.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            // ~200 m displacement (well past minimumTripDistanceMeters=150) but speed is below threshold.
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 1f))

            assertFalse(
                env.coordinator.hasDetectedMovement,
                "displacement alone without speed must not trip hasEverMoved",
            )

            job.cancelAndJoin()
        }

    // ─────────────────────────────────────────────────────────────────────────
    // LOC-002: low-accuracy fix must not clear bestStopLocation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_preserve_bestStopLocation_when_high_speed_fix_has_poor_accuracy() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Origin + cross the movement threshold so hasEverMoved=true.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            // Park: stopped fix with good accuracy at (40.005, -3.7). This becomes the
            // bestStopLocation we expect to survive the noisy fix below.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // Noisy GPS hallucination: apparent driving speed but bad accuracy. Pre-LOC-002
            // this single fix wiped bestStopLocation; with the gate in place it is ignored.
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 100f, timestamp = 0L, speed = 5f))

            // Trigger user-confirmed path with a stopped fix at a DIFFERENT spot (40.020).
            // If bestStopLocation was wrongly cleared, the saved location would be 40.020
            // (the fallback bestFix). With LOC-002 the saved location stays at 40.005.
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.020, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved, "active session should be persisted")
            assertEquals(
                40.005,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "bestStopLocation must survive a high-speed fix with accuracy > minGpsAccuracyForDriving",
            )
        }

    @Test
    fun should_clear_bestStopLocation_when_high_speed_fix_has_good_accuracy() =
        runTest(UnconfinedTestDispatcher()) {
            // Regression: ensure a trusted driving signal (good accuracy + driving speed)
            // still clears bestStopLocation, so traffic-light stops followed by genuine
            // driving away don't anchor the eventual park to the wrong intersection.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            // Brief stop (traffic light) — sets bestStopLocation.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            // Resume driving with GOOD accuracy. This is a trusted driving signal and
            // should clear bestStopLocation per the original LOC-001 contract.
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 5f, timestamp = 0L, speed = 5f))
            // User confirms after stopping at the eventual park spot (40.030).
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.030, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                40.030,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "trusted driving fix must clear bestStopLocation so the eventual park anchors here",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // PARKING-001: reposition-burst clears bestStopLocation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_clear_bestStopLocation_after_three_consecutive_reposition_fixes() =
        runTest(UnconfinedTestDispatcher()) {
            // Scenario: wait + maneuver to plaza. The car stops at the waiting position,
            // bestStopLocation is captured there. The brief maneuver to the actual plaza
            // produces 3 consecutive fixes at 1.7 m/s with good accuracy — below
            // clearBestStopSpeedMps so LOC-002 alone would preserve the stale value, but
            // PARKING-001 counts the burst (repositionFixCount=3) and clears it. The
            // eventual confirmed location must be the plaza, not the waiting position.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Origin + cross the movement threshold so hasEverMoved=true.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            // Waiting stop — bestStopLocation = 40.005.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // Reposition burst: 3 fixes at 1.7 m/s with good accuracy. Below
            // clearBestStopSpeedMps (2.5) — LOC-002 alone would preserve. With PARKING-001
            // the third fix triggers the reset (repositionFixCount=3). [PARKING-001]
            locations.emit(GpsPoint(40.006, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.7f))
            locations.emit(GpsPoint(40.007, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.7f))
            locations.emit(GpsPoint(40.008, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.7f))

            // Trigger user-confirm at the actual plaza (40.010). With the burst-reset the
            // saved location should be 40.010, not the stale waiting 40.005.
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                40.010,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "reposition burst must clear bestStopLocation so the plaza wins over the waiting spot",
            )
        }

    @Test
    fun should_preserve_bestStopLocation_on_single_reposition_fix() =
        runTest(UnconfinedTestDispatcher()) {
            // A single 1.7 m/s fix must NOT clear bestStopLocation — that would be the
            // LOC-002 noise-spike scenario lowered to reposition speed. Three consecutive
            // fixes are required (repositionFixCount=3). [PARKING-001]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            // Park: bestStopLocation = 40.005.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // ONE reposition-speed fix → counter=1 < repositionFixCount(3). bestStopLocation preserved.
            locations.emit(GpsPoint(40.006, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.7f))

            // User confirms at a different stop. Saved location should still be 40.005.
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.020, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                40.005,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "single reposition-speed fix must not clear bestStopLocation",
            )
        }

    @Test
    fun should_preserve_bestStopLocation_on_sustained_walking() =
        runTest(UnconfinedTestDispatcher()) {
            // The user parks and walks toward their destination at ~1.2 m/s. Walking pace
            // is below repositionSpeedMps (1.7), so consecutiveRepositionFixes never
            // increments. bestStopLocation must remain at the parked-car position even
            // across many walking fixes.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            // Park: bestStopLocation = 40.005.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // Walking sequence: speed sustained at 1.2 m/s — never crosses 1.7.
            repeat(6) { i ->
                locations.emit(
                    GpsPoint(40.005 + (i + 1) * 0.0001, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f)
                )
            }

            // User confirms at the walking destination. Saved location must still be the parked spot.
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.020, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                40.005,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "sustained walking pace must not clear bestStopLocation",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification reset on invoke entry
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_dismiss_parking_confirmation_notification_on_each_invoke_entry() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            // Start a session — this should call dismiss() during the reset().
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }
            // Drive the flow once so the reset() pathway has surely run.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            job.cancelAndJoin()

            assertTrue(
                env.notification.dismissedIds.contains(
                    io.apptolast.paparcar.domain.notification.AppNotificationManager
                        .PARKING_CONFIRMATION_NOTIFICATION_ID,
                ),
                "reset() must dismiss the PARKING_CONFIRMATION notification",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun stationaryFix(lat: Double, lon: Double): GpsPoint =
        GpsPoint(latitude = lat, longitude = lon, accuracy = 5f, timestamp = 0L, speed = 0f)

    private data class TestEnv(
        val coordinator: ParkingDetectionCoordinator,
        val parkingRepo: FakeUserParkingRepository,
        val geofence: FakeGeofenceManager,
        val enrichment: FakeParkingEnrichmentScheduler,
        val notification: FakeAppNotificationManager,
        val stepDetector: FakeStepDetectorSource,
    )
}
