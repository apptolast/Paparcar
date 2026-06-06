package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.detection.TransitionAction
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeBluetoothScanner
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakeStepDetectorSource
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import io.apptolast.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class HandleVehicleTransitionUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val epochMs = 1_000_000L

    private val carVehicle = Vehicle(
        id = "v-car",
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM,
        vehicleType = VehicleType.CAR,
        bluetoothDeviceId = null,
    )
    private val carWithBt = carVehicle.copy(bluetoothDeviceId = "AA:BB:CC:DD")
    private val scooterVehicle = carVehicle.copy(vehicleType = VehicleType.SCOOTER)

    // ── ENTER debounce ────────────────────────────────────────────────────────

    @Test
    fun should_return_Ignore_when_enter_received_while_already_in_vehicle() = runTest {
        val env = buildEnv(carVehicle, btEnabled = false)
        env.useCase(isEnter = true, epochMs = epochMs)   // first ENTER → not ignored

        val second = env.useCase(isEnter = true, epochMs = epochMs + 1_000L)

        assertIs<TransitionAction.Ignore>(second)
    }

    // ── ENTER — DepartureEventBus ─────────────────────────────────────────────

    @Test
    fun should_dispatch_vehicleEntered_to_bus_on_first_enter() = runTest {
        val env = buildEnv(carVehicle, btEnabled = false)

        env.useCase(isEnter = true, epochMs = epochMs)

        assertEquals(epochMs, env.bus.lastVehicleEnteredAt)
    }

    @Test
    fun should_not_dispatch_vehicleEntered_on_debounced_enter() = runTest {
        val env = buildEnv(carVehicle, btEnabled = false)
        env.useCase(isEnter = true, epochMs = epochMs)
        env.bus.reset()

        env.useCase(isEnter = true, epochMs = epochMs + 1_000L)

        assertEquals(null, env.bus.lastVehicleEnteredAt)
    }

    // ── ENTER — strategy routing ──────────────────────────────────────────────

    @Test
    fun should_return_StartCoordinatorDetection_when_strategy_is_COORDINATOR() = runTest {
        val env = buildEnv(carVehicle, btEnabled = false)

        val action = env.useCase(isEnter = true, epochMs = epochMs)

        assertIs<TransitionAction.StartCoordinatorDetection>(action)
    }

    @Test
    fun should_return_StopIfIdle_when_strategy_is_BLUETOOTH() = runTest {
        val env = buildEnv(carWithBt, btEnabled = true)

        val action = env.useCase(isEnter = true, epochMs = epochMs)

        assertIs<TransitionAction.StopIfIdle>(action)
    }

    @Test
    fun should_return_StopIfIdle_when_strategy_is_NONE() = runTest {
        val env = buildEnv(scooterVehicle, btEnabled = false)

        val action = env.useCase(isEnter = true, epochMs = epochMs)

        assertIs<TransitionAction.StopIfIdle>(action)
    }

    // ── EXIT ──────────────────────────────────────────────────────────────────

    @Test
    fun should_return_StopIfIdle_on_exit() = runTest {
        val env = buildEnv(carVehicle, btEnabled = false)

        val action = env.useCase(isEnter = false, epochMs = epochMs)

        assertIs<TransitionAction.StopIfIdle>(action)
    }

    @Test
    fun should_allow_new_enter_after_exit_resets_debounce() = runTest {
        val env = buildEnv(carVehicle, btEnabled = false)
        env.useCase(isEnter = true, epochMs = epochMs)
        env.useCase(isEnter = false, epochMs = epochMs + 1_000L)

        val reenter = env.useCase(isEnter = true, epochMs = epochMs + 2_000L)

        assertIs<TransitionAction.StartCoordinatorDetection>(reenter)
        assertNotNull(env.bus.lastVehicleEnteredAt)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildEnv(vehicle: Vehicle, btEnabled: Boolean): TestEnv {
        val auth = FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession("u-1"))
        val vehicleRepo = FakeVehicleRepository(defaultVehicle = vehicle)
        val btScanner = FakeBluetoothScanner(bluetoothEnabled = btEnabled)
        val strategyResolver = ParkingStrategyResolver(vehicleRepo, btScanner)
        val bus = FakeDepartureEventBus()
        val notification = FakeAppNotificationManager()
        val coordinator = ParkingDetectionCoordinator(
            calculateParkingConfidence = CalculateParkingConfidenceUseCase(config),
            confirmParking = ConfirmParkingUseCase(
                userParkingRepository = FakeUserParkingRepository(),
                vehicleRepository = vehicleRepo,
                zoneRepository = FakeZoneRepository(),
                geofenceService = FakeGeofenceManager(),
                notificationPort = notification,
                enrichmentScheduler = FakeParkingEnrichmentScheduler(),
                authRepository = auth,
                config = config,
                departureEventBus = bus,
            ),
            notifyParkingConfirmation = NotifyParkingConfirmationUseCase(notification, vehicleRepo),
            notificationPort = notification,
            vehicleRepository = vehicleRepo,
            stepDetector = FakeStepDetectorSource(),
            config = config,
        )
        val useCase = HandleVehicleTransitionUseCase(strategyResolver, coordinator, bus)
        return TestEnv(useCase, bus)
    }

    private data class TestEnv(
        val useCase: HandleVehicleTransitionUseCase,
        val bus: FakeDepartureEventBus,
    )
}
