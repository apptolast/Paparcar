@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.rndeveloper.paparcar.domain.detection.coordinator

import com.rndeveloper.paparcar.domain.detection.CoordinatorParkingDetector
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.VehicleType
import com.rndeveloper.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateUnattendedParkingSaveUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeActivityRecognitionManager
import com.rndeveloper.paparcar.domain.detection.ArmEvidence
import com.rndeveloper.paparcar.domain.detection.HoldAction
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import com.rndeveloper.paparcar.fakes.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.FakeDetectionEventLogger
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeParkingEnrichmentScheduler
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import com.rndeveloper.paparcar.fakes.FakeStepDetectorSource
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [CoordinatorParkingDetector].
 *
 * Scope is the deterministic, non-time-dependent behaviour:
 *  - user-confirmed confirmation path
 *  - deny semantics (preserve hasEverMoved)
 *  - hasDetectedMovement requires BOTH speed AND distance
 *  - spurious sessions are gated by the movement guard
 *
 * The observation-window paths (vehicle-exit / slow-path auto-confirmation) are
 * time-driven via `kotlin.time.Clock.System.now()` and intentionally exercised by
 * [com.rndeveloper.paparcar.domain.usecase.parking.ParkingFlowIntegrationTest] instead
 * of being faked here.
 */
class CoordinatorParkingDetectorTest {

    /** Resting latitude of the short-hop fixture (~900 m north of the pin). */
    private val SHORT_HOP_PARKED_LAT = 40.00815

    /** [DET-UNVERIFIED-ARM-DRIVE-PROOF-001] Where the late-armed hop comes to rest — ~1.13 km from
     *  the pin it left, the field distance of 2026-08-15. */
    private val LATE_ARM_PARKED_LAT = 40.01015

    private val authSession = FakeAuthRepository.authenticatedSession(userId = "user-1")
    // confirmHoldMs = 0 → no post-confirm hold, so egress confirms fire immediately and these
    // deterministic tests stay synchronous. The hold itself is covered by dedicated tests below
    // that drive an injected clock. [DET-C-02]
    private val config = ParkingDetectionConfig(confirmHoldMs = 0L)

    private fun setup(
        config: ParkingDetectionConfig = this.config,
        clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
        extraVehicles: List<Vehicle> = emptyList(),
        /** [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] The garage's answer to "what do you own" — the
         *  one human-powered source no measurement overrides [DET-SOLID-001 C2]. */
        defaultVehicleType: VehicleType = VehicleType.CAR,
    ): TestEnv {
        val auth = FakeAuthRepository(initialSession = authSession)
        val vehicleRepo = FakeVehicleRepository(
            defaultVehicle = Vehicle(
                id = "v-1",
                userId = "user-1",
                sizeCategory = VehicleSize.MEDIUM_SUV,
                vehicleType = defaultVehicleType,
            ),
            extraVehicles = extraVehicles,
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
        val detectionLogger = FakeDetectionEventLogger()
        val coordinator = CoordinatorParkingDetector(
            calculateParkingConfidence = calcConfidence,
            confirmParking = confirmParking,
            notifyParkingConfirmation = notifyParking,
            notificationPort = notification,
            vehicleRepository = vehicleRepo,
            stepDetector = stepDetector,
            config = config,
            detectionEventLogger = detectionLogger,
            evaluateParkingDecision = EvaluateParkingDecisionUseCase(config),
            // [DET-DI-DETECTION-MODULE-001] Was the coordinator's own constructor default; the
            // instance is identical, it is just built where it can be seen.
            evaluateUnattendedParkingSave = EvaluateUnattendedParkingSaveUseCase(config),
            // These three used to default to null. They still are null here — this suite exercises
            // neither the Home phase surface nor the deduced-departure pair — but now it says so.
            phaseSink = null,
            finalizeDeducedDeparture = null,
            retractDeducedDeparture = null,
            clock = clock,
        )
        return TestEnv(coordinator, parkingRepo, geofence, enrichment, notification, stepDetector, detectionLogger)
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

    @Test
    fun should_attribute_the_park_to_the_nominating_vehicle_not_the_active_one() =
        runTest(UnconfinedTestDispatcher()) {
            // The active vehicle is v-1, but a geofence exit nominated v-nominator. The confirmed
            // park must belong to the NOMINATOR, not whatever ranked active. [VEH-ACTIVE-FENCE-001]
            val env = setup(
                extraVehicles = listOf(
                    Vehicle(id = "v-nominator", userId = "user-1", sizeCategory = VehicleSize.VAN_HIGH),
                ),
            )
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, nominatingVehicleId = "v-nominator") }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            env.coordinator.onUserConfirmedParking()
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved, "active session should be persisted")
            assertEquals("v-nominator", saved.vehicleId, "park must be attributed to the nominating fence's vehicle")
            // End-to-end proof: ConfirmParking resolved the nominator's vehicle (its VAN size), not v-1's.
            assertEquals(VehicleSize.VAN_HIGH, saved.sizeCategory)
        }

    @Test
    fun should_attribute_the_park_to_the_active_vehicle_when_the_nominator_is_bt_paired() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-BT-OWNERSHIP-001] Field replay 2026-08-11: the user drove the ACTIVE Focus (v-1)
            // all day, but the parked Kamiq's fence (BT-paired, inactive) nominated every arm, and
            // the old nominator-always-wins lock stamped all 8 parks on the Kamiq — each confirm
            // re-fencing the Kamiq and re-arming the chain. A BT-paired vehicle's identity is the
            // MAC, never a fence: the lock must veto the nominator and attribute to the active car.
            val env = setup(
                extraVehicles = listOf(
                    Vehicle(
                        id = "v-kamiq",
                        userId = "user-1",
                        sizeCategory = VehicleSize.VAN_HIGH,
                        bluetoothDeviceId = "AA:BB:CC:DD:EE:FF",
                    ),
                ),
            )
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, nominatingVehicleId = "v-kamiq") }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            env.coordinator.onUserConfirmedParking()
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved, "active session should be persisted")
            assertEquals("v-1", saved.vehicleId, "BT-paired nominator must be vetoed — park belongs to the active vehicle")
            // End-to-end proof: ConfirmParking resolved the ACTIVE vehicle (its SUV size), not the Kamiq's VAN.
            assertEquals(VehicleSize.MEDIUM_SUV, saved.sizeCategory)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-LOG-03: coordinator emits a diagnostics session trace
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_emit_session_trace_when_user_confirms() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            env.coordinator.onUserConfirmedParking()
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            job.cancelAndJoin()

            val events = env.detectionLogger.events
            assertTrue(events.any { it is DetectionEvent.SessionStarted }, "SessionStarted must be logged")
            assertTrue(events.any { it is DetectionEvent.LocationFix }, "raw GPS fixes must be logged [DET-LOG-04]")
            assertTrue(
                events.any { it is DetectionEvent.Decision && it.outcome == "CONFIRMED" && it.pathLabel == "user" },
                "a CONFIRMED Decision with the user path must be logged",
            )
            assertTrue(events.any { it is DetectionEvent.SessionEnded }, "SessionEnded must be logged")
            assertEquals(
                1,
                events.map { it.sessionId }.distinct().size,
                "all events in one session must share a single sessionId",
            )
        }

    @Test
    fun should_log_vehicle_exit_transition_in_trace() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-LOG-04] An IN_VEHICLE→EXIT fed via onVehicleExit() must surface as an
            // ActivityTransition in the trace, edge-logged once on the next fix.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            env.coordinator.onVehicleExit()
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            job.cancelAndJoin()

            assertTrue(
                env.detectionLogger.events.any {
                    it is DetectionEvent.ActivityTransition && it.activity == "IN_VEHICLE" && it.transition == "EXIT"
                },
                "an IN_VEHICLE EXIT transition must be logged in the trace",
            )
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
                    com.rndeveloper.paparcar.domain.notification.AppNotificationManager
                        .PARKING_CONFIRMATION_NOTIFICATION_ID,
                ),
                "session-start must dismiss any stale PARKING_CONFIRMATION notification",
            )
        }

    @Test
    fun should_keep_post_save_card_after_session_finally() =
        runTest(UnconfinedTestDispatcher()) {
            // Regression: after auto-confirm, [runConfirm] posts the unified "Vehículo aparcado ·
            // Cancelar" card on PARKING_CONFIRMATION_NOTIFICATION_ID. The session finally must
            // NOT dismiss that id, otherwise the user loses the revert affordance ~1–2 s after
            // it appears (next location tick closes the flow). [REFACTOR-300 follow-up]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive past the movement threshold so the user-confirm path can run.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            // User taps "Yes" — runConfirm fires → onSuccess → showParkingSavedConfirm.
            env.coordinator.onUserConfirmedParking()
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.notification.parkingSavedConfirmCallCount,
                "showParkingSavedConfirm should fire exactly once on auto-confirm success",
            )
            // The post-save card must be the LAST op on the confirmation id — nothing
            // (finally's reset(), or any other path) should have dismissed it afterwards.
            assertEquals(
                "savedConfirm",
                env.notification.confirmationNotifOps.last(),
                "post-save card must survive session finally — no dismiss may follow it",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // BUG-FALSE-ENTER-WALKING: 8 steps before driving speed → abort the session
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_abort_session_when_steps_burst_before_driving_speed() =
        runTest(UnconfinedTestDispatcher()) {
            // Scenario: AR misfires IN_VEHICLE_ENTER while the user is walking from the
            // car carrying bags. The coordinator should not wait the full maxNoMovementMs
            // (4 min) — once 8 pedestrian steps have accumulated without driving speed
            // being reached, abort the session and let the service stop. [BUG-FALSE-ENTER-WALKING]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Emit a slow GPS fix (walking speed) — does NOT cross minimumTripSpeedMps (5 m/s).
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))

            // 8 pedestrian steps fire — typical post-park burst (door slam + walk to trunk).
            env.stepDetector.emitSteps(8)

            // Next location fix — the abort check runs here and flips completed=true. The
            // takeWhile won't actually close the flow until the *following* emit, so we use
            // cancelAndJoin to wrap up the test rather than waiting for that next tick.
            locations.emit(GpsPoint(40.0001, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))

            job.cancelAndJoin()

            assertFalse(
                env.coordinator.hasDetectedMovement,
                "session must abort without ever reaching driving speed",
            )
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "no parking save must happen — this was a false ENTER, not a real trip",
            )
        }

    @Test
    fun should_not_abort_session_when_steps_arrive_after_driving_speed() =
        runTest(UnconfinedTestDispatcher()) {
            // Regression guard: once the user is genuinely driving (hasEverReachedDrivingSpeed),
            // step events during a stop are the normal "user got out" proof and must NOT
            // trigger the false-ENTER abort. The abort gate is strictly pre-drive.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive: cross movement threshold.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertTrue(env.coordinator.hasDetectedMovement, "sanity: driving speed reached")

            // Stop and fire 8 steps — this is the canonical "user got out" signal, not a false ENTER.
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))
            env.stepDetector.emitSteps(8)
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            // Session must still be alive (no abort). The user-confirm path can still confirm.
            assertTrue(
                env.coordinator.hasDetectedMovement,
                "session must remain alive after driving + steps — abort is pre-drive only",
            )

            job.cancelAndJoin()
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001: the arm's seed is a hypothesis
    // the departure worker adjudicates — and it may take it back
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_abort_the_walk_when_the_departure_the_arm_rested_on_is_dismissed() =
        runTest(UnconfinedTestDispatcher()) {
            // Field replay 2026-08-22 20:50 (Oppo, at home). An indoor GPS mirage broke the parked
            // car's geofence and armed this session `verified_speed`, seeding it already-driving.
            // Nothing ever moved: the departure worker sampled 0.9, 0.0 and 0.0 km/h and DISMISSED
            // the departure — but the seed it had granted stayed, the anti-walking guards stayed
            // down, and nine steps through the house confirmed a phantom park in the living room,
            // replacing the correct pin. With the seed retracted the same burst must abort.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedBySpeed(speedKmh = 36f, accuracyM = 5.5f),
                    armingGeofenceId = "geo-1",
                )
            }

            assertTrue(env.coordinator.hasDetectedMovement, "the arm seeds the session on trust")

            env.coordinator.notifyDepartureDismissed("geo-1")

            assertFalse(
                env.coordinator.hasDetectedMovement,
                "a refuted departure must take back the seed it lent",
            )

            // The same indoor walk that used to confirm.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(9)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "no phantom park may be confirmed once the arm's evidence is refuted",
            )
        }

    @Test
    fun should_keep_the_seed_when_another_fence_departure_is_dismissed() =
        runTest(UnconfinedTestDispatcher()) {
            // The worker adjudicates ONE fence. A verdict about a different one says nothing about
            // this session and must not disarm it.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedBySpeed(speedKmh = 20f, accuracyM = 10f),
                    armingGeofenceId = "geo-1",
                )
            }

            env.coordinator.notifyDepartureDismissed("geo-2")

            assertTrue(
                env.coordinator.hasDetectedMovement,
                "a foreign fence's verdict must not retract this arm's seed",
            )

            job.cancelAndJoin()
        }

    @Test
    fun should_keep_the_seed_once_the_session_has_measured_a_drive_of_its_own() =
        runTest(UnconfinedTestDispatcher()) {
            // The dismissal refutes the EXIT, never the trip that followed it. Once this session's
            // own track proves a drive, the seed is a measured fact and stops being retractable —
            // a slow garage exit whose four speed samples all missed the departure threshold must
            // not lose a drive its GPS stream witnessed.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedBySpeed(speedKmh = 20f, accuracyM = 10f),
                    armingGeofenceId = "geo-1",
                )
            }

            emitCorroboratedDrive(locations)

            env.coordinator.notifyDepartureDismissed("geo-1")

            assertTrue(
                env.coordinator.hasDetectedMovement,
                "a drive this session MEASURED survives the exit's adjudication",
            )

            job.cancelAndJoin()
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-G-04: a GEOFENCE_EXIT-armed session is seeded already-driving
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_confirm_geofence_armed_session_even_when_it_never_reaches_driving_speed() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-G-04] Real trace (2026-07-01, El Puerto de Santa María): a GEOFENCE_EXIT armed a
            // coordinator session for a SHORT hop between two parks. Its GPS stream warmed up after
            // the fast driving was already over — every fix reported ≤ 2.9 m/s (< minimumTripSpeedMps
            // = 5), so hasEverReachedDrivingSpeed stayed false and the egress steps tripped
            // falseEnterAbortSteps → aborted_false_enter, and the REAL park was lost. Because the
            // geofence exit is a CONFIRMED departure (the car left its own parked-car geofence — the
            // same signal that publishes the freed spot), the session is armed already-driving and
            // MUST confirm the park instead of aborting.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedBySpeed(speedKmh = 20f, accuracyM = 10f)) }

            // The seed is applied before the first fix — the session reports movement immediately.
            assertTrue(
                env.coordinator.hasDetectedMovement,
                "a geofence-armed session is seeded already-driving [DET-G-04]",
            )

            // Arrival + park: NO fix ever crosses minimumTripSpeedMps (5 m/s). bestStopLocation is
            // captured at (40.005) — the real parked-car position.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 2.8f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // User gets out: 8 pedestrian steps + egress displacement (~33 m from the anchor). Pre-fix
            // this same burst would have tripped the false-ENTER abort; now it confirms.
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "geofence-armed session must confirm the real park despite never re-reaching driving speed [DET-G-04]",
            )
            assertEquals(
                40.005,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.00001,
                "confirmed location must be the parked-car position (bestStopLocation)",
            )
        }

    @Test
    fun should_still_abort_false_enter_when_session_is_not_a_confirmed_departure() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-G-04] Regression guard: the seed must NOT leak to MANUAL/unverified sessions.
            // Without verified arm evidence the false-ENTER guard still protects against a
            // spurious AR IN_VEHICLE_ENTER while walking (bus/taxi/desk) — same input as the test
            // above, but this session must ABORT and save nothing.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) } // default: ArmEvidence.Manual (no seed)

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 2.8f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertFalse(
                env.coordinator.hasDetectedMovement,
                "a non-departure session that never reaches driving speed must not be treated as driving",
            )
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "false-ENTER guard must still abort spurious walking sessions [DET-G-04]",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-SOLID-001 C3: the time-driven paths, exercised with the injected clock
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_abort_after_maxNoMovement_without_driving() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = config.maxNoMovementMs + 1_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_no_movement", ended.outcome)
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // [DET-JAM-WINDOW-001] Measured creep buys the extended no-movement budget
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_extend_no_movement_budget_when_creeping_through_a_jam() =
        runTest(UnconfinedTestDispatcher()) {
            // Left the spot but stuck in stop-go traffic: ~55 m of RECENT crawl below driving
            // speed at the 4-min check must keep the session alive instead of folding it silently.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = config.maxNoMovementMs - 10_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            // 55 m crawled since the fix 11 s ago — recent creep, guard must extend.
            nowMs = config.maxNoMovementMs + 1_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 5f, timestamp = 0L, speed = 2f))
            // Still creeping within each window — stays alive deep into the extended budget.
            nowMs = config.maxNoMovementMs + 100_000L
            locations.emit(GpsPoint(40.0010, -3.7, accuracy = 5f, timestamp = 0L, speed = 2f))

            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>().isEmpty(),
                "a measured recent crawl must not fold at the standard budget",
            )
            job.cancelAndJoin()
        }

    @Test
    fun should_fold_with_the_jam_outcome_when_the_extended_budget_expires_without_driving() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            // Crawling ~55 m per beat, each beat inside the creep window, past the standard budget…
            var lat = 40.0
            var t = config.maxNoMovementMs - 10_000L
            while (t <= config.jamExtendedNoMovementMs) {
                nowMs = t
                locations.emit(GpsPoint(lat, -3.7, accuracy = 5f, timestamp = 0L, speed = 2f))
                lat += 0.0005
                t += 100_000L
            }
            // …until the extended ceiling: still creeping, still never drove — fold, jam label.
            nowMs = config.jamExtendedNoMovementMs + 1_000L
            locations.emit(GpsPoint(lat, -3.7, accuracy = 5f, timestamp = 0L, speed = 2f))

            job.cancelAndJoin()

            // Distinct label so field telemetry can size the cohort (jam that never cleared vs
            // crawl into a re-park) before deciding whether it deserves a nudge; no pin, no save.
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_no_movement_jam", ended.outcome)
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount)
        }

    @Test
    fun should_not_extend_the_budget_for_stationary_gps_noise() =
        runTest(UnconfinedTestDispatcher()) {
            // A zombie/batched arm at home: ~11 m of GPS noise is not creep — the standard
            // 4-min fold (and its OEM power profile) must stay exactly as it was.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = config.maxNoMovementMs + 1_000L
            locations.emit(GpsPoint(40.0001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_no_movement", ended.outcome)
        }

    @Test
    fun should_never_extend_the_stale_lane_probe_even_with_creep() =
        runTest(UnconfinedTestDispatcher()) {
            // The zombie probe's whole point is folding fast on stale deliveries — displacement
            // on a stale arm is untrustworthy (the phone may have travelled since the event).
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, staleExitDelivery = true) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = config.staleExitNoMovementMs + 1_000L
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_no_movement", ended.outcome)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // [DET-ZOMBIE-PROBE-001] Stale-delivered EXITs get the SHORT no-movement probe
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_abort_after_short_probe_when_stale_exit_delivery_never_moves() =
        runTest(UnconfinedTestDispatcher()) {
            // Zombie delivery: the OS hands over an hours-old EXIT while the phone sits at home.
            // The session must fold at staleExitNoMovementMs (~75 s), not burn the full 4-min
            // GPS window (field 2026-07-24/25: three 4.1-min zombie sessions per night). The
            // budget shrinks but the outcome label stays aborted_no_movement (honest-close and
            // diagnostics tooling key on it).
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, staleExitDelivery = true) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = config.staleExitNoMovementMs + 1_000L // well inside maxNoMovementMs
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_no_movement", ended.outcome)
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount)
        }

    @Test
    fun should_not_abort_within_short_probe_when_stale_exit_delivery() =
        runTest(UnconfinedTestDispatcher()) {
            // The probe is a budget, not an instant kill: fixes inside the window keep the
            // session alive so a slow GPS warm-up on a REAL mid-drive far delivery still gets
            // its chance to show driving speed.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, staleExitDelivery = true) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = config.staleExitNoMovementMs - 10_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>().isEmpty(),
                "must not abort before the probe budget elapses",
            )
            job.cancelAndJoin()
        }

    @Test
    fun should_escape_short_probe_when_driving_fix_arrives_within_it() =
        runTest(UnconfinedTestDispatcher()) {
            // A REAL mid-drive far delivery: the car is moving by construction, so a credible
            // driving-speed fix lands within the probe. hasEverReachedDrivingSpeed flips and the
            // session survives well past maxNoMovementMs — the probe never fires on real trips.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, staleExitDelivery = true) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = 30_000L
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            nowMs = config.maxNoMovementMs + 60_000L
            locations.emit(GpsPoint(40.004, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>()
                    .none { it.outcome == "aborted_no_movement" },
                "a session that showed driving must never fold as no-movement",
            )
            job.cancelAndJoin()
        }

    @Test
    fun should_save_unattended_when_prompt_gets_no_response_within_timeout() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-RECONCILE-001] The prompt only shows after a real trip + stop; an unanswered
            // notification must not cost the user their parking (field incident 2026-07-06,
            // Redmi: a real parking was discarded after 15 silent minutes). The timeout SAVES
            // with low reliability instead of aborting; the session still closes. [BUG-STUCK-SESSION]
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive, then stop long enough for the slow path to reach Low → Notified (90 s gate
            // + 90 s lowNotifTimeout), then let the 15-min response window expire untouched.
            emitCorroboratedDrive(locations)
            nowMs = 1_000L
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = 1_000L + config.slowPathGateMs + 5_000L
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs += config.lowNotifTimeoutMs + 5_000L
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "prompt must be shown")
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "nothing saved while the prompt waits")

            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_unattended_timeout", ended.outcome, "[DET-RECONCILE-001]")
            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "unanswered prompt saves, never discards")
        }

    @Test
    fun should_nudge_instead_of_saving_when_unattended_timeout_has_no_measured_driving() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-AR-FIRST-001 F3] Seeded evidence (verified_enter arm) authorises RELEASING the
            // old spot, never PLACING a new pin: a session armed after the trip ended follows the
            // pedestrian, and its unattended save planted the pin in the user's living room
            // (field 2026-07-10 19:34, Redmi). Without measured in-session driving the timeout
            // must ask WHERE the car is, not guess.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedByVehicleEnter(30_000L))
            }

            // Seeded session: hasEverReachedDrivingSpeed=true but every fix is pedestrian —
            // maxSpeedMps never crosses minimumTripSpeedMps. Slow path reaches High → prompt.
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = config.slowPath5MinMs + 1_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 10f, timestamp = 0L, speed = 0.1f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "prompt must be shown")

            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 10f, timestamp = 0L, speed = 0.1f))

            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "no pin without measured driving")
            assertEquals(1, env.notification.markParkingNudgeCallCount, "the user must be asked where the car is")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_unattended_no_drive", ended.outcome, "[DET-AR-FIRST-001]")
        }

    @Test
    fun should_save_zone_when_no_drive_timeout_has_live_egress_and_vehicular_signal() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-NODRIVE-ZONE-001] An EXIT delivered kilometres late births the session at the
            // destination (field 2026-07-27 20:36, Redmi): the only "driving" is a short raw
            // burst the track can never corroborate, so the timeout used to exit nudge-only and
            // a REAL park was lost. With a LIVE counter at egress scale, a real walked
            // displacement off the anchor and an in-session vehicular signal, the honest exit is
            // an approximate ZONE at the locked kerb anchor — never a pin, never a lost park.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedByVehicleEnter(30_000L))
            }

            // One credible raw driving fix — a single-sided burst `corroboratesDrive` never latches on.
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 12f, timestamp = 0L, speed = 7f))
            nowMs = 10_000L
            val kerbLat = 40.0005
            locations.emit(GpsPoint(kerbLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // kerb stop → anchor
            env.stepDetector.emitSteps(12) // live egress steps at the kerb — the anchor LOCKS
            env.coordinator.onVehicleExit() // AR IN_VEHICLE→EXIT, the in-session vehicular signal
            nowMs = 40_000L
            val homeLat = 40.0010 // ~55 m walk to the door
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 8f, timestamp = 0L, speed = 0.5f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "weak evidence must ask first")
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "nothing saved while the prompt waits")

            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 8f, timestamp = 0L, speed = 0.1f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the park must be KEPT as a zone, not lost")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertTrue(saved.isApproximate, "no proven driving may only yield an AREA, never an exact pin")
            assertEquals(kerbLat, saved.location.latitude, 0.00005, "zone centers on the locked kerb anchor")
            assertTrue(saved.zoneRadiusMeters!! >= config.honestCloseMinZoneRadiusMeters)
            assertEquals(config.reliabilityUnattendedSave, saved.detectionReliability, "never community-published")
            assertEquals(0, env.notification.markParkingNudgeCallCount, "the saved-parking card is the ask — no extra nudge")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_unattended_zone_no_drive_egress", ended.outcome, "[DET-NODRIVE-ZONE-001]")
        }

    @Test
    fun should_nudge_when_no_drive_timeout_lacks_egress_scale_steps() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-NODRIVE-ZONE-001 anti-resurrection] The same-day mirage (2026-07-27 14:56:
            // indoor drift, ONE step, a 45 m/s Doppler burst the track never corroborated) must
            // stay nudge-only: without egress-scale LIVE steps the walk from the "car" is
            // unbounded and a zone would assert the car is somewhere it provably may not be.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedByVehicleEnter(30_000L))
            }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 45f)) // mirage burst
            nowMs = 10_000L
            locations.emit(GpsPoint(40.0002, -3.7, accuracy = 10f, timestamp = 0L, speed = 0f)) // drift stop → anchor
            env.stepDetector.emitSteps(1) // the mirage's single step
            nowMs = 60_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 12f, timestamp = 0L, speed = 0.8f)) // 33 m drift
            nowMs += config.slowPath5MinMs + 1_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 10f, timestamp = 0L, speed = 0.1f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "prompt must be shown")

            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 10f, timestamp = 0L, speed = 0.1f))

            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "the mirage must never resurrect as a zone")
            assertEquals(1, env.notification.markParkingNudgeCallCount, "nudge-only stays the mirage's exit")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_unattended_no_drive", ended.outcome, "[DET-NODRIVE-ZONE-001]")
        }

    @Test
    fun should_nudge_when_no_drive_timeout_has_no_vehicular_signal() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-NODRIVE-ZONE-001] Egress-scale steps alone are just a pedestrian: without an
            // in-session vehicular signal (AR vehicle-exit or a credible raw driving fix) nothing
            // ties the walk to a drive — a stale seeded arm plus a stroll must never plant a zone.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedByVehicleEnter(30_000L))
            }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 12f, timestamp = 0L, speed = 4f)) // sub-trip-speed roll
            nowMs = 10_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // stop → anchor
            env.stepDetector.emitSteps(12)
            nowMs = 40_000L
            locations.emit(GpsPoint(40.0010, -3.7, accuracy = 8f, timestamp = 0L, speed = 0.5f)) // ~55 m walk
            assertEquals(1, env.notification.parkingConfirmationCallCount, "weak evidence must ask first")

            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.0010, -3.7, accuracy = 8f, timestamp = 0L, speed = 0.1f))

            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "a walk with no vehicular signal must never plant a zone")
            assertEquals(1, env.notification.markParkingNudgeCallCount, "the user must be asked where the car is")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_unattended_no_drive", ended.outcome, "[DET-NODRIVE-ZONE-001]")
        }

    @Test
    fun should_keep_kerb_anchor_when_user_walks_away_immediately_after_parking() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-AR-FIRST-001 F3] The Camelias regression (field 2026-07-10 15:54): park, exit
            // the car after only 3 steps, walk off at 2.6 m/s — the old rule cleared the
            // unlocked anchor on that first ambiguous fix and the pin re-anchored wherever the
            // pedestrian ended up (inside the house). Steps discriminate person vs car: the
            // displacement never outruns the counted steps, so the kerb anchor must survive and
            // the steps+egress confirm must save AT THE KERB.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            val kerbLat = 40.001
            locations.emit(GpsPoint(kerbLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park → anchor
            env.stepDetector.emitSteps(3) // door slam + first steps, stop still alive
            // Brisk walk-away: ambiguous band (≥ clearBestStopSpeedMps, < real driving), good
            // accuracy — the fix that used to WIPE the anchor. 3 steps cover ~5 m: HOLD.
            locations.emit(GpsPoint(40.00105, -3.7, accuracy = 15f, timestamp = 0L, speed = 2.6f))
            // The walk continues; steps keep counting even though GPS reads movement (the
            // counting gate feeds the discriminator during the walk).
            env.stepDetector.emitSteps(6) // total 9 ≥ minStepsToConfirm
            // ~28 m from the kerb at walking pace → steps+egress confirm fires.
            locations.emit(GpsPoint(40.00125, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "steps+egress must confirm")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(kerbLat, saved.location.latitude, 0.00005, "pin must stay at the kerb anchor, not follow the walker")
        }

    @Test
    fun should_flush_phantom_jam_steps_when_displacement_outruns_them() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-AR-FIRST-001 F3] The jam guard the discriminator must NOT break: phone jiggle
            // at a jam stop counts 2 phantom steps; the car then creeps on at 3 m/s (below real
            // driving). Displacement outruns what 2 steps could walk → CAR: anchor cleared AND
            // steps flushed, so the next genuine stop re-anchors clean and confirms THERE.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // jam stop → anchor
            env.stepDetector.emitSteps(2) // phone jiggle
            // Jam creeps on: ambiguous band, displacement 30 m then 60 m — outruns 2 steps.
            locations.emit(GpsPoint(40.00127, -3.7, accuracy = 10f, timestamp = 0L, speed = 3f))
            locations.emit(GpsPoint(40.00164, -3.7, accuracy = 10f, timestamp = 0L, speed = 3f))
            // Real park 100 m later.
            val plazaLat = 40.0025
            locations.emit(GpsPoint(plazaLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8) // real exit
            locations.emit(GpsPoint(40.00275, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f)) // egress ~28 m

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount)
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(plazaLat, saved.location.latitude, 0.00005, "pin must anchor at the real plaza, not the jam stop")
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-ANCHOR-FREEZE-001: the end-of-drive anchor on mute-step-counter devices
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_pin_the_frozen_anchor_when_a_stepless_walk_drags_to_a_later_stop() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-07-11 20:46 (Redmi): drive → 78-s stop AT the car → walk home with a
            // MUTE step counter (zero steps the whole way) → stand at the front door → prompt
            // ignored → unattended save. The unlocked anchor followed the walker and the pin
            // landed at the door, 95 m from the car. The matured end-of-drive stop must FREEZE
            // the anchor: the walk (including reposition-signature bursts) cannot move it, the
            // later stop cannot re-capture it, and the timeout save pins the CAR.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations) // drive
            val carLat = 40.001
            nowMs = 1_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park
            nowMs = 1_000L + config.anchorFreezeStopMs + 1_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // stop matures → FROZEN
            // Stepless walk home, including three consecutive reposition-signature fixes
            // (≥ repositionSpeedMps, tight accuracy) that used to clear an unlocked anchor.
            locations.emit(GpsPoint(40.0013, -3.7, accuracy = 10f, timestamp = 0L, speed = 2.0f))
            locations.emit(GpsPoint(40.0016, -3.7, accuracy = 10f, timestamp = 0L, speed = 2.0f))
            locations.emit(GpsPoint(40.0019, -3.7, accuracy = 10f, timestamp = 0L, speed = 2.0f))
            val doorLat = 40.0021
            nowMs += 60_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // front door
            nowMs += config.slowPathGateMs + 5_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs += config.lowNotifTimeoutMs + 5_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "prompt must be shown")
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "a pinned anchor authorises the unattended save")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(carLat, saved.location.latitude, 0.00005, "pin must stay at the frozen end-of-drive anchor, not the front door")
        }

    @Test
    fun should_confirm_kinematically_when_stepless_walk_leaves_the_frozen_anchor() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-KINEMATIC-EGRESS-001] Field 2026-07-11 (Redmi), the outcome it should have
            // had: drive → stop matures at the car (anchor FROZEN) → the user walks home with a
            // MUTE step counter. The frozen anchor watches a sustained quality walk away — that
            // GPS-measured egress must confirm the park AT THE ANCHOR within seconds, at the
            // kinematic reliability tier, instead of waiting 15 minutes for the 0.5 timeout save.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations) // drive
            val carLat = 40.001
            nowMs = 1_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park
            nowMs = 1_000L + config.anchorFreezeStopMs + 1_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // FROZEN
            // Stepless walk home: quality pedestrian-band fixes, ~11 m apart.
            var lat = carLat
            repeat(config.kinematicEgressMinWalkFixes) {
                lat += 0.0001
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = 0L, speed = 1.3f))
            }

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "kinematic egress must confirm")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(carLat, saved.location.latitude, 0.00005, "pin at the frozen anchor, not along the walk")
            assertEquals(
                config.reliabilityKinematicEgress,
                saved.detectionReliability ?: 0f,
                /* absoluteTolerance = */ 0.0001f,
                "kinematic tier, distinguishable in forensics [DET-KINEMATIC-EGRESS-001]",
            )
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_kinematic+egress", ended.outcome)
        }

    @Test
    fun should_refine_the_anchor_past_the_initial_window_while_no_step_is_counted() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-07-11 (Redmi, Avenida Sanlúcar): the stop began during the final
            // approach drift — the 30-s window froze the anchor on a 20-m fix 260 m short of the
            // spot, while the real-spot 9-m fix arrived at second 71 of the SAME stop. Until a
            // step is counted every fix of the stop is still the parked car: refinement must
            // stay open for the whole stop.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            val approachLat = 40.001
            val carLat = 40.0028
            nowMs = 1_000L
            locations.emit(GpsPoint(approachLat, -3.7, accuracy = 20f, timestamp = 0L, speed = 0f)) // drift fix
            nowMs = 1_000L + config.initialStopWindowMs + 15_000L // past the 30-s window, same stop
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 8f, timestamp = 0L, speed = 0f)) // real spot, better fix
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 8f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount)
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(carLat, saved.location.latitude, 0.00005, "the better same-stop fix must refine the anchor past the initial window")
        }

    @Test
    fun should_freeze_anchor_by_stable_fixes_on_a_short_trip_before_the_60s_timer() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-SHORT-TRIP-FREEZE-001] Field 2026-07-12 (Oppo, Durango→Glorieta ~2 min): the
            // destination stop never lasted 60 s before the user walked off, so the anchor never
            // froze and the park was lost. With freeze-by-evidence, N dense stopped fixes (~15 s)
            // mature the anchor WELL BEFORE anchorFreezeStopMs (60 s); the stepless walk then
            // confirms kinematically AT the anchor. All timestamps stay under 60 s to prove it is
            // the EVIDENCE path, not the timer.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations) // drive
            val carLat = 40.001
            // Stopped fixes at HIGH_ACCURACY cadence: one to open the stop + anchorFreezeStableFixes
            // more. The freeze fires on the fix whose PRIOR stopped-fix count reaches the threshold.
            repeat(config.anchorFreezeStableFixes + 1) {
                nowMs += 5_000L // 5s, 10s, 15s, 20s … all << anchorFreezeStopMs (60s)
                locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            }
            // Stepless walk home: quality pedestrian-band fixes away from the (now frozen) anchor.
            var lat = carLat
            repeat(config.kinematicEgressMinWalkFixes) {
                lat += 0.0001
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = 0L, speed = 1.3f))
            }

            job.cancelAndJoin()

            assertTrue(nowMs < config.anchorFreezeStopMs, "sanity: whole trace stays under the 60 s timer")
            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "evidence-frozen anchor + kinematic egress must confirm the short trip")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(carLat, saved.location.latitude, 0.00005, "pin at the evidence-frozen anchor")
        }

    @Test
    fun should_not_freeze_anchor_when_the_stopped_fixes_keep_covering_ground() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-STOP-MUST-BE-STILL-IN-SPACE-001] Field 2026-08-22 (Oppo, Camelias→Góndola): three
            // fixes DECLARING 0.0 m/s, 122 m apart across 9.6 s with 6–11 m envelopes, matured the
            // stop and froze the anchor in the side-street mouth — the pin landed 70 m short of the
            // spot while the car was still rolling in. Declared speed is not position: a stop whose
            // own track covers car-grade ground is not a stop.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations)
            // The lying stretch: every fix says 0 m/s, each sits ~55 m past the last (0.0005 lat at this
            // latitude ≈ 55.6 m) — far outside the joint 10+10 m envelopes plus the hop margin, at
            // ~11 m/s of measured ground. Three of them, the freeze threshold, and none may count.
            val mouthLat = 40.001
            repeat(config.anchorFreezeStableFixes) {
                nowMs += 5_000L
                locations.emit(GpsPoint(mouthLat + 0.0005 * it, -3.7, accuracy = 10f, timestamp = nowMs, speed = 0f))
            }
            // The real spot, 55 m further on: a stop that holds its position, with better fixes.
            val spotLat = mouthLat + 0.0005 * config.anchorFreezeStableFixes
            repeat(config.anchorFreezeStableFixes + 1) {
                nowMs += 5_000L
                locations.emit(GpsPoint(spotLat, -3.7, accuracy = 5f, timestamp = nowMs, speed = 0f))
            }
            // Stepless walk away — kinematic egress confirms at whatever the anchor turned out to be.
            var lat = spotLat
            repeat(config.kinematicEgressMinWalkFixes) {
                lat += 0.0001
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = nowMs, speed = 1.3f))
            }

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the park must still be detected")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                spotLat, saved.location.latitude, 0.00005,
                "the pin belongs at the stop that held its position, not at the first fix that merely claimed 0 m/s",
            )
        }

    @Test
    fun should_still_freeze_anchor_when_a_stopped_car_drifts_inside_its_accuracy_envelope() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-STOP-MUST-BE-STILL-IN-SPACE-001] The other side of the same bar: a genuinely
            // parked car in an urban canyon wanders between fixes, and that must stay a stop. Field
            // 2026-08-22 (Redmi, Camelias): 4–20 m of wander against 15–59 m envelopes froze
            // correctly and the pin was right. Refutation takes BOTH disjoint envelopes and
            // car-grade ground rate, so drift like this never trips it.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations)
            // ~11 m of wander per fix (0.0001 lat) against 30+30 m envelopes: inside the noise, and
            // 2.2 m/s of ground — under the car-grade bar. A stop, and it must mature as one.
            val carLat = 40.001
            repeat(config.anchorFreezeStableFixes + 1) {
                nowMs += 5_000L
                locations.emit(GpsPoint(carLat + 0.0001 * it, -3.7, accuracy = 30f, timestamp = nowMs, speed = 0f))
            }
            var lat = carLat + 0.001
            repeat(config.kinematicEgressMinWalkFixes) {
                lat += 0.0001
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = nowMs, speed = 1.3f))
            }

            job.cancelAndJoin()

            assertEquals(
                1, env.parkingRepo.saveNewParkingSessionCallCount,
                "GPS wander inside the accuracy envelopes must still mature the stop and confirm the park",
            )
        }

    @Test
    fun should_not_spend_refuted_stillness_as_time_credit_toward_the_anchor_freeze() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001] Field 2026-08-28 (Redmi, house
            // FP): a stop opened mid-route on network fixes was refuted FOUR times — and still
            // matured by TIME, because the maturity clock kept the credit of stillness the track
            // had already disproven. Here: 60 s of refuted creep, then a real stop whose UNREFUTED
            // run is only 5 s old when the walk starts. The freeze may not fire on the poisoned
            // clock, so the stepless walk finds no frozen anchor and nothing confirms silently.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations)
            // Refuted creep: every fix claims 0 m/s while hopping ~55 m every 20 s (2.8 m/s of
            // measured ground, far outside the 10+10 m envelopes). The stop's WALL clock passes
            // anchorFreezeStopMs (60 s) inside this stretch.
            val mouthLat = 40.001
            nowMs = 45_000L
            locations.emit(GpsPoint(mouthLat, -3.7, accuracy = 10f, timestamp = nowMs, speed = 0f))
            repeat(3) {
                nowMs += 20_000L
                locations.emit(GpsPoint(mouthLat + 0.0005 * (it + 1), -3.7, accuracy = 10f, timestamp = nowMs, speed = 0f))
            }
            // The real spot — but its unrefuted stillness is only 5 s old when the walk begins.
            val spotLat = mouthLat + 0.0015
            nowMs += 5_000L
            locations.emit(GpsPoint(spotLat, -3.7, accuracy = 8f, timestamp = nowMs, speed = 0f))
            var lat = spotLat
            repeat(config.kinematicEgressMinWalkFixes) {
                lat += 0.0001
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = nowMs, speed = 1.3f))
            }
            job.cancelAndJoin()

            assertEquals(
                0, env.parkingRepo.saveNewParkingSessionCallCount,
                "refuted stillness spent as time credit is how the mid-route anchor froze on " +
                    "2026-08-28 — 5 s of unrefuted rest may not freeze, so nothing confirms silently",
            )
        }

    @Test
    fun should_disown_an_anchor_captured_from_fixes_the_stop_later_refuted() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001] The capture half of the same
            // invariant. Field 2026-08-28: the anchor stuck to the stop-OPENING fix (19.75 m — it
            // beat every later fix on accuracy) through four refutations, 3.5 km from the park.
            // A refutation proves the car was still MOVING through the fixes the anchor came from,
            // so the anchor is disowned and the best-accuracy contest restarts among fixes the
            // track has not contradicted. Here the mouth fix is the sharpest of the whole stream
            // (4 m): without the disown it would own the pin forever.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations)
            val mouthLat = 40.001
            nowMs = 45_000L
            locations.emit(GpsPoint(mouthLat, -3.7, accuracy = 4f, timestamp = nowMs, speed = 0f))
            // The refuting hop: ~55 m past the mouth in 20 s while claiming 0 m/s — the car was
            // still rolling; the 4 m mouth anchor dies with the refuted stillness.
            val spotLat = mouthLat + 0.0005
            nowMs += 20_000L
            locations.emit(GpsPoint(spotLat, -3.7, accuracy = 10f, timestamp = nowMs, speed = 0f))
            // The car's true rest: enough stopped fixes to freeze by quorum, none sharper than 6 m.
            repeat(config.anchorFreezeStableFixes + 1) {
                nowMs += 5_000L
                locations.emit(GpsPoint(spotLat, -3.7, accuracy = 6f, timestamp = nowMs, speed = 0f))
            }
            var lat = spotLat
            repeat(config.kinematicEgressMinWalkFixes) {
                lat += 0.0001
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = nowMs, speed = 1.3f))
            }
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the real park must confirm")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                spotLat, saved.location.latitude, 0.00005,
                "the pin belongs at the rest the track never refuted, not at the sharp fix the car " +
                    "provably rolled through",
            )
        }

    @Test
    fun should_demote_an_inferred_confirm_to_a_zone_when_its_fix_cannot_carry_an_exact_claim() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-INFERRED-PIN-CARRIES-ITS-DOUBT-001] Field 2026-08-28, second half: with the
            // mid-route anchor gone, steps+egress re-anchored on a 92.9 m network fix — and saved
            // it as an EXACT pin at 0.9, the field FP's shape one street over. An inferred confirm
            // may not claim more precision than the fix it stands on: past the honest-zone floor
            // (60 m) the save is an AREA of the fix's own accuracy. The dozens of exact-pin
            // assertions elsewhere in this suite are the control: sharp anchors stay exact points.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations)
            val carLat = 40.001
            repeat(config.anchorFreezeStableFixes + 1) {
                nowMs += 5_000L
                locations.emit(GpsPoint(carLat, -3.7, accuracy = 90f, timestamp = nowMs, speed = 0f))
            }
            var lat = carLat
            repeat(config.kinematicEgressMinWalkFixes) {
                lat += 0.0001
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = nowMs, speed = 1.3f))
            }
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the park itself is not in doubt")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertTrue(
                saved.isApproximate,
                "a 90 m fix cannot carry an exact claim — the save must draw its doubt as an area",
            )
            assertEquals(
                90f, saved.zoneRadiusMeters,
                "the area is the fix's own accuracy: never less doubt than the evidence measured",
            )
        }

    @Test
    fun should_save_approximate_zone_when_unattended_timeout_finds_an_unpinned_anchor() =
        runTest(UnconfinedTestDispatcher()) {
            // Measured driving happened, but no stop matured and no egress steps sealed anything:
            // by timeout the anchor is wherever the body last stood — a guess. [DET-FROZEN-COUNTER-001]
            // The honest exit keeps the park as an APPROXIMATE ZONE covering the doubt (a real
            // drive ended near the evidence) instead of losing it to a nudge nobody sees
            // (field 2026-07-25/26, Redmi: 92 driving fixes, no saved parking).
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations) // drive
            nowMs = 1_000L
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // brief stop
            nowMs = 11_000L // 10 s — far below anchorFreezeStopMs
            locations.emit(GpsPoint(40.0013, -3.7, accuracy = 10f, timestamp = 0L, speed = 1.2f)) // stepless walk
            val homeLat = 40.0018
            nowMs += 60_000L
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // home
            // A LIVE counter (a few steps, below the anchor-lock threshold) — the walk bound that
            // makes the fallback zone honest. [DET-FROZEN-COUNTER-001]
            env.stepDetector.emitSteps(3)
            nowMs += config.slowPathGateMs + 5_000L
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs += config.lowNotifTimeoutMs + 5_000L
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "prompt must be shown")
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the park must be KEPT as a zone, not lost")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertTrue(saved.isApproximate, "an unpinned anchor may only be saved as an AREA, never an exact pin")
            assertTrue(saved.zoneRadiusMeters!! >= config.honestCloseMinZoneRadiusMeters)
            assertEquals(config.reliabilityUnattendedSave, saved.detectionReliability, "never community-published")
            assertEquals(0, env.notification.markParkingNudgeCallCount, "the saved-parking card is the ask — no extra nudge")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_unattended_zone_unpinned_anchor", ended.outcome, "[DET-ANCHOR-FREEZE-001][DET-FROZEN-COUNTER-001]")
        }

    @Test
    fun should_never_auto_confirm_a_candidate_without_steps() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-SOLID-001 C3 finding] The "vehicleExit+window+egress" decision branch is
            // STRUCTURALLY UNREACHABLE through the real loop: with activityExit=true the scorer
            // takes the fast path (ceiling Medium), so a Candidate can only ever open with
            // hadVehicleExit=false — whose window (5 min) then requires steps to confirm. This
            // test pins the REAL end-to-end behaviour: a stepless Candidate is prompted, dies
            // Rejected at the window, and the ignored prompt resolves via the unattended save
            // (low reliability) — egress without steps NEVER silently AUTO-saves; the human
            // window always runs first. [DET-RECONCILE-001]
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations) // drive
            nowMs = 10_000L
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park
            nowMs = 10_000L + config.slowPath5MinMs + 1_000L
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 10f, timestamp = 0L, speed = 0.1f)) // High → Candidate
            assertEquals(1, env.notification.parkingConfirmationCallCount, "candidate must prompt")
            // Egress ~33 m at walking pace, but NO steps ever counted (phone left in the car).
            nowMs += 10_000L
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))
            // The (no-exit) 5-min observation window elapses → candidate Rejected, falls to Notified.
            nowMs += config.confirmationObservationWindowMs + 1_000L
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "stepless egress must never AUTO-save")
            // Nobody answers the prompt → [DET-RECONCILE-001] the timeout saves unattended with
            // low reliability (a real trip + 5-min stop happened; discarding it loses the car).
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "ignored prompt saves unattended")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_unattended_timeout", ended.outcome)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-SOLID-001 B3/B4: weak-evidence prompt + enter-arm step veto
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_prompt_instead_of_saving_when_enter_only_arm_never_sees_driving() =
        runTest(UnconfinedTestDispatcher()) {
            // [B3] ENTER-only evidence (bus/taxi-falsifiable) + no driving observed by the stream
            // → all confirm conditions hold but the coordinator must ASK, not save.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedByVehicleEnter(enterToExitMs = 30_000L))
            }

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 2.8f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "ENTER-only evidence must never save silently [DET-SOLID-001 B3]",
            )
            assertEquals(
                1,
                env.notification.parkingConfirmationCallCount,
                "the user must be asked instead",
            )

            // A user "Sí" then saves at reliability 1.0 (guards bypassed by user confirmation).
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            job.cancelAndJoin()
            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "user tap completes the save")
        }

    @Test
    fun should_veto_enter_arm_when_first_step_arrives_immediately() =
        runTest(UnconfinedTestDispatcher()) {
            // [B4] Veto ON: a VerifiedByVehicleEnter arm whose FIRST step lands right after the
            // arm (no driving seen) is a spurious walking ENTER — evidence degrades and the
            // false-ENTER abort re-arms, so the walking burst aborts with no save AND no prompt.
            val env = setup(config = config.copy(enterArmStepVetoMs = 15_000L))
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedByVehicleEnter(enterToExitMs = 30_000L))
            }

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))
            env.stepDetector.emitSteps(1)
            assertFalse(
                env.coordinator.hasDetectedMovement,
                "immediate first step must degrade the ENTER evidence and un-seed [DET-SOLID-001 B4]",
            )
            env.stepDetector.emitSteps(7)
            locations.emit(GpsPoint(40.0051, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))

            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "vetoed session saves nothing")
            assertEquals(0, env.notification.parkingConfirmationCallCount, "vetoed session prompts nothing")
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-SOLID-001: a driving-speed crossing needs a credible-accuracy fix
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_not_trust_driving_speed_from_a_degraded_accuracy_fix() =
        runTest(UnconfinedTestDispatcher()) {
            // A single walking GPS spike (speed 6 m/s but accuracy 120 m) used to flip
            // hasEverReachedDrivingSpeed and unlock every confirm path — the GPS-noise variant
            // of the walking false positive. The 50 m credibility gate must reject it, so the
            // subsequent step burst still aborts the session. [DET-SOLID-001]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 120f, timestamp = 0L, speed = 6f))
            assertFalse(
                env.coordinator.hasDetectedMovement,
                "degraded fix must not count as driving [DET-SOLID-001]",
            )

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "the walking burst must still abort — the spike opened no confirm path",
            )
        }

    @Test
    fun should_trust_driving_speed_from_a_credible_accuracy_fix() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 20f, timestamp = 0L, speed = 6f))
            assertTrue(env.coordinator.hasDetectedMovement, "credible fix keeps the normal path")

            job.cancelAndJoin()
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-SHORT-HOP-PROOF-001: a short hop proves its drive by DISPLACEMENT
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_keep_the_park_when_a_short_hop_proves_its_drive_by_displacement_from_the_pin() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-08-14 22:56 (Oppo, session 1786740987649): punctual VERIFIED exit, 303
            // fixes, peak 30 km/h, and the car ended 900 m from its pin — yet `drive 3/303`: the
            // sparse stop-and-go stream never held ~150 m inside any single 20-60 s look-back
            // window, so `corroboratesDrive` never latched, `maxSpeedMps` stayed 0, and the
            // unattended timeout read "no measured driving" and threw the real park away with a
            // nudge nobody answered. The displacement from the PIN is the proof that hop offers.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val pin = GpsPoint(40.0, -3.7, accuracy = 8f, timestamp = 0L, speed = 0f)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedBySpeed(speedKmh = 30f, accuracyM = 10f),
                    departureAnchor = pin,
                    departureFenceRadiusMeters = 80f,
                )
            }

            driveSparseShortHop(locations) { nowMs = it }
            // Weak arrival evidence on purpose (no egress-scale steps, no AR vehicle-exit): the
            // session can only end through the unattended timeout — the exact branch the field FN
            // died in. With the drive PROVEN it saves the park instead of nudging it away.
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(SHORT_HOP_PARKED_LAT, -3.7, accuracy = 6f, timestamp = nowMs, speed = 0f))
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(SHORT_HOP_PARKED_LAT, -3.7, accuracy = 6f, timestamp = nowMs, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "a 900 m hop away from the pin IS measured driving — the park must not be lost [DET-SHORT-HOP-PROOF-001]",
            )
            val ended = env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertTrue(
                ended.outcome != "aborted_unattended_no_drive",
                "the field FN outcome must be gone, was ${ended.outcome}",
            )
            assertEquals(0, env.notification.markParkingNudgeCallCount, "a proven drive never degrades to the mark-your-spot nudge")
        }

    @Test
    fun should_not_prove_a_drive_by_displacement_when_nothing_measured_a_departure() =
        runTest(UnconfinedTestDispatcher()) {
            // Anti-resurrection: geometry ALONE still proves nothing. Same displacement, same
            // unverified arm — but every fix reports pedestrian speed, so neither the arm nor the
            // stream ever witnessed the car leaving. A long walk, a passenger ride and a sparse
            // stream all draw this shape. Doctrine: rather a false negative.
            // [DET-SHORT-HOP-PROOF-001][DET-UNVERIFIED-ARM-DRIVE-PROOF-001]
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val pin = GpsPoint(40.0, -3.7, accuracy = 8f, timestamp = 0L, speed = 0f)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.Unverified,
                    departureAnchor = pin,
                    departureFenceRadiusMeters = 80f,
                )
            }

            driveSparseShortHop(locations, speedMps = 1.2f) { nowMs = it }
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(SHORT_HOP_PARKED_LAT, -3.7, accuracy = 6f, timestamp = nowMs, speed = 0f))
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(SHORT_HOP_PARKED_LAT, -3.7, accuracy = 6f, timestamp = nowMs, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "displacement with no measured departure must never silently pin [DET-SHORT-HOP-PROOF-001]",
            )
        }

    @Test
    fun should_keep_the_park_when_the_stream_itself_measured_the_departure_from_the_pin() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-08-15 21:26 (Redmi, session 1786821963745): MIUI never delivered the
            // geofence EXIT, so a sentry-wake armed the session `self_observed` with the car already
            // ~1.1 km from home. The stream caught only the drive's tail — three credible fixes at
            // 25-30 km/h inside 10 s, far too short a span for any 20-60 s look-back window, so
            // `corroboratesDrive` never latched. The displacement proof would have saved it, but it
            // was gated on the ARM being verified, so it never ran: `aborted_unattended_no_drive`, a
            // nudge nobody answered, and the park lost — followed by the return trip, which had no
            // pin left to arm from. The other phone, whose EXIT arrived on time, pinned both.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val pin = GpsPoint(40.0, -3.7, accuracy = 8f, timestamp = 0L, speed = 0f)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.Unverified,
                    departureAnchor = pin,
                    departureFenceRadiusMeters = 80f,
                )
            }

            driveLateArmedHop(locations) { nowMs = it }
            // Weak arrival evidence on purpose: the session can only end through the unattended
            // timeout — the exact branch the field park died in.
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(LATE_ARM_PARKED_LAT, -3.7, accuracy = 6f, timestamp = nowMs, speed = 0f))
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(LATE_ARM_PARKED_LAT, -3.7, accuracy = 6f, timestamp = nowMs, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "the stream measured the departure the EXIT never reported — the park must survive [DET-UNVERIFIED-ARM-DRIVE-PROOF-001]",
            )
            val ended = env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertTrue(
                ended.outcome != "aborted_unattended_no_drive",
                "the field FN outcome must be gone, was ${ended.outcome}",
            )
            assertEquals(0, env.notification.markParkingNudgeCallCount, "a proven drive never degrades to the mark-your-spot nudge")
        }

    @Test
    fun should_not_pin_when_a_sentry_wake_arms_after_the_user_already_walked_away() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-08-16 23:52 (Oppo, session 1786917152243) — the FALSE POSITIVE that names
            // DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001. The user walked ~990 m from the previous pin to
            // the seafront. The significant-motion sensor fired there, arming a session
            // `self_observed` with the whole walk already banked, so `elapsedSinceArmMs` started at
            // zero and `isBeyondPedestrianReach` read a kilometre covered "in 15 s". Three
            // consecutive far-but-stationary fixes then satisfied the displacement proof, which
            // unlocked `maxSpeedMps` off the single 42 km/h Doppler spike the receiver emits as it
            // converges out of a cold start — and with `sessionSawDriving` true the `self_observed`
            // weak-evidence guard stood down and 220 walking steps silently pinned the beach.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val pin = GpsPoint(40.0, -3.7, accuracy = 8f, timestamp = 0L, speed = 0f)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.Unverified,
                    departureAnchor = pin,
                    departureFenceRadiusMeters = 80f,
                )
            }

            // Cold start ~990 m from the pin: two coarse fixes, then the convergence spike.
            val seafrontLat = 40.0089
            locations.emit(GpsPoint(seafrontLat, -3.7, accuracy = 100f, timestamp = 0L, speed = 0f))
            nowMs = 5_000L
            locations.emit(GpsPoint(seafrontLat, -3.7, accuracy = 100f, timestamp = nowMs, speed = 0f))
            nowMs = 10_000L
            locations.emit(GpsPoint(seafrontLat, -3.7, accuracy = 11.5f, timestamp = nowMs, speed = 11.7f))
            // …and from here on, a person on foot. Nothing else in the session ever moves faster.
            nowMs = 13_000L
            locations.emit(GpsPoint(seafrontLat, -3.7, accuracy = 20f, timestamp = nowMs, speed = 0.6f))
            nowMs = 25_000L
            locations.emit(GpsPoint(seafrontLat, -3.7, accuracy = 9f, timestamp = nowMs, speed = 0.3f))
            env.stepDetector.emitSteps(220) // the walk along the promenade
            nowMs = 190_000L
            locations.emit(GpsPoint(40.00995, -3.7, accuracy = 4f, timestamp = nowMs, speed = 0.9f))

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "a walk-out must never confirm silently [DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001]",
            )

            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.00995, -3.7, accuracy = 4f, timestamp = nowMs, speed = 0.1f))
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.00995, -3.7, accuracy = 4f, timestamp = nowMs, speed = 0.1f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "no measured driving anywhere in this session — nothing may be pinned",
            )
            val ended = env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertTrue(
                ended.outcome != "confirmed_steps+egress",
                "the field FP outcome must be gone, was ${ended.outcome}",
            )
        }

    @Test
    fun should_not_save_anything_when_the_ride_was_human_powered() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-08-16 11:08Z (Samsung SM-A536B, session 1786878499475). A 59-minute
            // bicycle ride to Los Toruños broke the car's own geofence at 352 m, was sealed
            // `verified_speed` (38 km/h clears the 10 km/h bar effortlessly), and ended by planting
            // `unattended_zone_unpinned_anchor` 4,8 km from a Mercedes that never moved. Note where
            // it landed: NOT on an auto-confirm path but on the unattended timeout, which is why the
            // veto has to cover both. [DET-BIKE-NOT-A-CAR-001]
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedBySpeed(38f, 12f))
            }

            // Android's classifier says bicycle. Nothing else in the session can tell.
            env.coordinator.onHumanPoweredRide(nowMs)
            // A cyclist's band: it clears every threshold calibrated for a pedestrian, which is the
            // whole danger, and stays where the field rides measured. [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001]
            emitCorroboratedDrive(locations, speedMps = BICYCLE_SPEED_MPS)
            nowMs = 60_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(12)
            // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] No AR exit — the fixture used to inject one as
            // a shortcut into the confirm path, but its own field source (session 1786878499475)
            // recorded ZERO AR transitions, and an `IN_VEHICLE EXIT` now proves the boarding it
            // followed. Asserting a bicycle while feeding it a vehicle exit tested nothing real.
            nowMs = 90_000L
            locations.emit(GpsPoint(40.0010, -3.7, accuracy = 8f, timestamp = 0L, speed = 0.5f))

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "a bicycle ride must never confirm silently [DET-BIKE-NOT-A-CAR-001]",
            )

            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.0010, -3.7, accuracy = 8f, timestamp = 0L, speed = 0.1f))
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.0010, -3.7, accuracy = 8f, timestamp = 0L, speed = 0.1f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "…and must not buy an approximate zone through the timeout either — the car never moved",
            )
            val ended = env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_unattended_human_powered", ended.outcome)
        }

    @Test
    fun should_close_the_session_at_the_matured_stop_when_the_ride_was_human_powered() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-08-19 22:32 (Oppo session 1787171533976, Redmi 1787171592952): a bicycle
            // ride home, phone left on a table — no egress steps, so the candidate phase could
            // never have confirmed anything. It did not: CANDIDATE opened and expired three times
            // (22:50 → 22:55 → 23:00, each discard zeroing stepCount) until the 15-minute response
            // timeout finally read the human-powered flag that had been true since the ride itself.
            // 19 minutes of foreground service and 2-5 s GPS for a verdict available at the first
            // matured stop. [DET-HUMAN-POWERED-EARLY-CLOSE-001]
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified)
            }

            env.coordinator.onHumanPoweredRide(nowMs)
            // A bicycle holds the DRIVING band as well as a car does — it just cannot hold the
            // motor band. [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001]
            emitCorroboratedDrive(locations, speedMps = BICYCLE_SPEED_MPS)

            // Home. The bike is left; the phone stops moving. No steps, no AR exit — nothing that
            // could ever satisfy the egress conjunction.
            nowMs = 60_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 4f, timestamp = 0L, speed = 0f))
            // The stop matures past the 5-minute tier: the only route to High confidence, and the
            // measured fact that says "the ride is over".
            nowMs += config.slowPath5MinMs + 30_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 4f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val ended = env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals(
                "aborted_unattended_human_powered",
                ended.outcome,
                "the session must end at the matured stop, not at the response timeout",
            )
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount)
            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.Decision>()
                    .none { it.outcome == "PROMPT_SHOWN" },
                "a ride known to be muscle-powered must never be asked '¿has aparcado?'",
            )
        }

    @Test
    fun should_record_the_cycling_stamp_in_the_trace_with_how_stale_it_already_was() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C] The 2026-08-20 session logged 1 476 events
            // and not one named the `ON_BICYCLE` stamp that decided it — the veto was only
            // reachable by elimination, days later. The AR EVIDENCE lane belongs in the trace, with
            // the TRUE transition time it is arbitrated on (AR delivers up to ~2 min late).
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            env.coordinator.onHumanPoweredRide(atMs = -90_000L) // stamped 90 s before this fix
            nowMs = 0L
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            // A second fix must not re-log the same stamp: this is an edge, not a heartbeat.
            nowMs = 5_000L
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))

            job.cancelAndJoin()

            val cycling = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.ActivityTransition>()
                .filter { it.activity == "ON_BICYCLE" }
            assertEquals(1, cycling.size, "edge-logged exactly once, was ${cycling.size}")
            assertEquals("ENTER", cycling.single().transition)
            assertEquals(
                90_000L,
                cycling.single().trueTimeAgeMs,
                "the trace must carry how stale AR's answer already was",
            )
        }

    /**
     * [DET-THREE-EDGE-MARKERS-CANNOT-GO-SILENT-001] The EXIT edge, BOTH halves of it.
     *
     * `should_log_vehicle_exit_transition_in_trace` above asserts `any { … }` — that at least one
     * EXIT reached the trace. That witnesses total silence and nothing else: an edge that regressed
     * into a heartbeat (one line per fix, the noise the marker exists to prevent) passes it, and so
     * does an edge that never re-arms. Both are the failure, and neither was covered.
     */
    @Test
    fun should_log_the_vehicle_exit_once_per_departure_and_again_after_the_car_drives_off() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            env.coordinator.onVehicleExit(atMs = 0L)
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            // The hint is still set on the next fix. An edge says nothing; a heartbeat repeats.
            nowMs = 5_000L
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))

            // The car leaves again: a driving fix clears the hint (EgressEvidence.onFix), which is
            // what re-arms the marker — the reason it is a flag reset and not a latch.
            nowMs = 10_000L
            locations.emit(GpsPoint(40.0027, -3.7, accuracy = 5f, timestamp = 10_000L, speed = 15f))
            env.coordinator.onVehicleExit(atMs = 12_000L)
            nowMs = 15_000L
            locations.emit(stationaryFix(lat = 40.0027, lon = -3.7))

            job.cancelAndJoin()

            val exits = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.ActivityTransition>()
                .filter { it.activity == "IN_VEHICLE" && it.transition == "EXIT" }
            assertEquals(
                2,
                exits.size,
                "one line per departure — not one per fix, and not one for the whole session; was ${exits.size}",
            )
        }

    /**
     * [DET-THREE-EDGE-MARKERS-CANNOT-GO-SILENT-001] The boarding stamp: deduped by VALUE, not
     * latched. It is the counterpart the human-powered verdict is read against — without it the
     * trace shows a cycling veto and no sign of the boarding that should have superseded it — and
     * unlike its `ON_BICYCLE` twin one line above, nothing asserted it at all.
     */
    @Test
    fun should_log_each_distinct_boarding_stamp_once_with_how_stale_it_already_was() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            env.coordinator.onVehicleRide(atMs = -60_000L) // AR delivered it a minute late
            nowMs = 0L
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            // Same stamp on the next fix: the value has not changed, so neither has the trace.
            nowMs = 5_000L
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))

            // A genuinely NEW boarding. Deduping by value is what lets this one through, and it is
            // the property a latch would have swallowed.
            env.coordinator.onVehicleRide(atMs = 20_000L)
            nowMs = 25_000L
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))

            job.cancelAndJoin()

            val boardings = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.ActivityTransition>()
                .filter { it.activity == "IN_VEHICLE" && it.transition == "ENTER" }
            assertEquals(2, boardings.size, "one line per distinct stamp, was ${boardings.size}")
            assertEquals(
                60_000L,
                boardings.first().trueTimeAgeMs,
                "the trace must carry how stale AR's answer already was",
            )
            assertEquals(5_000L, boardings.last().trueTimeAgeMs)
        }

    /**
     * [DET-THREE-EDGE-MARKERS-CANNOT-GO-SILENT-001] The motor refutation: a true latch, and the one
     * marker of the four that carries a VERDICT's name into the trace. If the motor band ever
     * refutes a ride that really was muscle, this is the line that will say so — so a session that
     * crossed the bar and never said it is the whole failure, and repeating it on every subsequent
     * fix buries it in the noise it was written to avoid.
     */
    @Test
    fun should_announce_the_motor_witness_once_when_it_crosses_and_never_again() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            // 15 m/s (54 km/h) is above motorProofSpeedMps (11.1). Fixes 20 s apart, displaced by
            // the ~300 m that speed actually covers, so the band credits each full gap.
            //
            // FIVE fixes, and the count is the point. The band credits the interval BETWEEN two
            // in-band fixes, so the first one banks nothing and the crossing of
            // sustainedDriveProofMs (30 s) lands on the FOURTH: 0, 0, 20 s, 40 s, 60 s. A stream
            // that stops at the crossing cannot tell a latch from a heartbeat — there is no later
            // fix for the heartbeat to speak on — and this test passed against a delatched
            // coordinator until the fifth fix was added.
            val speed = 15f
            listOf(0L, 20_000L, 40_000L, 60_000L, 80_000L).forEachIndexed { index, at ->

                nowMs = at
                locations.emit(
                    GpsPoint(40.0 + 0.0027 * index, -3.7, accuracy = 5f, timestamp = at, speed = speed),
                )
            }

            job.cancelAndJoin()

            val witnessed = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.Decision>()
                .filter { it.outcome == "MOTOR_WITNESSED" }
            assertEquals(1, witnessed.size, "latched once at the crossing, was ${witnessed.size}")
            assertTrue(
                witnessed.single().pathLabel?.contains("motorBand=") == true,
                "the line must carry the band it crossed, not just the fact that it did: " +
                    "${witnessed.single().pathLabel}",
            )
        }

    @Test
    fun should_record_the_pedal_cadence_latch_in_the_trace_even_when_its_second_fix_arrives_late() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C] The OTHER source of the human-powered veto.
            // With the AR lane now traced, a session vetoed by CADENCE still showed nothing at all,
            // so the reader was back to inferring by elimination — the 2026-08-20 Oppo shape.
            //
            // The emission is deliberately the one the previous edge CONCEDED it would miss: the
            // event count crosses the threshold while only ONE distinct fix has been credited, and
            // the second fix (the half that completes the verdict) arrives afterwards. At that
            // moment `events == pedalCadenceMinStepEvents` is already false, so an equality edge
            // stays silent for a latch that is fully held.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            // One credible fix inside the band a bicycle can occupy (≥ egressStepMaxSpeedMps and,
            // since §A, < motorProofSpeedMps), then the whole burst against that single fix.
            nowMs = 1_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 6f, timestamp = 1_000L, speed = BICYCLE_SPEED_MPS))
            nowMs = 2_000L
            env.stepDetector.emitSteps(config.pedalCadenceMinStepEvents) // events = 12, fixes = 1

            val beforeSecondFix = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.Decision>()
                .count { it.outcome == "PEDAL_CADENCE_LATCHED" }
            assertEquals(0, beforeSecondFix, "one fix is one pothole — the latch is not held yet")

            // The second distinct fix, and the step that credits it: now both halves hold.
            nowMs = 5_000L
            locations.emit(GpsPoint(40.0004, -3.7, accuracy = 6f, timestamp = 5_000L, speed = BICYCLE_SPEED_MPS))
            nowMs = 6_000L
            env.stepDetector.emitSteps(1) // events = 13, fixes = 2
            // Still pedalling: the marker is an edge, not a heartbeat.
            env.stepDetector.emitSteps(5)

            job.cancelAndJoin()

            val latched = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.Decision>()
                .filter { it.outcome == "PEDAL_CADENCE_LATCHED" }
            assertEquals(1, latched.size, "edge-logged exactly once, was ${latched.size}")
            assertTrue(
                latched.single().pathLabel!!.contains("fixes=2"),
                "the trace must carry the numbers the verdict weighed, was ${latched.single().pathLabel}",
            )
        }

    @Test
    fun should_trace_the_pedal_strokes_themselves_as_one_rollup_per_credited_fix() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-CADENCE-STEPS-ARE-INVISIBLE-TO-TELEMETRY-001] The test above pins the VERDICT.
            // Its INPUTS reached no lane at all: `Step` is emitted from three branches (pre-drive /
            // stopped / anchor-set) and a step taken while DRIVING with the anchor cleared — which is
            // what `cadenceQualifies` requires — fell through every one of them. Field 2026-08-26's
            // twelve strokes had to be reconstructed by arithmetic before its replay could reproduce
            // the loss, which is the definition of a decision nobody can audit.
            //
            // The emission is a ROLLUP: one event per distinct fix credited, never one per step,
            // because a bicycle pedals continuously.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            nowMs = 1_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 6f, timestamp = 1_000L, speed = BICYCLE_SPEED_MPS))
            nowMs = 2_000L
            env.stepDetector.emitSteps(config.pedalCadenceMinStepEvents) // 12 strokes, all on fix #1

            val afterFirstFix = env.detectionLogger.events.filterIsInstance<DetectionEvent.Cadence>()
            assertEquals(
                1, afterFirstFix.size,
                "twelve strokes on one fix are ONE rollup, not twelve events — was ${afterFirstFix.size}",
            )
            assertEquals(1, afterFirstFix.single().creditedFixes)
            assertEquals(
                BICYCLE_SPEED_MPS, afterFirstFix.single().location?.speed,
                "the event must carry the FIX the concurrency was judged against — that speed is the " +
                    "whole calibration question",
            )

            nowMs = 5_000L
            locations.emit(GpsPoint(40.0004, -3.7, accuracy = 6f, timestamp = 5_000L, speed = BICYCLE_SPEED_MPS))
            nowMs = 6_000L
            env.stepDetector.emitSteps(4) // a second credited fix, three more strokes on it

            job.cancelAndJoin()

            val rollups = env.detectionLogger.events.filterIsInstance<DetectionEvent.Cadence>()
            assertEquals(2, rollups.size, "one rollup per credited FIX, was ${rollups.size}")
            assertEquals(2, rollups.last().creditedFixes)

            // THE PROPERTY THAT MAKES THE ROLLUP WORTH READING, stated exactly. Each event fires on
            // the FIRST stroke credited to a new fix, so its `sessionStepEvents` is the running total
            // INCLUDING that opening stroke — 1, then 13. The burst a fix actually collected is
            // therefore the DELTA to the next event, not the value carried by its own:
            assertEquals(1, rollups.first().sessionStepEvents, "the opening stroke of fix #1")
            assertEquals(
                config.pedalCadenceMinStepEvents + 1, rollups.last().sessionStepEvents,
                "12 strokes on fix #1, then the opening stroke of fix #2",
            )
            assertEquals(
                config.pedalCadenceMinStepEvents,
                rollups.last().sessionStepEvents - rollups.first().sessionStepEvents,
                "the delta recovers fix #1's COMPLETE burst — this is what a calibration reads",
            )
            // …and the honest limit: fix #2's own 4 strokes are not recoverable, because no third
            // fix ever closed them. Documented on the event, and it costs nothing a threshold reads.
        }

    @Test
    fun should_not_trace_a_cadence_rollup_for_a_step_that_did_not_read_as_pedalling() =
        runTest(UnconfinedTestDispatcher()) {
            // The DENOMINATOR must not be laundered into the numerator. A step taken while moving
            // BELOW the pedestrian ceiling is not a pedal stroke, and the remote lane must stay
            // silent for it — otherwise the fraction the calibration ticket needs
            // (`DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001`) is unmeasurable in the other
            // direction. It is still written to `parkdiag`, where the volume is free.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            // Authorise driving, then drop into the walking band without ever stopping: no anchor is
            // born, so these steps take the same fourth branch — and must NOT be credited.
            nowMs = 1_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 6f, timestamp = 1_000L, speed = BICYCLE_SPEED_MPS))
            nowMs = 3_000L
            locations.emit(GpsPoint(40.0002, -3.7, accuracy = 6f, timestamp = 3_000L, speed = 2.5f))
            nowMs = 4_000L
            env.stepDetector.emitSteps(config.pedalCadenceMinStepEvents * 2)

            job.cancelAndJoin()

            val rollups = env.detectionLogger.events.filterIsInstance<DetectionEvent.Cadence>()
            assertTrue(
                rollups.isEmpty(),
                "24 steps at 2,5 m/s are a walk, not a cadence — was ${rollups.size} rollups",
            )
            val latched = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.Decision>()
                .count { it.outcome == "PEDAL_CADENCE_LATCHED" }
            assertEquals(0, latched, "and nothing may be vetoed off them either")
        }

    @Test
    fun should_not_latch_pedal_cadence_on_the_egress_walk_after_the_anchor_is_pinned() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001] Field 2026-08-22 (Redmi,
            // Góndola→Camelias): a 75 km/h car trip with 57 driving fixes was judged human-powered
            // 36 s AFTER the anchor froze, on steps the log itself labelled `egress walk, anchor
            // set` against 4.27 m/s fixes in a narrow street. Once the anchor is pinned the session
            // has already witnessed the car at rest, so this signature is an egress walk on a noisy
            // stream — the expected shape — and may not accuse anyone of pedalling.
            //
            // The speed is the field's own 4.3 m/s: above the cadence floor (egressStepMaxSpeedMps,
            // 3.0) and below the bar that would legitimately unfreeze the anchor
            // (minimumTripSpeedMps, 5.0). The pre-anchor half of this rule keeps its own test above.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            emitCorroboratedDrive(locations)
            val carLat = 40.001
            repeat(config.anchorFreezeStableFixes + 1) {
                nowMs += 5_000L
                locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = nowMs, speed = 0f))
            }
            var lat = carLat
            repeat(4) {
                lat += 0.0002
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = nowMs, speed = 4.3f))
                nowMs += 500L
                env.stepDetector.emitSteps(config.pedalCadenceMinStepEvents)
            }

            job.cancelAndJoin()

            val latched = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.Decision>()
                .filter { it.outcome == "PEDAL_CADENCE_LATCHED" }
            assertTrue(
                latched.isEmpty(),
                "the egress walk must not be read as pedalling once the anchor is pinned, was $latched",
            )
            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.Decision>()
                    .none { it.outcome == "CONFIRM_DEGRADED_PROMPT" && it.pathLabel?.contains("human_powered") == true },
                "a car park must not be degraded to a prompt for a ride nobody pedalled",
            )
        }

    @Test
    fun should_keep_the_egress_steps_on_the_record_when_a_candidate_is_discarded() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001 · paso 1] A discarded candidate used to
            // set `stepCount = 0`. That is the right answer to the NEXT confirm and the wrong
            // answer to everyone else — above all to the 15-minute unattended verdict, which reads
            // the same counter to decide whether an egress walk justifies keeping the park as a
            // zone. Measured cost: replaying the 2026-07-27 field trace with the scorer cap lifted
            // turned `confirmed_unattended_zone_no_drive_egress` into `aborted_unattended_no_drive`.
            //
            // Shape: the 2026-07-27 one. The fence EXIT arrives at the destination, so the only
            // "driving" is a lone burst the track never corroborates — no proven drive means the
            // verdict may not pin, only save an AREA, and only if an egress walk vouches for it.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedByVehicleEnter(60_000L))
            }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            // Two credible driving-speed fixes: enough for the raw vehicular signal the no-drive
            // branch accepts, far too little for the track to corroborate a drive. Deliberately NO
            // AR vehicle-exit here — it would cap the scorer at Medium and no candidate could ever
            // open, which is the very bug this ticket's step 2 removes.
            nowMs = 5_000L
            locations.emit(GpsPoint(40.0004, -3.7, accuracy = 5f, timestamp = nowMs, speed = 7f))
            nowMs = 7_000L
            locations.emit(GpsPoint(40.00045, -3.7, accuracy = 5f, timestamp = nowMs, speed = 7f))
            nowMs = 60_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 4f, timestamp = nowMs, speed = 0f))
            env.stepDetector.emitSteps(12)        // the egress walk BEGINS, at the car

            // The stop matures → High → CANDIDATE opens. The user has not moved away yet, so the
            // observation window expires with no egress displacement → DISCARDED.
            nowMs = 60_000L + config.slowPath5MinMs + 10_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 4f, timestamp = nowMs, speed = 0f))
            nowMs += config.confirmationObservationWindowMs + 10_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 4f, timestamp = nowMs, speed = 0f))

            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.Candidate>()
                    .any { it.action == "DISCARDED" },
                "the fixture must actually reach a discard",
            )

            // NOW the user finishes walking away — 28 m, and the counter under-logs it the way a
            // real egress does (field Calle Gavia: 68 m on 8 logged steps).
            nowMs += 30_000L
            locations.emit(GpsPoint(40.00075, -3.7, accuracy = 4f, timestamp = nowMs, speed = 0.5f))
            env.stepDetector.emitSteps(2)

            // Nobody answers the prompt. The verdict reads the counter the discard used to wipe:
            // 14 steps of real egress, not the 2 that arrived after it.
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.00075, -3.7, accuracy = 4f, timestamp = nowMs, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "the egress walk happened — a discard says those steps cannot CONFIRM, not that they " +
                    "never occurred, and the unattended verdict still needs them to keep the park",
            )
            assertTrue(
                assertNotNull(env.parkingRepo.getActiveSession()).isApproximate,
                "no proven driving may only yield an AREA, never an exact pin",
            )
        }

    @Test
    fun should_close_the_session_even_when_an_ar_vehicle_exit_caps_the_scorer_at_medium() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §B] The shape that hung for 102 minutes in the
            // field: a human-powered verdict (so the Low/Medium prompt is SUPPRESSED) plus an AR
            // `IN_VEHICLE EXIT` (so `CalculateParkingConfidenceUseCase` takes its fast path and
            // never scores above Medium). No prompt → no response timeout. No High → the early
            // close could never be asked. No candidate → no verdict of any kind. The session had
            // literally no way to end.
            //
            // The registered profile is the human-powered source on purpose: it is the one no
            // measurement may overturn [DET-SOLID-001 C2], so this guard cannot be silently
            // defused by §A's motor refutation the way an AR stamp now is.
            var nowMs = 0L
            val env = setup(clock = { nowMs }, defaultVehicleType = VehicleType.BIKE)
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified)
            }

            emitCorroboratedDrive(locations, speedMps = BICYCLE_SPEED_MPS)
            nowMs = 60_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 4f, timestamp = 0L, speed = 0f))
            // AR says the rider got off — this is what caps the scorer at Medium forever.
            env.coordinator.onVehicleExit(nowMs)
            nowMs += config.fastPathMinStoppedMs + 5_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 4f, timestamp = 0L, speed = 0f))

            // The stop matures past the 5-minute rest the verdict needs. Pre-fix this fix changed
            // nothing at all: Medium again, prompt suppressed again, session alive again.
            nowMs = 60_000L + config.slowPath5MinMs + 10_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 4f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val ended = env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>()
            assertEquals(
                listOf("aborted_unattended_human_powered"),
                ended.map { it.outcome },
                "a session whose prompt is suppressed MUST still reach its own verdict",
            )
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount)
            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.Decision>()
                    .none { it.outcome == "PROMPT_SHOWN" },
                "and it must still never ask '¿has aparcado?'",
            )
        }

    @Test
    fun should_still_save_when_a_boarding_superseded_the_cycling() {
        // The counterpart: bike to the station, then drive. The trip IS a car trip and must pin.
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedBySpeed(38f, 12f))
            }

            env.coordinator.onHumanPoweredRide(nowMs)
            env.coordinator.onVehicleRide(nowMs + 1) // the boarding that supersedes it
            emitCorroboratedDrive(locations)
            nowMs = 60_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(12)
            env.coordinator.onVehicleExit()
            nowMs = 90_000L
            locations.emit(GpsPoint(40.0010, -3.7, accuracy = 8f, timestamp = 0L, speed = 0.5f))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "cycling to the station then driving is a CAR trip [DET-BIKE-NOT-A-CAR-001]",
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-DRIVE-PROOF-001: a Doppler mirage is not measured driving
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_not_pin_when_a_doppler_mirage_is_the_only_measured_driving() =
        runTest(UnconfinedTestDispatcher()) {
            // Replay of the at-home FP (field 2026-07-27, Oppo session 1785157018067): phone
            // INDOORS, a 10-s GPS mirage claims 45 m/s at acc 5 m from 216 m away, then every
            // fix returns home at ~0 m/s. The lone credible spike set maxSpeed for the whole
            // session (`sessionSawDriving`); indoor drift froze the anchor, accumulated
            // pedestrian-band "kinematic egress" fixes past the displacement floor, and
            // CONFIRMED kinematic+egress with 1 step — a pin in the living room. With drive
            // proof the mirage corroborates nothing (fix #1 has no prev, #2 carries degraded
            // accuracy, #3 claims 20 m/s while hopping 11 m) → no confirm path may open.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedBySpeed(speedKmh = 162f, accuracyM = 5f),
                )
            }

            val homeLat = 40.0
            // Mirage burst — the shape of the real trace, timestamps included.
            locations.emit(GpsPoint(homeLat - 0.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 45f))
            nowMs = 5_000L
            locations.emit(GpsPoint(homeLat - 0.004, -3.7, accuracy = 69f, timestamp = 5_000L, speed = 40.6f))
            nowMs = 10_000L
            locations.emit(GpsPoint(homeLat - 0.0039, -3.7, accuracy = 9f, timestamp = 10_000L, speed = 20.2f))
            // Back home; the stop matures (the seeded verified arm lets the anchor freeze).
            nowMs = 15_000L
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 5f, timestamp = 15_000L, speed = 0f))
            nowMs += config.anchorFreezeStopMs + 1_000L
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 5f, timestamp = 15_500L, speed = 0f))
            // Indoor drift in the pedestrian band, wandering past the egress displacement floor
            // — exactly what read as a "kinematic egress walk" in the field.
            var lat = homeLat
            repeat(config.kinematicEgressMinWalkFixes + 2) {
                lat += 0.0001
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = 0L, speed = 1.3f))
            }

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "a Doppler mirage must never count as measured driving nor unlock the kinematic confirm [DET-DRIVE-PROOF-001]",
            )

            job.cancelAndJoin()
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-G-05: unverified exits stay guarded; a late departure verdict upgrades
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_prompt_when_late_departure_verdict_upgrades_a_session_that_never_saw_driving() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-G-05][ANCHOR-LOCK-001] A GEOFENCE_EXIT with no vehicle evidence at arm time
            // arms WITHOUT the seed. When DepartureDetectionWorker later confirms the departure,
            // notifyDepartureConfirmed() seeds the RUNNING session — but its verdict can rest on
            // the same falsifiable ENTER fall-through, so a session that never witnessed driving
            // itself must PROMPT, not save silently (2026-07-04 field incident: the late upgrade
            // silently saved a park the user had been asked about). A user "Sí" completes it.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) } // unverified: no seed

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 2.8f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            assertFalse(env.coordinator.hasDetectedMovement, "sanity: unverified session starts guarded")

            // The sibling departure pipeline confirms mid-session.
            env.coordinator.notifyDepartureConfirmed()
            assertTrue(
                env.coordinator.hasDetectedMovement,
                "a confirmed departure verdict must seed the running session [DET-G-05]",
            )

            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "verified_late without observed driving must not save silently [ANCHOR-LOCK-001]",
            )
            assertEquals(1, env.notification.parkingConfirmationCallCount, "it must ask instead")

            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "user tap completes the save")
        }

    @Test
    fun should_ignore_departure_verdict_between_sessions() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-G-05] A verdict landing with no session running must not leak a seed into the
            // NEXT session — the walking-exit protection would silently vanish.
            val env = setup()
            env.coordinator.notifyDepartureConfirmed() // no session → no-op

            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 2.8f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            assertFalse(
                env.coordinator.hasDetectedMovement,
                "a between-sessions verdict must not seed the next session [DET-G-05]",
            )
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "walking burst must still abort when the verdict predates the session [DET-G-05]",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // BUG-OPPO-LATE-CONFIRM: EXIT + 8 steps → confirm without waiting for STILL
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_fast_confirm_when_exit_and_steps_arrive_before_slow_path_matures() =
        runTest(UnconfinedTestDispatcher()) {
            // Scenario: real parking on a phone where STILL arrives very late or never.
            // Today the coordinator would wait the slow-path's 5-min continuous-stop
            // requirement before reaching HIGH — and resetting stoppedSince on every walk
            // burst between stops pushes the confirm minutes after the real park. With
            // EXIT + minStepsToConfirm steps in hand, confirm immediately and anchor at
            // bestStopLocation. [BUG-OPPO-LATE-CONFIRM]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive: origin + cross movement threshold.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertTrue(env.coordinator.hasDetectedMovement, "sanity: driving speed reached")

            // Park at (40.005, -3.7) — captures bestStopLocation AND the egress anchor in the
            // initial-stop window.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // AR EXIT arrives + 8 pedestrian steps fire. No STILL, no 5 min of stop.
            env.coordinator.onVehicleExit()
            env.stepDetector.emitSteps(8)

            // [DET-A] The user has now physically walked away: next stopped fix is ~33 m from the
            // park anchor (40.005 → 40.0053), past minEgressDisplacementMeters=18 m. The egress
            // gate is satisfied and the fast-confirm fires here.
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "EXIT + minStepsToConfirm steps + egress displacement must trigger an immediate confirm",
            )
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                40.005,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "confirmed location must be the parked-car position (bestStopLocation), not the walking endpoint",
            )
            assertEquals(
                config.reliabilityVehicleExit,
                saved.detectionReliability ?: 0f,
                /* absoluteTolerance = */ 0.0001f,
                "reliability should be reliabilityVehicleExit — EXIT + steps is the same evidence class as the candidate steps path",
            )
        }

    @Test
    fun should_not_fast_confirm_when_only_exit_without_steps() =
        runTest(UnconfinedTestDispatcher()) {
            // Regression guard: EXIT alone (without the pedestrian-steps proof) must NOT
            // trigger the fast confirm — that's the long-stop-in-car case the slow path's
            // 5-min window deliberately protects against (queue at a garage gate, etc.).
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            env.coordinator.onVehicleExit()
            // No steps emitted.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "EXIT without steps must not auto-confirm — slow path remains the gate",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-A: egress displacement gate — the Prague false positive
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_not_fast_confirm_when_exit_and_steps_arrive_without_egress_displacement() =
        runTest(UnconfinedTestDispatcher()) {
            // The Prague false positive, replayed. A spurious AR IN_VEHICLE_EXIT fires mid-trip
            // and, stuck in stop-and-go traffic, the phone bouncing in the user's pocket counts
            // ≥ minStepsToConfirm step events — all while the car never moved and the user never
            // left it. Pre-DET-A this satisfied `vehicleExitConfirmed && stepCount >= min` and
            // published a phantom spot. The egress gate requires real displacement from the
            // parked-car anchor, which never happens here, so no spot is saved. [DET-A]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive: origin + cross movement threshold.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertTrue(env.coordinator.hasDetectedMovement, "sanity: driving speed reached")

            // Traffic-jam stop at (40.005) — egress anchor pinned here.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // Spurious EXIT + 8 bouncing-phone steps. The user is still in the car.
            env.coordinator.onVehicleExit()
            env.stepDetector.emitSteps(8)

            // Subsequent fixes stay essentially at the anchor (~1 m jitter, well under the
            // 18 m gate). The car never drove away, the user never walked away.
            locations.emit(GpsPoint(40.005009, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            locations.emit(GpsPoint(40.005000, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "EXIT + steps WITHOUT egress displacement must NOT confirm — this is the Prague phantom spot",
            )
        }

    @Test
    fun should_fast_confirm_on_steps_and_egress_without_any_vehicle_exit() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-D-03] Steps + egress confirm on their own — no AR IN_VEHICLE_EXIT required. A field
            // trace (2026-06-26) showed the confirm needlessly waiting ~16 s for the AR EXIT while
            // steps+egress were already satisfied. The egress gate is the decisive signal; the exit
            // requirement was redundant and fragile on hardware where EXIT is late/missing.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive, then park at (40.005) — bestStopLocation pinned here.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // NO onVehicleExit() — only the pedestrian-steps proof + egress displacement.
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // ~33 m away

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "steps + egress must confirm WITHOUT an AR vehicle-exit [DET-D-03]",
            )
            assertEquals(
                40.005,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.00001,
                "confirmed location must be the parked-car position (bestStopLocation)",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-GAP-ANCHOR-001: an anchor whose stop opened after a GPS hole never pins
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_prompt_notPin_when_anchor_stop_opens_after_gps_hole() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-07-29, Redmi Av. Sanlúcar: corroborated driving, then a 100-s MIUI hole,
            // then ONE speed-0 fix mid-route — the stream never witnessed the car coming to rest,
            // so that fix may be a drive-past point. The egress walk home then satisfied
            // steps+egress and pinned 315 m before the real park. The proofs hold, the ANCHOR
            // doesn't: ask, never pin.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations) // ends at 40.0, last fix t=25 s, 11 m/s
            // 120-s hole, then a single stopped fix somewhere further up the route.
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 5f, timestamp = 145_000L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0103, -3.7, accuracy = 5f, timestamp = 150_000L, speed = 0f)) // egress ~33 m

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "a gap-entered anchor must never pin silently [DET-GAP-ANCHOR-001]",
            )
            assertEquals(
                1,
                env.notification.parkingConfirmationCallCount,
                "the user must be asked instead — the proofs hold, the anchor doesn't",
            )

            // An explicit user "Sí" still saves (they answer near the car).
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.0103, -3.7, accuracy = 5f, timestamp = 155_000L, speed = 0f))
            job.cancelAndJoin()
            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "user tap completes the save")
        }

    @Test
    fun should_confirm_normally_when_stop_opens_within_the_gap_budget() =
        runTest(UnconfinedTestDispatcher()) {
            // Control replay with REAL timestamps at normal cadence: the destination stop opens
            // 5 s after the last driving fix — no hole, no taint, silent steps+egress confirm.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations) // ends at 40.0, last fix t=25 s, 11 m/s
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 5f, timestamp = 30_000L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0008, -3.7, accuracy = 5f, timestamp = 35_000L, speed = 0f)) // egress ~33 m

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "a stop entered at normal cadence confirms exactly as before [DET-GAP-ANCHOR-001 control]",
            )
            assertEquals(
                40.0005,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.00001,
                "confirmed location must be the parked-car anchor",
            )
        }

    /**
     * [DET-GAP-ANCHOR-ZONE-001] The unanswered flavour of the same replay (the 05:24 prompt nobody
     * saw). This used to assert "nudge, never pin", on the grounds that a gap-entered anchor's
     * forward error is unboundable. Field 2026-08-17 (Redmi session `1786970028118`, Calle Bahía de
     * Alcudia 4) refuted that: the hole has a DURATION, the phone can only have covered the
     * car→anchor offset on foot inside it, and the car then sat still for 14,7 min. The park is now
     * KEPT as an area sized by the hole; the taint costs precision, not the car.
     *
     * The FP this branch was built for (Av. Sanlúcar 2026-07-29) is separated by the sustained rest
     * and is pinned in `EvaluateUnattendedParkingSaveUseCaseTest` — there the car drove ON, so no
     * rest ever accrues and the verdict is still the nudge.
     */
    @Test
    fun should_saveBoundedZone_when_unattended_timeout_finds_gap_entered_anchor_at_rest() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations)
            nowMs = 145_000L
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 5f, timestamp = 145_000L, speed = 0f))
            env.stepDetector.emitSteps(8)
            nowMs = 150_000L
            locations.emit(GpsPoint(40.0103, -3.7, accuracy = 5f, timestamp = 150_000L, speed = 0f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "prompt must be shown")

            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.0103, -3.7, accuracy = 5f, timestamp = nowMs, speed = 0f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the park must be KEPT as a zone, not lost")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertTrue(saved.isApproximate, "a gap-born anchor may only yield an AREA, never an exact pin")
            assertEquals(40.010, saved.location.latitude, 0.00005, "the zone centers on the gap-born anchor")
            assertTrue(saved.zoneRadiusMeters!! >= config.honestCloseMinZoneRadiusMeters)
            assertEquals(config.reliabilityUnattendedSave, saved.detectionReliability, "never community-published")
            assertEquals(0, env.notification.markParkingNudgeCallCount, "the saved-parking card is the ask — no extra nudge")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals(
                "confirmed_unattended_zone_gap_anchor",
                ended.outcome,
                "[DET-GAP-ANCHOR-001][DET-GAP-ANCHOR-ZONE-001]",
            )
        }

    @Test
    fun should_saveBoundedZone_when_user_confirms_over_a_gap_entered_anchor() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-USER-YES-IS-NOT-A-COORDINATE-001] The answer settles WHETHER the user parked, not
            // WHERE. This path already discards a gap-born anchor as "possibly a drive-past point
            // hundreds of meters out" and then used to pin the fallback fix as an EXACT coordinate,
            // recording the doubt nowhere — while the unattended timeout, facing the identical
            // situation, bounds it and saves an area. Same hole, same bound, now the same honesty.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations)
            // The stop opens on the far side of a ~105 s hole with the car last seen driving.
            nowMs = 145_000L
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 5f, timestamp = 145_000L, speed = 0f))
            env.stepDetector.emitSteps(8)
            nowMs = 150_000L
            locations.emit(GpsPoint(40.0103, -3.7, accuracy = 5f, timestamp = 150_000L, speed = 0f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "prompt must be shown")

            // …and this time the user answers it.
            env.coordinator.onUserConfirmedParking()
            nowMs += 5_000L
            locations.emit(GpsPoint(40.0103, -3.7, accuracy = 5f, timestamp = nowMs, speed = 0f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the user's yes must still save")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertTrue(
                saved.isApproximate,
                "a yes over an anchor the path itself distrusted may not claim an exact coordinate",
            )
            assertTrue(
                saved.zoneRadiusMeters!! >= config.honestCloseMinZoneRadiusMeters,
                "the radius must cover the ground walkable inside the hole, was ${saved.zoneRadiusMeters}",
            )
            assertEquals(
                config.reliabilityUserConfirmed, saved.detectionReliability,
                "the EVENT is certain — the user said so; only its position is not",
            )
        }

    @Test
    fun should_keep_the_exact_pin_when_user_confirms_over_a_witnessed_anchor() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-USER-YES-IS-NOT-A-COORDINATE-001] The other side: when nothing declared the
            // location doubtful, an area says LESS than the point does, so the point stands. This
            // is what keeps the change from smearing every well-located pin into a 60 m blob —
            // field 2026-08-22 (Redmi, 15.7 m fix) stays an exact pin on purpose.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            driveParkAndWalkAwayWithLateBirth(env, locations) { nowMs = it }
            env.coordinator.onUserConfirmedParking()
            nowMs = 125_000L
            locations.emit(GpsPoint(40.0025, -3.7, accuracy = 8f, timestamp = 125_000L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertNull(
                saved.zoneRadiusMeters,
                "a witnessed anchor with a decent fix must stay an exact pin, was ${saved.zoneRadiusMeters}",
            )
            assertEquals(config.reliabilityUserConfirmed, saved.detectionReliability)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-CONFIRM-ANCHOR-001: a late user "Sí" anchors at the car, not the user
    // ─────────────────────────────────────────────────────────────────────────

    /** Shared fixture: corroborated drive → witnessed stop at 40.0005 (no gap, frozen by time)
     *  → poor-accuracy walk (mute-ish counter: 2 steps fire mid-walk, so the egress birth is
     *  recorded ~222 m from the anchor → egress-born-AWAY → the user-confirm else branch). */
    private suspend fun driveParkAndWalkAwayWithLateBirth(
        env: TestEnv,
        locations: MutableSharedFlow<GpsPoint>,
        advanceClock: (Long) -> Unit,
    ) {
        emitCorroboratedDrive(locations) // ends at 40.0, last fix t=25 s, 11 m/s
        // Witnessed stop: opens 5 s after the last driving fix (no gap) and matures past
        // anchorFreezeStopMs → anchor FROZEN at 40.0005.
        advanceClock(30_000L)
        locations.emit(GpsPoint(40.0005, -3.7, accuracy = 5f, timestamp = 30_000L, speed = 0f))
        advanceClock(95_000L)
        locations.emit(GpsPoint(40.0005, -3.7, accuracy = 6f, timestamp = 95_000L, speed = 0f))
        // Walk away on a degraded stream (accuracy 60 > minGpsAccuracyForDriving): no kinematic
        // egress fix ever counts, so no birth is recorded near the car.
        advanceClock(100_000L)
        locations.emit(GpsPoint(40.0009, -3.7, accuracy = 60f, timestamp = 100_000L, speed = 1.3f))
        advanceClock(105_000L)
        locations.emit(GpsPoint(40.0013, -3.7, accuracy = 60f, timestamp = 105_000L, speed = 1.3f))
        advanceClock(110_000L)
        locations.emit(GpsPoint(40.0017, -3.7, accuracy = 60f, timestamp = 110_000L, speed = 1.3f))
        advanceClock(115_000L)
        locations.emit(GpsPoint(40.0021, -3.7, accuracy = 60f, timestamp = 115_000L, speed = 1.3f))
        // The counter finally delivers 2 steps mid-walk → the next fix records the egress birth
        // ~222 m from the anchor (past the 150 m born-at-anchor floor).
        env.stepDetector.emitSteps(2)
        advanceClock(120_000L)
        locations.emit(GpsPoint(40.0025, -3.7, accuracy = 60f, timestamp = 120_000L, speed = 1.3f))
    }

    @Test
    fun should_anchor_at_witnessed_stop_when_user_confirms_far_from_it() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-08-11 16:08: measured driving came to rest (witnessed stop), mute step
            // counter (2 steps), and the user answered "Sí" AFTER walking to their destination —
            // the pin planted at the destination, not where the drive ended. A "Sí" far from
            // both car witnesses must anchor at the witnessed stop. [DET-CONFIRM-ANCHOR-001]
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            driveParkAndWalkAwayWithLateBirth(env, locations) { nowMs = it }
            // Keep walking well past the birth, then answer at the destination — ~444 m from the
            // stop and ~222 m from the birth, far from BOTH car witnesses.
            nowMs = 125_000L
            locations.emit(GpsPoint(40.0031, -3.7, accuracy = 60f, timestamp = 125_000L, speed = 1.3f))
            nowMs = 130_000L
            locations.emit(GpsPoint(40.0038, -3.7, accuracy = 60f, timestamp = 130_000L, speed = 1.3f))
            env.coordinator.onUserConfirmedParking()
            nowMs = 140_000L
            locations.emit(GpsPoint(40.0045, -3.7, accuracy = 8f, timestamp = 140_000L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "user tap must save exactly once")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                40.0005,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "a late 'Sí' far from every car witness must anchor at the witnessed stop, not the pedestrian [DET-CONFIRM-ANCHOR-001]",
            )
            assertEquals(
                config.reliabilityUserConfirmed,
                saved.detectionReliability ?: 0f,
                /* absoluteTolerance = */ 0.0001f,
                "re-anchoring must not change the user-confirmed reliability",
            )
        }

    @Test
    fun should_keep_current_fix_when_user_confirms_near_the_witnessed_stop() =
        runTest(UnconfinedTestDispatcher()) {
            // Same egress-born-away session, but the user answers back within the near-car radius
            // of the stop — today's behavior (anchor at the user's stop) must be untouched.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            driveParkAndWalkAwayWithLateBirth(env, locations) { nowMs = it }
            // Walk back towards the car and answer ~33 m from the stop (≤ 100 m).
            nowMs = 125_000L
            locations.emit(GpsPoint(40.0018, -3.7, accuracy = 60f, timestamp = 125_000L, speed = 1.3f))
            nowMs = 130_000L
            locations.emit(GpsPoint(40.0012, -3.7, accuracy = 60f, timestamp = 130_000L, speed = 1.3f))
            env.coordinator.onUserConfirmedParking()
            nowMs = 140_000L
            locations.emit(GpsPoint(40.0008, -3.7, accuracy = 8f, timestamp = 140_000L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                40.0008,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.00001,
                "answering near the stop must keep today's behavior (the user's current stop) [DET-CONFIRM-ANCHOR-001]",
            )
        }

    @Test
    fun should_keep_current_fix_when_user_confirms_near_the_egress_birth() =
        runTest(UnconfinedTestDispatcher()) {
            // Enamorados guard (field 2026-07-15): with an egress born AWAY the anchor may be an
            // intermediate stop (a light 1.11 km back) and the BIRTH is where the car is. A "Sí"
            // answered near the birth must keep today's behavior (the user's current stop) — the
            // witnessed-stop re-anchor only wins far from BOTH car witnesses.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            driveParkAndWalkAwayWithLateBirth(env, locations) { nowMs = it }
            // Answer right next to the recorded birth (~233 m from the stop, ~11 m from the birth).
            env.coordinator.onUserConfirmedParking()
            nowMs = 140_000L
            locations.emit(GpsPoint(40.0026, -3.7, accuracy = 8f, timestamp = 140_000L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                40.0026,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.00001,
                "answering near the egress birth must keep the user's current stop — the birth may be the car [DET-CONFIRM-ANCHOR-001]",
            )
        }

    @Test
    fun should_keep_current_fix_when_user_confirms_far_but_anchor_is_gap_entered() =
        runTest(UnconfinedTestDispatcher()) {
            // A gap-entered anchor may be a drive-past point with UNBOUNDABLE forward error
            // (field 2026-07-29, Av. Sanlúcar) — it must never win the user-confirm re-anchor,
            // however far the user answered from it. [DET-GAP-ANCHOR-001]
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations) // ends at 40.0, last fix t=25 s, 11 m/s
            // 120-s hole → the stop at 40.010 is gap-entered.
            nowMs = 145_000L
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 5f, timestamp = 145_000L, speed = 0f))
            env.stepDetector.emitSteps(8)
            nowMs = 150_000L
            locations.emit(GpsPoint(40.0103, -3.7, accuracy = 5f, timestamp = 150_000L, speed = 0f))
            // Walk on well past the near-car radius and answer there.
            nowMs = 155_000L
            locations.emit(GpsPoint(40.0110, -3.7, accuracy = 5f, timestamp = 155_000L, speed = 1.3f))
            nowMs = 160_000L
            locations.emit(GpsPoint(40.0125, -3.7, accuracy = 5f, timestamp = 160_000L, speed = 1.3f))
            env.coordinator.onUserConfirmedParking()
            nowMs = 165_000L
            locations.emit(GpsPoint(40.0130, -3.7, accuracy = 8f, timestamp = 165_000L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                40.0130,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.00001,
                "a gap-entered anchor must never win the re-anchor — the user's current stop is the only honest witness",
            )
        }

    @Test
    fun should_anchor_at_current_fix_when_no_stop_was_witnessed() =
        runTest(UnconfinedTestDispatcher()) {
            // No bestStopLocation at all (still driving when the user taps "Sí"): the current
            // fix remains the only anchor available — unchanged behavior.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations)
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.003, -3.7, accuracy = 5f, timestamp = 30_000L, speed = 10f))

            job.cancelAndJoin()

            assertEquals(
                40.003,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.00001,
                "without a witnessed stop the current fix is the only anchor available",
            )
        }

    @Test
    fun should_anchor_at_stop_when_egress_born_at_anchor_and_user_confirms_far() =
        runTest(UnconfinedTestDispatcher()) {
            // The egress-born-AT-anchor branch is untouched: birth recorded at the car, user
            // answers far away → the pin was and stays the stop anchor.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            emitCorroboratedDrive(locations)
            nowMs = 30_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 5f, timestamp = 30_000L, speed = 0f))
            nowMs = 95_000L
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 6f, timestamp = 95_000L, speed = 0f))
            // 2 steps AT the car (below the auto-confirm bar) → birth recorded at the anchor.
            env.stepDetector.emitSteps(2)
            nowMs = 100_000L
            locations.emit(GpsPoint(40.00052, -3.7, accuracy = 6f, timestamp = 100_000L, speed = 0f))
            // Degraded walk far away, then the late answer.
            nowMs = 115_000L
            locations.emit(GpsPoint(40.0015, -3.7, accuracy = 60f, timestamp = 115_000L, speed = 1.3f))
            nowMs = 130_000L
            locations.emit(GpsPoint(40.0030, -3.7, accuracy = 60f, timestamp = 130_000L, speed = 1.3f))
            env.coordinator.onUserConfirmedParking()
            nowMs = 140_000L
            locations.emit(GpsPoint(40.0045, -3.7, accuracy = 8f, timestamp = 140_000L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                40.0005,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.00001,
                "egress born at the anchor keeps anchoring the 'Sí' at the stop — branch untouched",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-C-02: post-confirm hold — errand re-anchor + finalize
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_discard_tentative_confirm_and_reanchor_at_final_spot_when_driving_resumes() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-C-02] The "buy tobacco" bug: the user parks at an errand spot, gets out and walks
            // to a kiosk (egress → tentative confirm), then drives on to park properly nearby. The
            // tentative confirm must be DISCARDED when driving resumes within the hold, and the park
            // must re-anchor at the FINAL spot — not pin the kiosk.
            var fakeNow = 1_000_000L
            val holdConfig = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = holdConfig, clock = { fakeNow })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive, then park at the ERRAND spot (40.005); egress → tentative confirm (held, not saved).
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress ~33 m
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "must NOT confirm yet — held in the post-confirm window [DET-C-02]",
            )

            // 30 s into the hold the errand is over: drive off again → discard the tentative confirm.
            fakeNow += 30_000L
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "tentative confirm discarded — still nothing saved",
            )

            // Park for real at the FINAL spot (40.020); egress → new tentative confirm; let it settle.
            locations.emit(GpsPoint(40.020, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0203, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress ~33 m
            fakeNow += 120_001L
            locations.emit(GpsPoint(40.0203, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // hold elapsed

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "exactly one confirm — the FINAL spot, after the hold settled [DET-C-02]",
            )
            assertEquals(
                40.020,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.0001,
                "park must anchor at the FINAL spot (40.020), not the errand stop (40.005)",
            )
        }

    @Test
    fun should_finalize_starved_hold_by_clock_when_gps_dies_after_parking() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-AUDIT-002 T7/M2] The COMMON egress: park, walk into the building, GPS dies.
            // Every hold decision used to wait for the NEXT fix — which never came — and the
            // tentatively-confirmed park died in silence (no pin, no notification). The watchdog
            // clock must finalize it at the pinned location and end the session.
            val holdConfig = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = holdConfig)
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // [DET-NO-CLOCK-PLANTS-A-PIN-001] The drive is now a DRIVE. This scenario always said
            // "park, walk into the building" — which happens after driving somewhere — but its
            // stream was a single 6 m/s fix over 111 m with no timestamps, i.e. not a trip by any
            // measure the app applies. The watchdog may only finalize what the session MEASURED, so
            // the stub is spelled out: five credible in-band fixes, 10 s apart, 222 m of ground.
            // Its sibling below covers the other half — a starved hold nothing measured.
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 10_000L, speed = 6f))
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 5f, timestamp = 20_000L, speed = 6f))
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 30_000L, speed = 6f))
            locations.emit(GpsPoint(40.0015, -3.7, accuracy = 5f, timestamp = 40_000L, speed = 6f))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 50_000L, speed = 6f))
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 60_000L, speed = 0f)) // park
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0013, -3.7, accuracy = 5f, timestamp = 70_000L, speed = 0f)) // egress ~33 m → held
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "held, nothing saved yet")

            // GPS dies — no more fixes, ever. Only virtual time advances.
            testScheduler.advanceUntilIdle()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the clock, not a fix, must finalize the starved hold [DET-AUDIT-002 T7]")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(40.001, saved.location.latitude, 0.00005, "pin at the parked-car anchor")

            job.cancelAndJoin()
        }

    @Test
    fun should_resolve_a_starved_prompt_by_clock_when_no_fix_ever_comes_back() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-STARVED-PROMPT-HAS-NO-WITNESS-001] The user's own question: what happens if,
            // after the last bad fix, no good one arrives for an hour?
            //
            // Before this: NOTHING. `ResponseTimeoutStage` is a SessionStage, so the 15-minute
            // verdict only ever ran when a FIX arrived. Ask the user, get no answer, let the stream
            // die — and the park was lost in silence: no pin, no zone, no nudge, and not one line
            // in the trace to say a decision had been due. It is the common shape, not an edge
            // case: the prompt fires as the user walks into a building, which is when GPS stops.
            //
            // ⏱ The 15 min + margin cost nothing here — `runTest` runs on virtual time.
            var fakeNow = 1_000_000L
            val cfg = ParkingDetectionConfig(confirmHoldMs = 0L) // watchdog del hold OFF: seam de test
            val env = setup(config = cfg, clock = { fakeNow })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // A real drive, so the session is not thrown away for lack of evidence…
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 10_000L, speed = 6f))
            locations.emit(GpsPoint(40.0005, -3.7, accuracy = 5f, timestamp = 20_000L, speed = 6f))
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 30_000L, speed = 6f))
            locations.emit(GpsPoint(40.0015, -3.7, accuracy = 5f, timestamp = 40_000L, speed = 6f))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 50_000L, speed = 6f))
            // …then a stop, and the GPS dies for good. `runCurrent`, NOT `advanceUntilIdle`: the
            // latter would jump virtual time past the watchdog's own delay and resolve it before
            // the snapshot below, which is precisely how this test first passed with the watchdog
            // neutralised.
            // …and then a long stillness, which is what earns the prompt. In the field it took the
            // stop clock past `lowNotifTimeoutMs` with the Low/Medium notification suppressed until
            // it did (`⊘ Low/Medium notif suppressed — no vehicleExit`), so the stub has to sit
            // still for real rather than emit one stopped fix and hope.
            var ts = 60_000L
            repeat(20) {
                ts += 20_000L
                fakeNow += 20_000L
                locations.emit(GpsPoint(40.0025, -3.7, accuracy = 40f, timestamp = ts, speed = 0f))
                testScheduler.runCurrent()
            }
            // The GPS now dies for good. `runCurrent`, NOT `advanceUntilIdle`: the latter would jump
            // virtual time past the watchdog's own delay and resolve it before the snapshot below,
            // which is exactly how this test first passed with the watchdog neutralised.
            testScheduler.runCurrent()

            // ⚠️ The BASELINE is what makes this test mean anything. Asserting "a notification
            // exists" is trivially true — the prompt itself is one — so the assertion is that the
            // count MOVED after the stream died, which only the watchdog can do.
            val savesBefore = env.parkingRepo.saveNewParkingSessionCallCount
            val notifsBefore = env.notification.confirmationNotifOps.size
            // The trace is the third witness, and the one the ticket is really about: a starved
            // prompt must leave a RECORD of the decision, whichever way it went.
            val eventsBefore = env.detectionLogger.events.size

            fakeNow += cfg.confirmationResponseTimeoutMs + 120_000L
            testScheduler.advanceUntilIdle()

            // Deliberately not asserting WHICH verdict won: SaveZone vs Ask depends on evidence
            // this stub does not fix, and pinning it here would be asserting the stage's job rather
            // than the watchdog's. What the watchdog owes is that the decision RAN at all.
            assertTrue(
                env.parkingRepo.saveNewParkingSessionCallCount > savesBefore ||
                    env.notification.confirmationNotifOps.size > notifsBefore ||
                    env.detectionLogger.events.size > eventsBefore,
                "a starved prompt must still reach a verdict — pin, zone or nudge, but never " +
                    "silence [DET-STARVED-PROMPT-HAS-NO-WITNESS-001]. " +
                    "saves ${env.parkingRepo.saveNewParkingSessionCallCount} (was $savesBefore), " +
                    "notifs ${env.notification.confirmationNotifOps.size} (was $notifsBefore), " +
                    "events ${env.detectionLogger.events.size} (was $eventsBefore)",
            )

            job.cancelAndJoin()
        }

    @Test
    fun should_close_a_starved_hold_without_a_pin_when_the_session_never_measured_a_drive() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-NO-CLOCK-PLANTS-A-PIN-001] The branch the redesign named: a clock running out
            // means "no further evidence arrived", and that is not evidence. This is the exact
            // stream the sibling test used to carry — ONE fix at driving speed over 111 m, which
            // `DrivingEvidence` calls Weak — and the watchdog used to plant a pin on it with no fix
            // to re-validate it. In field forensics that is what "a spot appeared and I don't know
            // why" looks like.
            //
            // ⛔ `confirmHoldMs = 120_000L` is a TEST SEAM, not a runtime option: the watchdog is
            // switched OFF by `confirmHoldMs = 0`, and three test files rely on that. Anyone
            // "cleaning up" the `> 0` guard breaks them.
            // ⏱ The 2 min 30 s wait costs nothing: `runTest` runs on virtual time.
            val holdConfig = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = holdConfig)
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // "drive"
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0013, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress → held
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "held, nothing saved yet")

            // GPS dies — no more fixes, ever. Only virtual time advances.
            testScheduler.advanceUntilIdle()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "a clock may not plant a pin the session never measured [DET-NO-CLOCK-PLANTS-A-PIN-001]",
            )

            job.cancelAndJoin()
        }

    @Test
    fun should_save_with_user_reliability_when_user_confirms_during_the_hold() =
        runTest(UnconfinedTestDispatcher()) {
            // A "Sí" tapped while the tentative confirm is HELD is the USER-CONFIRMED path: the
            // save must carry reliabilityUserConfirmed (1.0) and the "user" label, not the 0.9 of
            // the auto path that opened the hold — the class KDoc promises it, and the repark
            // guard must never veto a park the user explicitly confirmed. [DET-C-02]
            var fakeNow = 1_000_000L
            val holdConfig = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = holdConfig, clock = { fakeNow })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress → held
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "held, nothing saved yet")

            // 10 s into the hold the user taps "Sí" on the notification.
            fakeNow += 10_000L
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount)
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                holdConfig.reliabilityUserConfirmed,
                saved.detectionReliability ?: 0f,
                /* absoluteTolerance = */ 0.0001f,
                "a Sí during the hold saves as the USER path (1.0), not the auto reliability [DET-C-02]",
            )
        }

    @Test
    fun should_finalize_tentative_confirm_after_hold_when_car_stays_put() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-C-02] A genuine park: egress → tentative confirm → the car stays put → the hold
            // elapses → finalize at the parked-car position. Nothing is saved during the hold window.
            var fakeNow = 1_000_000L
            val holdConfig = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = holdConfig, clock = { fakeNow })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress → tentative
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "held, not confirmed yet [DET-C-02]",
            )

            // Car stays put; hold elapses → finalize on the next stationary fix.
            fakeNow += 120_001L
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "finalized exactly once after the hold elapsed [DET-C-02]",
            )
            assertEquals(
                40.005,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.0001,
                "finalized at the parked-car position",
            )
        }

    @Test
    fun should_preserve_post_save_card_across_immediate_new_session() =
        runTest(UnconfinedTestDispatcher()) {
            // Scenario: auto-confirm succeeds → user walks → AR fires a SPURIOUS
            // IN_VEHICLE_ENTER → service restarts coordinator → new invoke() begins. The
            // freshly-posted revert card must NOT be wiped by the second session's
            // session-start dismiss. The timestamp gate (savedConfirmPostedAt vs
            // confirmationResponseTimeoutMs=15 min) protects it. [REFACTOR-300-FIX]
            val env = setup()

            // Session 1: drive + user-confirm → savedConfirm card posted.
            val session1 = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job1 = launch { env.coordinator.invoke(session1) }
            session1.emit(stationaryFix(lat = 40.0, lon = -3.7))
            session1.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            env.coordinator.onUserConfirmedParking()
            session1.emit(stationaryFix(lat = 40.002, lon = -3.7))
            job1.cancelAndJoin()

            assertEquals(1, env.notification.parkingSavedConfirmCallCount)
            assertEquals(
                "savedConfirm",
                env.notification.confirmationNotifOps.last(),
                "sanity: session 1 must end with the card visible",
            )
            val opsBeforeSession2 = env.notification.confirmationNotifOps.size

            // Session 2: simulate the spurious-ENTER restart. Just enter and exit the
            // coordinator with minimal traffic so its session-start path runs.
            val session2 = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job2 = launch { env.coordinator.invoke(session2) }
            session2.emit(stationaryFix(lat = 40.002, lon = -3.7))
            job2.cancelAndJoin()

            // No new dismiss should have been recorded on the confirmation id. The card
            // remains visible — the LAST op is still the savedConfirm from session 1.
            assertEquals(
                opsBeforeSession2,
                env.notification.confirmationNotifOps.size,
                "session-start dismiss must be skipped while the revert card is still fresh",
            )
            assertEquals(
                "savedConfirm",
                env.notification.confirmationNotifOps.last(),
                "the revert card must remain the last op on PARKING_CONFIRMATION_NOTIFICATION_ID",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-CONFIRM-FRESHNESS-001: evidence must still be true when the pin is planted
    // (field 2026-07-23: FP "Bodegas Osborne" traffic light, FP Calle Abeto pick-up,
    // FN Vista Hermosa maneuver-tainted anchor)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_discard_held_confirm_when_position_outran_the_steps_at_settle() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-07-23, Calle Abeto: pick-up stop → incidental steps + a drift fix opened
            // a tentative confirm → the departure's only rolling fix carried acc 71 m (> the 50 m
            // trust gate) and GPS then starved for 95 s → the hold settled with the car at ANOTHER
            // traffic light 570 m away and pinned the pick-up spot. Settle-time re-validation must
            // read the vehicle-scale displacement and DISCARD, never pin.
            var fakeNow = 1_000_000L
            val holdConfig = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = holdConfig, clock = { fakeNow })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f)) // drive
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))  // pick-up stop
            env.stepDetector.emitSteps(8)                                                      // incidental steps
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress floor → tentative
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "held, not confirmed yet [DET-C-02]")

            // No trustworthy rolling fix ever arrives (departure under degraded GPS); the hold
            // elapses with the car stopped ~550 m away — far beyond what 8 steps could walk.
            fakeNow += 120_001L
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "stale held confirm must be DISCARDED at settle, never pinned [DET-CONFIRM-FRESHNESS-001]",
            )
            // [DET-HOLD-BRANCHES-MUST-SPEAK-001] Was an ad-hoc `Decision(outcome=HOLD_STALE_DISCARDED)`;
            // it moved onto the typed hold lane so its six mute siblings became comparable to it.
            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.Hold>()
                    .any { it.action == HoldAction.DISCARDED_STALE },
                "the settle-time discard must be visible in forensics",
            )
        }

    @Test
    fun should_reanchor_at_the_real_stop_when_live_counter_creep_leaves_the_frozen_light() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-07-23, "Bodegas Osborne": the anchor froze at a 27-s traffic light and
            // the parking-search creep (6–16 km/h — below real-driving speed) could never move
            // it; the egress walk then confirmed AT the light, 160 m from the car. With the step
            // counter PROVEN ALIVE and silent through the creep, the stepless departure must
            // unfreeze the anchor so the REAL stop re-captures it and the confirm pins there.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            env.stepDetector.emitStep() // counter proven ALIVE (event gated while moving — count stays 0)
            val lightLat = 40.001
            nowMs = 1_000L
            locations.emit(GpsPoint(lightLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // light stop
            nowMs += config.anchorFreezeStopMs + 1_000L
            locations.emit(GpsPoint(lightLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // FROZEN at the light
            // Parking-search creep: sub-real-driving speed, ZERO steps, every fix provably beyond
            // the anchor envelope (5 + 10 + 18 = 33 m).
            var lat = lightLat
            repeat(config.frozenAnchorSteplessDepartureFixes) {
                lat += 0.0005 // ~55 m per fix
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = 0L, speed = 3.5f))
            }
            val carLat = lat + 0.0005
            nowMs += 5_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // REAL stop
            env.stepDetector.emitSteps(8) // egress steps
            nowMs += 5_000L
            locations.emit(GpsPoint(carLat + 0.0003, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.3f)) // walk away

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "steps+egress must confirm at the real stop")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                carLat,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00005,
                "pin at the REAL stop the creep led to, not at the frozen light [DET-CONFIRM-FRESHNESS-001]",
            )
        }

    @Test
    fun should_keep_frozen_anchor_through_fast_stepless_walk_when_counter_is_mute() =
        runTest(UnconfinedTestDispatcher()) {
            // Camelias-Oppo protection: same displacement signature as the creep above, but the
            // counter never fired this session — its silence is NOISE, not evidence. The frozen
            // anchor must hold and the timeout save must pin the CAR, not the walk's end.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            val carLat = 40.001
            nowMs = 1_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park
            nowMs += config.anchorFreezeStopMs + 1_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // FROZEN
            // Brisk MUTE walk with the stepless-departure signature (≥ clear speed, beyond envelopes).
            var lat = carLat
            repeat(config.frozenAnchorSteplessDepartureFixes) {
                lat += 0.0005
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = 0L, speed = 3.5f))
            }
            val doorLat = lat + 0.0002
            nowMs += 30_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // stand at the door
            nowMs += config.slowPathGateMs + 5_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs += config.lowNotifTimeoutMs + 5_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            if (saved != null) {
                assertEquals(
                    carLat,
                    saved.location.latitude,
                    /* absoluteTolerance = */ 0.00005,
                    "a MUTE counter must never anchor the save at the walk's end — the zone centers on the car [DET-CONFIRM-FRESHNESS-001][DET-FROZEN-COUNTER-001]",
                )
            }
        }

    @Test
    fun should_confirm_silently_when_the_parking_maneuver_spent_the_walk_fix_budget() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-07-24 01:08, Vista Hermosa (the FN): the slow final maneuver into the
            // spot (pedestrian-band fixes, ZERO step events, counter proven alive) tainted a
            // PERFECT anchor as walk-entered → the steps+egress confirm degraded to a 1 AM prompt
            // and the timeout guard then refused the save. With step corroboration required, a
            // maneuver-entered anchor must confirm SILENTLY.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            env.stepDetector.emitStep() // counter proven ALIVE pre-drive…
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // …then real driving resets the odometers
            // Final parking maneuver: pedestrian-band fixes over the walk-fix budget, NO steps.
            var lat = 40.0025
            repeat(config.anchorFreezeMaxWalkFixes + 1) {
                lat += 0.0001
                nowMs += 3_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 40f, timestamp = 0L, speed = 1.5f))
            }
            val carLat = lat + 0.0001
            nowMs += 3_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park
            env.stepDetector.emitSteps(8) // egress steps at the car
            nowMs += 5_000L
            locations.emit(GpsPoint(carLat + 0.0003, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.3f)) // walk away

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "maneuver-entered anchor must confirm SILENTLY, not degrade to a prompt [DET-CONFIRM-FRESHNESS-001]",
            )
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(carLat, saved.location.latitude, /* absoluteTolerance = */ 0.00005, "pin at the car's rest")
        }

    @Test
    fun should_still_taint_walk_entered_anchor_when_step_events_corroborate_the_walk_in() =
        runTest(UnconfinedTestDispatcher()) {
            // Regression control for the corroboration: a REAL walk-in on a live counter fires
            // step events along the way (even while the counting gate ignores them) — the taint
            // must stand and the confirm must keep degrading to a prompt, never pin silently.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            // Walk in: pedestrian-band fixes WITH step events between them.
            var lat = 40.0025
            repeat(config.anchorFreezeMaxWalkFixes + 1) {
                lat += 0.0001
                nowMs += 3_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 40f, timestamp = 0L, speed = 1.5f))
                env.stepDetector.emitStep()
            }
            val doorLat = lat + 0.0001
            nowMs += 3_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // stand still
            env.stepDetector.emitSteps(8)
            nowMs += 5_000L
            locations.emit(GpsPoint(doorLat + 0.0003, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.3f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "a step-corroborated walk-entered anchor must never pin silently [DET-CREDIBLE-DRIVE-001]",
            )
        }

    /**
     * [DET-EXIT-LINE-COUNTS-NOTHING-001] The session's closing line must report the fixes the trip
     * actually saw.
     *
     * It is logged AFTER the `finally`, and the `finally` calls `reset()`, so reading the counter
     * off the state there answered `locationCount=0` for every session that ended normally — a zero
     * that reads like a symptom of the very thing being diagnosed. The line is the one anyone looks
     * at first after a field test, and an instrument that lies costs the scarce resource, which is
     * the time of whoever is reading it.
     *
     * The only observable is the log line itself, so the witness is a recording `Antilog`: this is
     * a diagnostics defect, and asserting on anything else would be asserting on something that was
     * never broken.
     *
     * ⚠️ The session has to end of its OWN accord — `takeWhile { !completed }` ending the flow, the
     * `finally` running, `invoke` returning. `cancelAndJoin` does NOT reach the line: it is logged
     * after the `try/finally`, so a `CancellationException` propagates straight past it. Measured,
     * not assumed — the first version of this test cancelled and failed on "the session must
     * announce its own ending", which is a fact about the instrument and is written down in the
     * ticket.
     */
    @Test
    fun should_report_on_the_exit_line_the_fixes_this_trip_actually_saw() =
        runTest(UnconfinedTestDispatcher()) {
            val antilog = RecordingAntilog()
            Napier.base(antilog)
            try {
                val env = setup()
                val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
                val job = launch { env.coordinator.invoke(locations) }

                locations.emit(stationaryFix(lat = 40.0, lon = -3.7))                              // 1
                locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f)) // 2
                env.coordinator.onUserConfirmedParking()
                locations.emit(stationaryFix(lat = 40.002, lon = -3.7))                            // 3 → completed
                // The emission that trips the takeWhile. It is never counted — the guard runs
                // before the collector — so the trip saw three, and three is what must be reported.
                locations.emit(stationaryFix(lat = 40.002, lon = -3.7))
                job.join()

                val exit = antilog.lines.lastOrNull { it.contains("coordinator.invoke() EXITED") }
                assertNotNull(exit, "the session must announce its own ending")
                assertTrue(
                    exit.contains("locationCount=3"),
                    "three fixes were processed, the line must say so — was: $exit",
                )
            } finally {
                Napier.takeLogarithm()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** [DET-EXIT-LINE-COUNTS-NOTHING-001] The closing line's only observer. `performLog` is
     *  `protected` on `Antilog`, so the sink is the way in; the tests that own it remove it in a
     *  `finally`, because `Napier.base` is global. */
    private class RecordingAntilog : Antilog() {
        val lines = mutableListOf<String>()
        override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
            message?.let { lines += it }
        }
    }

    private fun stationaryFix(lat: Double, lon: Double): GpsPoint =
        GpsPoint(latitude = lat, longitude = lon, accuracy = 5f, timestamp = 0L, speed = 0f)

    /** ~900 m north of the pin at 40.0, on a stream too SPARSE for the speed-based drive proof
     *  (fixes 90 s apart → no look-back candidate ever falls inside the 20–60 s window), with a
     *  continuous arrival so the anchor is witnessed normally. The field 2026-08-14 22:56 shape.
     *  [DET-SHORT-HOP-PROOF-001] */
    private suspend fun driveSparseShortHop(
        locations: MutableSharedFlow<GpsPoint>,
        /** Speed every rolling fix reports. Pedestrian values make the stream witness no departure
         *  at all, leaving the bare geometry — the anti-resurrection shape.
         *  [DET-UNVERIFIED-ARM-DRIVE-PROOF-001] */
        speedMps: Float = 8.3f,
        advanceClock: (Long) -> Unit,
    ) {
        locations.emit(GpsPoint(40.0, -3.7, accuracy = 10f, timestamp = 0L, speed = speedMps))
        advanceClock(90_000L)
        locations.emit(GpsPoint(40.0040, -3.7, accuracy = 10f, timestamp = 90_000L, speed = speedMps))
        advanceClock(180_000L)
        locations.emit(GpsPoint(40.0081, -3.7, accuracy = 10f, timestamp = 180_000L, speed = speedMps))
        // Final manoeuvre + arrival at rest, witnessed without a hole.
        advanceClock(200_000L)
        locations.emit(GpsPoint(SHORT_HOP_PARKED_LAT, -3.7, accuracy = 8f, timestamp = 200_000L, speed = 3f))
        advanceClock(215_000L)
        locations.emit(GpsPoint(SHORT_HOP_PARKED_LAT, -3.7, accuracy = 6f, timestamp = 215_000L, speed = 0f))
    }

    /** [DET-UNVERIFIED-ARM-DRIVE-PROOF-001] The LATE-ARMED hop of field 2026-08-15 21:26 (Redmi,
     *  session 1786821963745): the EXIT never arrived, a sentry-wake armed the session with the car
     *  already ~1.1 km out, and the stream saw only the drive's tail — three credible fixes at
     *  25-30 km/h spanning 10 s. Deliberately too tight for `corroboratesDrive` (its look-back
     *  window starts at 20 s), so only the displacement from the pin can prove this drive. */
    private suspend fun driveLateArmedHop(
        locations: MutableSharedFlow<GpsPoint>,
        advanceClock: (Long) -> Unit,
    ) {
        advanceClock(25_000L)
        locations.emit(GpsPoint(40.0098, -3.7, accuracy = 8.9f, timestamp = 25_000L, speed = 7.1f))
        advanceClock(33_000L)
        locations.emit(GpsPoint(40.0100, -3.7, accuracy = 23.4f, timestamp = 33_000L, speed = 8.2f))
        advanceClock(35_000L)
        locations.emit(GpsPoint(40.0101, -3.7, accuracy = 24.2f, timestamp = 35_000L, speed = 8.4f))
        // Final manoeuvre + arrival at rest, witnessed without a hole.
        advanceClock(60_000L)
        locations.emit(GpsPoint(LATE_ARM_PARKED_LAT, -3.7, accuracy = 8f, timestamp = 60_000L, speed = 3f))
        advanceClock(75_000L)
        locations.emit(GpsPoint(LATE_ARM_PARKED_LAT, -3.7, accuracy = 6f, timestamp = 75_000L, speed = 0f))
    }

    /** [DET-DRIVE-PROOF-001] A drive the session speed statistic BELIEVES: a track with real
     *  timestamps that covers a trip's worth of ground across the look-back window (a lone
     *  speed-carrying fix no longer counts as measured driving — that was the at-home FP,
     *  field 2026-07-27). Emits 6 fixes 5 s apart (≈ 278 m over 25 s, progressing), approaching
     *  [toLat] from the south and ENDING at ([toLat], [lon]) so each test's downstream geometry
     *  is untouched. */
    /** [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] 6,5 m/s ≈ 23 km/h — a brisk cyclist, right where the
     *  two real field rides of 2026-08-19 actually measured (credible peaks 17,6 and 21,3 km/h).
     *  Above `minimumTripSpeedMps`, so it still clears every threshold calibrated for a pedestrian
     *  — which is the whole reason a bicycle is dangerous to this detector — and far below the
     *  motor band, so the veto it triggers is a veto the measurement agrees with. */
    private val BICYCLE_SPEED_MPS = 6.5f

    private suspend fun emitCorroboratedDrive(
        locations: MutableSharedFlow<GpsPoint>,
        toLat: Double = 40.0,
        lon: Double = -3.7,
        // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] The band the ride is made of. The default is a
        // modest car; a BICYCLE test must pass [BICYCLE_SPEED_MPS], because the measured motor band
        // now refutes a human-powered claim and a fixture pedalling at 39,6 km/h for 35 s would be
        // asserting something the two real field rides never did (both measured ZERO ms above
        // 40 km/h, with credible peaks of 17,6 and 21,3 km/h).
        speedMps: Float = 11f,
    ) {
        // 8 fixes × 5 s = 35 s of in-band GPS time: enough to corroborate the drive AND to satisfy
        // the sustained-drive clock (`sustainedDriveProofMs`, 30 s) [DET-MOTOR-PROOF-001].
        val fixes = 8
        // Ground rate must match the declared speed or `corroboratesDrive` sees a mirage:
        // 1e-5 degrees of latitude ≈ 1,11 m, so 5 s at `speedMps` is speedMps * 5 / 111_000 deg.
        val stepDeg = speedMps * 5.0 / 111_000.0
        repeat(fixes) { i ->
            locations.emit(
                GpsPoint(
                    latitude = toLat - stepDeg * (fixes - 1 - i),
                    longitude = lon,
                    accuracy = 5f,
                    timestamp = i * 5_000L,
                    speed = speedMps,
                )
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // [DET-STOP-BUTTON-001] "Parar detección" — the user's own veto
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_end_the_session_as_stopped_by_user_when_the_user_stops_detection() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            env.coordinator.onUserStoppedDetection()
            job.cancelAndJoin()

            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("stopped_by_user", ended.outcome)
            assertEquals("stopped_by_user", env.coordinator.lastSessionOutcome)
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "a user stop plants nothing",
            )
        }

    @Test
    fun should_not_plant_the_held_confirm_when_the_user_stops_detection() =
        runTest(UnconfinedTestDispatcher()) {
            // The case that makes the button honest: the user hits "Parar" while a confirm is HELD
            // in the post-confirm window. Cancelling the job alone would let the finally's watchdog
            // ([DET-AUDIT-002 T7]) finalize it — planting exactly the pin the user just refused.
            val holdConfig = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = holdConfig)
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress ~33 m
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "precondition: the confirm is held, not yet saved",
            )

            env.coordinator.onUserStoppedDetection()
            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "the held confirm must be dropped, not finalized by the teardown watchdog",
            )
            assertEquals(
                "stopped_by_user",
                env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>().single().outcome,
            )
        }

    private data class TestEnv(
        val coordinator: CoordinatorParkingDetector,
        val parkingRepo: FakeUserParkingRepository,
        val geofence: FakeGeofenceManager,
        val enrichment: FakeParkingEnrichmentScheduler,
        val notification: FakeAppNotificationManager,
        val stepDetector: FakeStepDetectorSource,
        val detectionLogger: FakeDetectionEventLogger,
    )
}
