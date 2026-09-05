package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.detection.ParkingStrategy
import com.rndeveloper.paparcar.domain.detection.ParkingStrategyResolver
import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.StaticDetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.TripContext
import com.rndeveloper.paparcar.domain.model.DetectionReadiness
import com.rndeveloper.paparcar.domain.model.DisabledReason
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.VehicleType
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeBluetoothScanner
import com.rndeveloper.paparcar.fakes.FakePermissionManager
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
        val gpsOff = com.rndeveloper.paparcar.domain.permissions.AppPermissionState(
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

    // ── The BT lane can report a trip too [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] ──

    @Test
    fun `should be Monitoring on the BT car when connected and its session is cleared`() = runTest {
        // The whole drive in the paired car used to read Ready: Monitoring meant "the Coordinator is
        // running", and under BLUETOOTH the Coordinator is suppressed by design.
        val car = btVehicle()
        val readiness = buildUseCase(
            vehicle = car,
            permissions = allGranted(),
            scanner = connectedTo(car.id),
        ).invoke().first()

        val monitoring = assertIs<DetectionReadiness.Monitoring>(readiness)
        assertEquals(ParkingStrategy.BLUETOOTH, monitoring.strategy)
        assertEquals(car.id, monitoring.departingVehicleId)
        assertEquals(DetectionPhase.Driving, monitoring.phase)
    }

    @Test
    fun `should stay Parked when the BT car is connected but still has its own session`() = runTest {
        // Getting in is not leaving. The deterministic lane gets the same bar as the probabilistic
        // one — no exception for being sure which car you are in. [DET-READY-TRIP-OVER-PARKED-001]
        val car = btVehicle()
        val readiness = buildUseCase(
            vehicle = car,
            permissions = allGranted(),
            sessions = listOf(session(geofenceId = "gf-1", vehicleId = car.id)),
            scanner = connectedTo(car.id),
        ).invoke().first()

        assertIs<DetectionReadiness.Parked>(readiness)
    }

    @Test
    fun `should be Monitoring when the BT car is connected while ANOTHER car sits parked`() = runTest {
        // A second car's parked session must not mask the trip — same reasoning that opened
        // [DET-READY-TRIP-OVER-PARKED-001] for the Coordinator, now reachable from this lane.
        val car = btVehicle()
        val other = otherVehicle()
        val readiness = buildUseCase(
            vehicle = car,
            extraVehicles = listOf(other),
            permissions = allGranted(),
            sessions = listOf(session(geofenceId = "gf-2", id = "s-other", vehicleId = other.id)),
            scanner = connectedTo(car.id),
        ).invoke().first()

        val monitoring = assertIs<DetectionReadiness.Monitoring>(readiness)
        assertEquals(car.id, monitoring.departingVehicleId)
    }

    @Test
    fun `should report Driving on a BT trip even when the coordinator phase is stale Candidate`() = runTest {
        // `phase` is the Coordinator's StateFlow. Inheriting a Candidate left over from ITS last
        // session would freeze this trip's puck where a different car once stopped.
        val car = btVehicle()
        val readiness = buildUseCase(
            vehicle = car,
            permissions = allGranted(),
            phase = DetectionPhase.Candidate,
            scanner = connectedTo(car.id),
        ).invoke().first()

        assertEquals(DetectionPhase.Driving, assertIs<DetectionReadiness.Monitoring>(readiness).phase)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `should follow the trip as soon as BT connects with nothing else emitting`() = runTest {
        // Why ONE continuous collection and not three `.first()` calls: re-subscribing would re-read
        // the connection on each new flow, so even a snapshot source would pass and the test would
        // prove nothing. Here everything else — fleet, sessions, permissions, settings, coordinator
        // runtime — is frozen, and the only thing that moves is the ACL edge. If the edge does not
        // PUSH, this collector never sees a second emission.
        val car = btVehicle()
        val scanner = FakeBluetoothScanner()
        val seen = mutableListOf<DetectionReadiness>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            buildUseCase(vehicle = car, permissions = allGranted(), scanner = scanner)
                .invoke()
                .toList(seen)
        }
        runCurrent()
        assertIs<DetectionReadiness.Ready>(seen.last())

        scanner.connectedVehicleIds = setOf(car.id)
        runCurrent()
        assertIs<DetectionReadiness.Monitoring>(seen.last())

        scanner.connectedVehicleIds = emptySet()
        runCurrent()
        assertIs<DetectionReadiness.Ready>(seen.last())
        job.cancel()
    }

    // ── Más de un coche por BT: se identifica, no se adivina ──────────────────────────────────

    @Test
    fun `should name the car connected LAST when two paired cars have a live link`() = runTest {
        // Two links can be up at once — you park the van, walk to the car, and the van's head unit
        // stays powered for a while. The newer ACL_CONNECTED is what tells them apart, and the stamp
        // is already on disk (the receiver writes it in the same breath as the connection).
        val van = btVehicle()
        val car = otherVehicle().copy(bluetoothDeviceId = "11:22:33:44:55:66")
        val readiness = buildUseCase(
            vehicle = van,
            extraVehicles = listOf(car),
            permissions = allGranted(),
            scanner = FakeBluetoothScanner(
                connectedVehicleIds = setOf(van.id, car.id),
                connectedAtMs = mapOf(van.id to 1_000L, car.id to 9_000L),
            ),
        ).invoke().first()

        assertEquals(car.id, assertIs<DetectionReadiness.Monitoring>(readiness).departingVehicleId)
    }

    @Test
    fun `should name no car when two live links cannot be ranked`() = runTest {
        // An unstamped link could be the most recent one for all we know, so it does not lose the
        // ranking — it makes the ranking unusable. The puck's glyph is an identity: the wrong car is
        // a false statement, no car is merely less information.
        val van = btVehicle()
        val car = otherVehicle().copy(bluetoothDeviceId = "11:22:33:44:55:66")
        val readiness = buildUseCase(
            vehicle = van,
            extraVehicles = listOf(car),
            permissions = allGranted(),
            scanner = FakeBluetoothScanner(
                connectedVehicleIds = setOf(van.id, car.id),
                connectedAtMs = mapOf(van.id to 1_000L),
            ),
        ).invoke().first()

        assertIs<DetectionReadiness.Ready>(readiness)
    }

    @Test
    fun `should name the only live link even with no stamp on it`() = runTest {
        // One connection needs no ordering: there is nothing to confuse it with.
        val car = btVehicle()
        val readiness = buildUseCase(
            vehicle = car,
            permissions = allGranted(),
            scanner = FakeBluetoothScanner(connectedVehicleIds = setOf(car.id)),
        ).invoke().first()

        assertEquals(car.id, assertIs<DetectionReadiness.Monitoring>(readiness).departingVehicleId)
    }

    // ── Both lanes at once: the car is named by the strongest evidence ────────────────────────

    @Test
    fun `should paint the BT car when the coordinator is following a DIFFERENT one`() = runTest {
        // The puck's glyph is an identity, so the lane that KNOWS the car by MAC outranks the one
        // that inferred it — which is also what detection itself already decided, since a paired-car
        // edge supersedes a live coordinator session.
        val btCar = btVehicle()
        val otherCar = otherVehicle()
        val readiness = buildUseCase(
            vehicle = btCar,
            extraVehicles = listOf(otherCar),
            permissions = allGranted(),
            running = true,
            trip = TripContext(departurePoint = point(), departingVehicleId = otherCar.id),
            phase = DetectionPhase.Candidate,
            scanner = connectedTo(btCar.id),
        ).invoke().first()

        val monitoring = assertIs<DetectionReadiness.Monitoring>(readiness)
        assertEquals(btCar.id, monitoring.departingVehicleId)
        // The other car's stop and the other car's kerb do not belong to this trip.
        assertEquals(DetectionPhase.Driving, monitoring.phase)
        assertEquals(null, monitoring.departurePoint)
    }

    @Test
    fun `should keep the coordinator payload when both lanes point at the SAME car`() = runTest {
        // No conflict to arbitrate: the phase and the origin describe the very car being painted, so
        // the Candidate freeze that stops the puck chasing the pedestrian must survive.
        val car = btVehicle()
        val readiness = buildUseCase(
            vehicle = car,
            permissions = allGranted(),
            running = true,
            trip = TripContext(departurePoint = point(), departingVehicleId = car.id),
            phase = DetectionPhase.Candidate,
            scanner = connectedTo(car.id),
        ).invoke().first()

        val monitoring = assertIs<DetectionReadiness.Monitoring>(readiness)
        assertEquals(car.id, monitoring.departingVehicleId)
        assertEquals(DetectionPhase.Candidate, monitoring.phase)
        assertEquals(point(), monitoring.departurePoint)
    }

    @Test
    fun `should keep the coordinator phase on a manual arm with no BT in play`() = runTest {
        // Regression guard for the lane that was already working: a manual arm carries no trip
        // context, so it names no car — but its Candidate phase is real and still has to freeze the
        // puck. Nothing about BT may quietly take that away.
        val car = vehicle(type = VehicleType.CAR)
        val readiness = buildUseCase(
            vehicle = car,
            permissions = allGranted(),
            running = true,
            phase = DetectionPhase.Candidate,
        ).invoke().first()

        assertEquals(DetectionPhase.Candidate, assertIs<DetectionReadiness.Monitoring>(readiness).phase)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** The active car, paired to a BT device — the setup the deterministic lane owns. */
    private fun btVehicle() = vehicle(type = VehicleType.CAR).copy(bluetoothDeviceId = "AA:BB:CC:DD:EE:FF")

    private fun connectedTo(vararg vehicleIds: String) =
        FakeBluetoothScanner(connectedVehicleIds = vehicleIds.toSet())


    private fun buildUseCase(
        vehicle: Vehicle?,
        permissions: com.rndeveloper.paparcar.domain.permissions.AppPermissionState = FakePermissionManager.allDenied(),
        session: UserParking? = null,
        sessions: List<UserParking> = emptyList(),
        extraVehicles: List<Vehicle> = emptyList(),
        running: Boolean = false,
        phase: DetectionPhase = DetectionPhase.Driving,
        trip: TripContext? = null,
        autoDetect: Boolean = true,
        scanner: FakeBluetoothScanner = FakeBluetoothScanner(bluetoothEnabled = false),
    ): ObserveDetectionReadinessUseCase {
        val vehicleRepo = FakeVehicleRepository(defaultVehicle = vehicle, extraVehicles = extraVehicles)
        val parkingRepo = FakeUserParkingRepository(initialSession = session, initialSessions = sessions)
        val permissionManager = FakePermissionManager().apply { emit(permissions) }
        // One scanner for both: the resolver names the strategy and the use case reads the live
        // connection off the SAME source, so a test cannot set up a fleet that is connected for one
        // and disconnected for the other. [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001]
        val resolver = ParkingStrategyResolver(
            vehicleRepository = vehicleRepo,
            bluetoothScanner = scanner,
        )
        return ObserveDetectionReadinessUseCase(
            vehicleRepository = vehicleRepo,
            userParkingRepository = parkingRepo,
            permissionManager = permissionManager,
            detectionRuntime = StaticDetectionRuntimeState(running = running, phase = phase, trip = trip),
            strategyResolver = resolver,
            appPreferences = FakeAppPreferences(initialAutoDetect = autoDetect),
            bluetoothScanner = scanner,
        )
    }

    private fun allGranted() = FakePermissionManager.allGranted()

    private fun coreOnly() = com.rndeveloper.paparcar.domain.permissions.AppPermissionState(
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
