@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.rndeveloper.paparcar.domain.detection.coordinator

import com.rndeveloper.paparcar.domain.detection.CoordinatorParkingDetector
import com.rndeveloper.paparcar.domain.detection.HoldAction
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateUnattendedParkingSaveUseCase
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.FakeDetectionEventLogger
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeParkingEnrichmentScheduler
import com.rndeveloper.paparcar.fakes.FakeStepDetectorSource
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [DET-PRECEDENCE-MUST-BE-TESTABLE-001] Characterization of the **ORDER** of the branches inside
 * the `collect` of [CoordinatorParkingDetector] — not of any single branch.
 *
 * **Why this file exists.** The loop evaluates ~10 branches and the FIRST one that applies wins.
 * That order IS behaviour: permute two entries and a different pin gets planted. Today the order is
 * nothing but the physical position of the blocks inside a 700-line method, the class KDoc that
 * documents it is **wrong** (it omits the hold, which runs first, and calls the user-confirm branch
 * a short-circuit when three branches outrank it), and **no test fails if the order changes**.
 *
 * The deep refactor moves those branches into an ordered `List<SessionStage>`. These asserts are the
 * net under that move: when the stages exist, this file becomes `StageOrderTest` **without a single
 * assert being edited** — which is the acceptance criterion of the whole refactor
 * (`docs/detection/10-plan-refactor.md` §0.3).
 *
 * **The measured order today** (branch → what it does):
 *  1. hold resolution · 2. false-ENTER abort · 3. no-movement budget · 4. vehicle attribution ·
 *  5. user confirm · 6. pre-drive skip · 7-10. response timeout / candidate / fast confirm / scoring
 *
 * **Rule of admission: every test here must DISCRIMINATE.** A precedence test that would pass under
 * any order is worthless — that failure mode is logged as bug #8 of the refactor audit (three tests
 * passing with comments describing arithmetic that no longer exists). Each test therefore states,
 * in its comment, the value the OPPOSITE order would produce, and was verified by hand to fail when
 * the pair it pins is permuted.
 *
 * Four tests here DISCRIMINATE (each verified by neutralising the branch it claims wins, and
 * watching it fail); the fifth is labelled a regression guard because three neutralisations left it
 * green — the reason is written in its own comment and is itself a finding.
 *
 * Two pairs are deliberately absent because they are **unreachable**, not because they are safe:
 *  - hold ↔ false-ENTER abort: opening a hold requires proven driving; the abort requires its absence.
 *  - hold ↔ user's bounded zone: [DET-GAP-ANCHOR-001] forbids a gap-born anchor from opening a hold
 *    at all (a hole yields a prompt, never a hold), so the two can never compete.
 */
class StagePrecedenceCharacterizationTest {

    private val authSession = FakeAuthRepository.authenticatedSession(userId = "user-1")

    /** Mirrors `CoordinatorParkingDetectorTest.setup` — same fakes, same wiring. Kept local so this
     *  file survives the move to `stages/` on its own. */
    private fun setup(
        config: ParkingDetectionConfig,
        clock: () -> Long,
        extraVehicles: List<Vehicle> = emptyList(),
    ): TestEnv {
        val auth = FakeAuthRepository(initialSession = authSession)
        val vehicleRepo = FakeVehicleRepository(
            defaultVehicle = Vehicle(
                id = "v-1",
                userId = "user-1",
                sizeCategory = VehicleSize.MEDIUM_SUV,
            ),
            extraVehicles = extraVehicles,
        )
        val parkingRepo = FakeUserParkingRepository()
        val geofence = FakeGeofenceManager()
        val notification = FakeAppNotificationManager()
        val confirmParking = ConfirmParkingUseCase(
            userParkingRepository = parkingRepo,
            vehicleRepository = vehicleRepo,
            zoneRepository = FakeZoneRepository(),
            geofenceService = geofence,
            enrichmentScheduler = FakeParkingEnrichmentScheduler(),
            authRepository = auth,
            config = config,
            departureEventBus = FakeDepartureEventBus(),
        )
        val stepDetector = FakeStepDetectorSource()
        val detectionLogger = FakeDetectionEventLogger()
        val coordinator = CoordinatorParkingDetector(
            calculateParkingConfidence = CalculateParkingConfidenceUseCase(config),
            confirmParking = confirmParking,
            notifyParkingConfirmation = NotifyParkingConfirmationUseCase(
                notificationPort = notification,
                vehicleRepository = vehicleRepo,
            ),
            notificationPort = notification,
            vehicleRepository = vehicleRepo,
            stepDetector = stepDetector,
            config = config,
            detectionEventLogger = detectionLogger,
            evaluateParkingDecision = EvaluateParkingDecisionUseCase(config),
            // [DET-DI-DETECTION-MODULE-001] Was the coordinator's own constructor default; the
            // instance is identical, it is just built where it can be seen.
            evaluateUnattendedParkingSave = EvaluateUnattendedParkingSaveUseCase(config),
            // These three used to default to null. They still are null here — the precedence
            // pairs never reach the Home surface or the deduced-departure lanes — but now the
            // characterization says which lanes it is NOT holding.
            phaseSink = null,
            finalizeDeducedDeparture = null,
            retractDeducedDeparture = null,
            clock = clock,
        )
        return TestEnv(coordinator, parkingRepo, stepDetector, detectionLogger)
    }

    private data class TestEnv(
        val coordinator: CoordinatorParkingDetector,
        val parkingRepo: FakeUserParkingRepository,
        val stepDetector: FakeStepDetectorSource,
        val detectionLogger: FakeDetectionEventLogger,
    )

    private fun fix(lat: Double, speed: Float = 0f, accuracy: Float = 5f) =
        GpsPoint(latitude = lat, longitude = -3.7, accuracy = accuracy, timestamp = 0L, speed = speed)

    // ═════════════════════════════════════════════════════════════════════════
    // P0.1 · Precedence pairs
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * **Branch 1 (hold) outranks branch 5 (user confirm).**
     *
     * A "Sí" tapped while a tentative confirm is HELD is finalised BY THE HOLD, which plants
     * `pending.location` — the spot where the car was judged to be parked. The user-confirm branch,
     * had it run first, chooses its own location (the witnessed stop, or the current fix) and would
     * have planted the pin where the PHONE is at answer time.
     *
     * The existing `should_save_with_user_reliability_when_user_confirms_during_the_hold` asserts
     * the reliability (1.0) — which both orders produce, so it does not pin the order. **Position
     * is what discriminates**, and that is what this test adds.
     */
    @Test
    fun should_plant_the_held_pin_not_the_answer_fix_when_the_user_says_yes_during_a_hold() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 1_000_000L
            val config = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = config, clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive, stop, get out: the egress opens a tentative confirm on the parked car.
            locations.emit(fix(lat = 40.0))
            locations.emit(fix(lat = 40.002, speed = 10f))
            locations.emit(fix(lat = 40.005))
            env.stepDetector.emitSteps(8)
            locations.emit(fix(lat = 40.0053)) // egress → tentative confirm → HELD
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "held, nothing saved yet")

            // The user keeps walking — ~110 m from the car — and only then taps "Sí".
            nowMs += 10_000L
            env.coordinator.onUserConfirmedParking()
            locations.emit(fix(lat = 40.0063))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved, "a park must be saved")
            // The hold plants the pinned car position. Under the opposite order the user-confirm
            // branch would have planted ~40.0063 — where the walker was, not where the car is.
            assertTrue(
                saved.location.latitude < 40.0060,
                "the hold must plant the held car pin, not the answer fix at 40.0063 " +
                    "(got ${saved.location.latitude})",
            )
            assertEquals(
                config.reliabilityUserConfirmed,
                saved.detectionReliability ?: 0f,
                /* absoluteTolerance = */ 0.0001f,
                "an explicit answer still carries user reliability [DET-C-02]",
            )
        }

    /**
     * **Branch 2 (false-ENTER abort) outranks branch 5 (user confirm).**
     *
     * The class KDoc claims `userConfirmedParking` "short-circuits everything". It does not: with no
     * driving ever measured and a pedestrian step burst in hand, the session ABORTS and the user's
     * answer is never reached. Under the opposite order the tap would have saved a park.
     *
     * This is characterization, not endorsement: whether an explicit answer *should* outrank a
     * spurious-ENTER abort is a product question. What the refactor may not do is change it by
     * accident while moving blocks.
     */
    @Test
    fun should_abort_the_false_enter_even_when_the_user_already_said_yes() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 0L
            val config = ParkingDetectionConfig(confirmHoldMs = 0L)
            val env = setup(config = config, clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // AR misfired: the user is walking, driving speed is never reached.
            locations.emit(fix(lat = 40.0, speed = 1.2f))
            env.stepDetector.emitSteps(8)

            // The user answers "Sí" BEFORE the fix that runs the abort check.
            env.coordinator.onUserConfirmedParking()
            locations.emit(fix(lat = 40.0001, speed = 1.2f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "the pre-drive abort wins: under the opposite order the tap would have saved a park",
            )
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_false_enter", ended.outcome, "the session ends as a false ENTER")
        }

    /**
     * **Branch 3 (no-movement budget) outranks branch 5 (user confirm).**
     *
     * Same shape as the previous one, one branch lower: a session that never measured driving folds
     * when its budget runs out even if the user already answered. Under the opposite order the
     * outcome would be a saved park instead of `aborted_no_movement`.
     */
    @Test
    fun should_fold_the_no_movement_budget_even_when_the_user_already_said_yes() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 0L
            val config = ParkingDetectionConfig(confirmHoldMs = 0L)
            val env = setup(config = config, clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(fix(lat = 40.0))
            env.coordinator.onUserConfirmedParking()

            nowMs = config.maxNoMovementMs + 1_000L
            locations.emit(fix(lat = 40.0))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "the budget fold wins: under the opposite order the tap would have saved a park",
            )
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_no_movement", ended.outcome)
        }

    /**
     * **Branch 4 (vehicle attribution) runs before branch 5 (user confirm) — in the SAME fix.**
     *
     * This is the pair that also pins the fall-through (P0.2): branch 4 resolves the vehicle and
     * does **not** stop the iteration, so branch 5 saves using what branch 4 just resolved.
     *
     * The discriminator is the vehicle: the answer is armed BEFORE the first driving fix, so
     * attribution and confirmation land on the very same fix. Today the park belongs to the
     * NOMINATING vehicle (a van). Under the opposite order `activeVehicleId` would still be null at
     * save time and the park would fall back to the default car.
     */
    @Test
    fun should_resolve_the_vehicle_before_confirming_within_the_same_fix() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 1_000_000L
            val config = ParkingDetectionConfig(confirmHoldMs = 0L)
            val env = setup(
                config = config,
                clock = { nowMs },
                extraVehicles = listOf(
                    Vehicle(
                        id = "v-nominator",
                        userId = "user-1",
                        sizeCategory = VehicleSize.VAN_HIGH,
                    ),
                ),
            )
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, nominatingVehicleId = "v-nominator") }

            locations.emit(fix(lat = 40.0))
            // The answer is already in hand when the FIRST driving fix arrives: on that single fix
            // the loop must resolve the vehicle (branch 4) and only then confirm (branch 5).
            env.coordinator.onUserConfirmedParking()
            locations.emit(fix(lat = 40.002, speed = 10f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved, "a park must be saved")
            assertEquals(
                "v-nominator",
                saved.vehicleId,
                "attribution ran first: under the opposite order the id would be unresolved",
            )
            assertEquals(
                VehicleSize.VAN_HIGH,
                saved.sizeCategory,
                "end-to-end proof that the NOMINATOR was resolved before the save, not the default car",
            )
        }

    // ═════════════════════════════════════════════════════════════════════════
    // P0.2 · Fall-through: a branch that resolves without stopping the iteration
    //
    // The fall-through is pinned by `should_resolve_the_vehicle_before_confirming_within_the_same_fix`
    // above: branch 4 resolves the vehicle, does NOT stop the iteration, and branch 5 saves using
    // what it just resolved — verified discriminating (neutralising branch 4 makes it fail).
    // The test below is a REGRESSION GUARD, not a second proof; see its comment.
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * **Regression guard — deliberately NOT discriminating.** Its job is to prove that a hold
     * discarded mid-window neither confirms the wrong spot nor ends the session: the scenario the
     * hold exists for [DET-C-02] — park → walk to a kiosk → drive on and park properly — must end
     * with exactly one pin, at the FINAL spot.
     *
     * **Honesty about what it does not prove** (measured, not assumed — this test passed on the
     * first run, so all three neutralisations below were tried by hand):
     *  - Forcing `drivingResumed = false` leaves it **green**, with the same pin: the stale-at-settle
     *    discard takes over and produces an identical outcome. So it does not isolate the drove-off
     *    flavour of the discard.
     *  - Killing the fall-through of either discard leaves it **green** too: the real park is
     *    confirmed on a LATER fix, so nothing here depends on the same-iteration continuation.
     *
     * That first result is worth writing down, because it is not a weakness of the test: the
     * drove-off discard emits **no** `DetectionEvent` — it is one of the mute branches catalogued in
     * `04-diagnostico.md`. There is no external observable that separates it from its sibling, in a
     * test or in the field. Proposal 3 of the target architecture (mute branches become notes the
     * tap emits) is what would make it distinguishable.
     */
    @Test
    fun should_pin_the_final_spot_when_a_hold_is_discarded_mid_window() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 1_000_000L
            val config = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = config, clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // First stop: drive, park, get out → tentative confirm opens and HOLDS.
            locations.emit(fix(lat = 40.0))
            locations.emit(fix(lat = 40.002, speed = 10f))
            locations.emit(fix(lat = 40.005))
            env.stepDetector.emitSteps(8)
            locations.emit(fix(lat = 40.0053))
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "held, nothing saved yet")

            // The errand ends: the car drives off well inside the hold window.
            nowMs += 20_000L
            locations.emit(fix(lat = 40.008, speed = 12f))
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "the discard must not confirm the kiosk stop",
            )

            // Real park, further along, with its own egress.
            nowMs += 20_000L
            locations.emit(fix(lat = 40.012, speed = 11f))
            locations.emit(fix(lat = 40.015))
            env.stepDetector.emitSteps(8)
            locations.emit(fix(lat = 40.0153))
            nowMs += 120_001L
            locations.emit(fix(lat = 40.0153))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "exactly one park: the discarded hold must not leave a second save behind",
            )
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved, "a park must be saved")
            assertTrue(
                saved.location.latitude > 40.010,
                "the session kept detecting after the discard and pinned the FINAL spot " +
                    "(got ${saved.location.latitude}, the discarded kiosk stop was ~40.005)",
            )
        }

    /**
     * **Branch 1 (hold) outranks the steps+egress fast lane — the pair Fase 0 could not write.**
     *
     * `DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001` set this scenario up and measured it as
     * **not observable**: neutralising the hold's `return@collect` produced byte-identical output
     * (same saves, same event census), so the pair was documented as unwritable and dropped. The
     * cause was not the scenario — it was that the hold emitted nothing, so a second pass through
     * the fast lane left no trace of itself.
     *
     * [DET-HOLD-BRANCHES-MUST-SPEAK-001] gives the hold a lifecycle event, and with it the pair
     * becomes exactly one assertion: **the hold opens ONCE**. Under the opposite order the fix
     * would fall through to the fast lane, call `beginConfirm` again, and restart the two-minute
     * clock on a pin that had already earned its confirm — a park delayed, and in a starved stream
     * a park lost.
     */
    @Test
    fun should_swallow_the_fix_while_a_tentative_confirm_is_holding() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 1_000_000L
            val config = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = config, clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive, stop, get out: the egress opens a tentative confirm on the parked car.
            locations.emit(fix(lat = 40.0))
            locations.emit(fix(lat = 40.002, speed = 10f))
            locations.emit(fix(lat = 40.005))
            env.stepDetector.emitSteps(8)
            locations.emit(fix(lat = 40.0053)) // egress → tentative confirm → HELD

            // Keep walking away with fresh steps: every one of these fixes satisfies the fast lane
            // again (steps in hand, displacement growing). Only the hold's early return stops them
            // from re-opening a confirm.
            repeat(4) { i ->
                nowMs += 10_000L
                env.stepDetector.emitSteps(6)
                locations.emit(fix(lat = 40.0056 + i * 0.0003))
            }

            job.cancelAndJoin()

            val opens = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.Hold>()
                .filter { it.action == HoldAction.OPENED }
            assertEquals(
                1,
                opens.size,
                "the hold must swallow every fix while it runs: a second OPENED means the fast lane " +
                    "re-fired and restarted the 2-min clock on an already-earned pin",
            )
        }
}
