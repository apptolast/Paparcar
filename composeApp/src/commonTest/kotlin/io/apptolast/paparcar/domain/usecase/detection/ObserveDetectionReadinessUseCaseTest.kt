package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.domain.detection.StaticDetectionRuntimeState
import io.apptolast.paparcar.domain.detection.TripContext
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.DisabledReason
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.permissions.RequiredPermission
import io.apptolast.paparcar.fakes.FakeAppPreferences
import io.apptolast.paparcar.fakes.FakeBluetoothScanner
import io.apptolast.paparcar.fakes.FakePermissionManager
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ObserveDetectionReadinessUseCaseTest {

    @Test
    fun `should be Disabled NO_VEHICLE when no vehicle is registered`() = runTest {
        val readiness = buildUseCase(vehicle = null).invoke().first()
        assertEquals(DetectionReadiness.Disabled(DisabledReason.NO_VEHICLE), readiness)
    }

    @Test
    fun `should be Disabled NON_PARKING_VEHICLE when active vehicle is a bike`() = runTest {
        val bike = vehicle(type = VehicleType.BIKE)
        val readiness = buildUseCase(vehicle = bike, permissions = allGranted()).invoke().first()
        assertEquals(DetectionReadiness.Disabled(DisabledReason.NON_PARKING_VEHICLE), readiness)
    }

    @Test
    fun `should be Blocked listing missing producer permissions when detection cannot run`() = runTest {
        val car = vehicle(type = VehicleType.CAR)
        val readiness = buildUseCase(vehicle = car, permissions = coreOnly()).invoke().first()
        val blocked = assertIs<DetectionReadiness.Blocked>(readiness)
        assertTrue(RequiredPermission.BACKGROUND_LOCATION in blocked.missing)
        assertTrue(RequiredPermission.ACTIVITY_RECOGNITION in blocked.missing)
    }

    @Test
    fun `should be Disabled TURNED_OFF when auto-detection is switched off in settings`() = runTest {
        val car = vehicle(type = VehicleType.CAR)
        val readiness = buildUseCase(vehicle = car, permissions = allGranted(), autoDetect = false).invoke().first()
        assertEquals(DetectionReadiness.Disabled(DisabledReason.TURNED_OFF), readiness)
    }

    @Test
    fun `TURNED_OFF wins over Blocked — no permission nag when detection is off by choice`() = runTest {
        val car = vehicle(type = VehicleType.CAR)
        // Permissions missing AND auto-detection off: the user's intent (off) takes precedence.
        val readiness = buildUseCase(vehicle = car, permissions = coreOnly(), autoDetect = false).invoke().first()
        assertEquals(DetectionReadiness.Disabled(DisabledReason.TURNED_OFF), readiness)
    }

    @Test
    fun `should be Ready with coordinator strategy when armed idle and no session`() = runTest {
        val car = vehicle(type = VehicleType.CAR)
        val readiness = buildUseCase(vehicle = car, permissions = allGranted()).invoke().first()
        assertEquals(DetectionReadiness.Ready(ParkingStrategy.COORDINATOR), readiness)
    }

    @Test
    fun `should be Monitoring when a tracking job is running`() = runTest {
        val car = vehicle(type = VehicleType.CAR)
        val readiness = buildUseCase(
            vehicle = car,
            permissions = allGranted(),
            running = true,
        ).invoke().first()
        assertEquals(DetectionReadiness.Monitoring(ParkingStrategy.COORDINATOR), readiness)
    }

    @Test
    fun `should carry the candidate phase on Monitoring`() = runTest {
        val car = vehicle(type = VehicleType.CAR)
        val readiness = buildUseCase(
            vehicle = car,
            permissions = allGranted(),
            running = true,
            phase = DetectionPhase.Candidate,
        ).invoke().first()
        val monitoring = assertIs<DetectionReadiness.Monitoring>(readiness)
        assertEquals(DetectionPhase.Candidate, monitoring.phase)
    }

    @Test
    fun `should default Monitoring phase to Driving`() = runTest {
        val car = vehicle(type = VehicleType.CAR)
        val readiness = buildUseCase(vehicle = car, permissions = allGranted(), running = true).invoke().first()
        val monitoring = assertIs<DetectionReadiness.Monitoring>(readiness)
        assertEquals(DetectionPhase.Driving, monitoring.phase)
    }

    @Test
    fun `should be Parked when an active session has a geofence`() = runTest {
        val car = vehicle(type = VehicleType.CAR)
        val session = session(geofenceId = "gf-1")
        val readiness = buildUseCase(
            vehicle = car,
            permissions = allGranted(),
            session = session,
        ).invoke().first()
        val parked = assertIs<DetectionReadiness.Parked>(readiness)
        assertEquals("gf-1", parked.session.geofenceId)
    }

    @Test
    fun `should prefer Blocked over Parked when permission revoked mid-session`() = runTest {
        val car = vehicle(type = VehicleType.CAR)
        val session = session(geofenceId = "gf-1")
        val readiness = buildUseCase(
            vehicle = car,
            permissions = coreOnly(),
            session = session,
        ).invoke().first()
        assertIs<DetectionReadiness.Blocked>(readiness)
    }

    @Test
    fun `should be Blocked with foreground location missing when GPS is off`() = runTest {
        // GPS toggle off surfaces as a CORE block (FOREGROUND_LOCATION), so Home shows the
        // "turn on location" row instead of force-navigating. [DET-READY-001i]
        val car = vehicle(type = VehicleType.CAR)
        val gpsOff = io.apptolast.paparcar.domain.permissions.AppPermissionState(
            hasLocationPermission = true,
            hasNotificationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isLocationServicesEnabled = false,
        )
        val readiness = buildUseCase(vehicle = car, permissions = gpsOff).invoke().first()
        val blocked = assertIs<DetectionReadiness.Blocked>(readiness)
        assertTrue(RequiredPermission.FOREGROUND_LOCATION in blocked.missing)
    }

    // ── Trip outranks ANOTHER car's parked session [DET-READY-TRIP-OVER-PARKED-001] ──

    @Test
    fun `should be Monitoring when a second car is parked and the tracked car already left`() = runTest {
        // The user's field case: the BT car sits parked while the other car is being driven. Its
        // session masked the trip, so Home never drew the puck, the route or the follow camera.
        val driven = vehicle(type = VehicleType.CAR)
        val parkedCar = otherVehicle()
        val readiness = buildUseCase(
            vehicle = driven,
            extraVehicles = listOf(parkedCar),
            permissions = allGranted(),
            sessions = listOf(session(geofenceId = "gf-other", id = "s-other", vehicleId = parkedCar.id)),
            running = true,
            trip = TripContext(departurePoint = point(), departingVehicleId = driven.id),
        ).invoke().first()
        val monitoring = assertIs<DetectionReadiness.Monitoring>(readiness)
        assertEquals(driven.id, monitoring.departingVehicleId)
    }

    @Test
    fun `should stay Parked when the tracked car itself has not left yet`() = runTest {
        // Armed at the car (AR ENTER waiting for ride proof): the session is still active, so no
        // measured movement has proved a drive. The banner must not claim one.
        val car = vehicle(type = VehicleType.CAR)
        val readiness = buildUseCase(
            vehicle = car,
            permissions = allGranted(),
            sessions = listOf(session(geofenceId = "gf-1", id = "s-1", vehicleId = car.id)),
            running = true,
            trip = TripContext(departurePoint = point(), departingVehicleId = car.id),
        ).invoke().first()
        val parked = assertIs<DetectionReadiness.Parked>(readiness)
        assertEquals("s-1", parked.session.id)
    }

    @Test
    fun `should stay Parked when a trip runs without a resolved departing vehicle`() = runTest {
        // Manual arms carry no trip context — nothing to attribute, so the parked reading holds.
        val car = vehicle(type = VehicleType.CAR)
        val readiness = buildUseCase(
            vehicle = car,
            permissions = allGranted(),
            sessions = listOf(session(geofenceId = "gf-1", id = "s-1", vehicleId = car.id)),
            running = true,
            trip = null,
        ).invoke().first()
        assertIs<DetectionReadiness.Parked>(readiness)
    }

    @Test
    fun `should report the most recently parked session as the Parked payload`() = runTest {
        // Same subject Home focuses on, so the watch badge describes the car the user is looking at.
        val car = vehicle(type = VehicleType.CAR)
        val other = otherVehicle()
        val readiness = buildUseCase(
            vehicle = car,
            extraVehicles = listOf(other),
            permissions = allGranted(),
            sessions = listOf(
                session(geofenceId = "gf-old", id = "s-old", vehicleId = car.id, parkedAt = 1_000L),
                session(geofenceId = null, id = "s-new", vehicleId = other.id, parkedAt = 9_000L),
            ),
        ).invoke().first()
        val parked = assertIs<DetectionReadiness.Parked>(readiness)
        assertEquals("s-new", parked.session.id)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        vehicle: Vehicle?,
        permissions: io.apptolast.paparcar.domain.permissions.AppPermissionState = FakePermissionManager.allDenied(),
        session: UserParking? = null,
        sessions: List<UserParking> = emptyList(),
        extraVehicles: List<Vehicle> = emptyList(),
        running: Boolean = false,
        phase: DetectionPhase = DetectionPhase.Driving,
        trip: TripContext? = null,
        autoDetect: Boolean = true,
    ): ObserveDetectionReadinessUseCase {
        val vehicleRepo = FakeVehicleRepository(defaultVehicle = vehicle, extraVehicles = extraVehicles)
        val parkingRepo = FakeUserParkingRepository(initialSession = session, initialSessions = sessions)
        val permissionManager = FakePermissionManager().apply { emit(permissions) }
        val resolver = ParkingStrategyResolver(
            vehicleRepository = vehicleRepo,
            bluetoothScanner = FakeBluetoothScanner(bluetoothEnabled = false),
        )
        return ObserveDetectionReadinessUseCase(
            vehicleRepository = vehicleRepo,
            userParkingRepository = parkingRepo,
            permissionManager = permissionManager,
            detectionRuntime = StaticDetectionRuntimeState(running = running, phase = phase, trip = trip),
            strategyResolver = resolver,
            appPreferences = FakeAppPreferences(initialAutoDetect = autoDetect),
        )
    }

    private fun allGranted() = FakePermissionManager.allGranted()

    private fun coreOnly() = io.apptolast.paparcar.domain.permissions.AppPermissionState(
        hasLocationPermission = true,
        hasNotificationPermission = true,
        hasBackgroundLocationPermission = false,
        hasActivityRecognitionPermission = false,
        isLocationServicesEnabled = true,
    )

    private fun vehicle(type: VehicleType) = Vehicle(
        id = "v-1",
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        vehicleType = type,
        isActive = true,
    )

    /** A second car in the fleet — the one left parked while the first is driven. */
    private fun otherVehicle() = Vehicle(
        id = "v-2",
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        vehicleType = VehicleType.CAR,
        isActive = false,
    )

    private fun point() = GpsPoint(latitude = 40.0, longitude = -3.7, accuracy = 0f, timestamp = 0L, speed = 0f)

    private fun session(
        geofenceId: String?,
        id: String = "s-1",
        vehicleId: String = "v-1",
        parkedAt: Long = 0L,
    ) = UserParking(
        id = id,
        userId = "u-1",
        vehicleId = vehicleId,
        location = point().copy(timestamp = parkedAt),
        geofenceId = geofenceId,
        isActive = true,
    )
}
