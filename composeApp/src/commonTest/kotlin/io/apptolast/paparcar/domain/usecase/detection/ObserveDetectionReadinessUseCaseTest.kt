package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.detection.StaticDetectionRuntimeState
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.DisabledReason
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.permissions.RequiredPermission
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        vehicle: Vehicle?,
        permissions: io.apptolast.paparcar.domain.permissions.AppPermissionState = FakePermissionManager.allDenied(),
        session: UserParking? = null,
        running: Boolean = false,
    ): ObserveDetectionReadinessUseCase {
        val vehicleRepo = FakeVehicleRepository(defaultVehicle = vehicle)
        val parkingRepo = FakeUserParkingRepository(initialSession = session)
        val permissionManager = FakePermissionManager().apply { emit(permissions) }
        val resolver = ParkingStrategyResolver(
            vehicleRepository = vehicleRepo,
            bluetoothScanner = FakeBluetoothScanner(bluetoothEnabled = false),
        )
        return ObserveDetectionReadinessUseCase(
            vehicleRepository = vehicleRepo,
            userParkingRepository = parkingRepo,
            permissionManager = permissionManager,
            detectionRuntime = StaticDetectionRuntimeState(running = running),
            strategyResolver = resolver,
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

    private fun session(geofenceId: String?) = UserParking(
        id = "s-1",
        userId = "u-1",
        vehicleId = "v-1",
        location = GpsPoint(latitude = 40.0, longitude = -3.7, accuracy = 0f, timestamp = 0L, speed = 0f),
        geofenceId = geofenceId,
        isActive = true,
    )
}
