@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.domain.coordinator

import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeActivityRecognitionManager
import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeDetectionEventLogger
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
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
 * [io.apptolast.paparcar.domain.usecase.parking.ParkingFlowIntegrationTest] instead
 * of being faked here.
 */
class CoordinatorParkingDetectorTest {

    private val authSession = FakeAuthRepository.authenticatedSession(userId = "user-1")
    // confirmHoldMs = 0 → no post-confirm hold, so egress confirms fire immediately and these
    // deterministic tests stay synchronous. The hold itself is covered by dedicated tests below
    // that drive an injected clock. [DET-C-02]
    private val config = ParkingDetectionConfig(confirmHoldMs = 0L)

    private fun setup(
        config: ParkingDetectionConfig = this.config,
        clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ): TestEnv {
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
            enrichmentScheduler = enrichment,
            authRepository = auth,
            config = config,
            departureEventBus = FakeDepartureEventBus(),
            activityRecognitionManager = FakeActivityRecognitionManager(),
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
                    io.apptolast.paparcar.domain.notification.AppNotificationManager
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
            // [DET-G-04] Regression guard: the seed must NOT leak to AR_PROXIMITY / MANUAL sessions.
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
    // DET-G-05: unverified exits stay guarded; a late departure verdict upgrades
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_confirm_when_late_departure_verdict_upgrades_an_unverified_session() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-G-05] A GEOFENCE_EXIT with no vehicle evidence at arm time arms WITHOUT the
            // seed. When DepartureDetectionWorker later confirms the departure (AR ENTER delivers
            // up to ~2 min late), notifyDepartureConfirmed() seeds the RUNNING session so the
            // steps+egress path can still confirm the short-hop park.
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

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "upgraded session must confirm the park via steps+egress [DET-G-05]",
            )
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
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun stationaryFix(lat: Double, lon: Double): GpsPoint =
        GpsPoint(latitude = lat, longitude = lon, accuracy = 5f, timestamp = 0L, speed = 0f)

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
