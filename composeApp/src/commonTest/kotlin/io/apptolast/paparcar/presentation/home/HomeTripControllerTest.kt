@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.location.UserLocationUi
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.fakes.FakeLocationDataSource
import io.apptolast.paparcar.fakes.FakePermissionManager
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [HomeTripController] — the driving-puck pipeline that previously lived buried in
 * HomeViewModel with zero coverage. The controller is fully self-contained: it observes its own fakes
 * (vehicles, permissions, active sessions), readiness is fed through the provider seam, and the tests
 * simply collect the cold [HomeTripController.updates] flow — no callbacks, no simulated VM sink.
 *
 * Map-matching is out of scope here (roadNetworkDataSource = null), so these tests isolate the puck +
 * trail + departure logic — including the [DET-PHASE-001] Candidate freeze and the atomic trip-end
 * reset (puck, trail AND matchedTrail cleared in one emission — the ~2.5 s matched-trail ghost is
 * impossible by construction) [ROUTE-SNAP-001].
 */
class HomeTripControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var scope: CoroutineScope

    private lateinit var location: FakeLocationDataSource
    private lateinit var permissions: FakePermissionManager
    private lateinit var vehicleRepo: FakeVehicleRepository
    private lateinit var parkingRepo: FakeUserParkingRepository
    private val readiness = MutableSharedFlow<DetectionReadiness>(extraBufferCapacity = 64)

    /** Every emission of the collected updates flow, in order. */
    private val updates = mutableListOf<TripUpdate>()

    private val vehicle = Vehicle(
        id = "veh-1",
        userId = "u1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        carbodyType = CarbodyType.SEDAN,
        isActive = true,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scope = CoroutineScope(testDispatcher)
        location = FakeLocationDataSource()
        permissions = FakePermissionManager()
        vehicleRepo = FakeVehicleRepository(defaultVehicle = vehicle)
        parkingRepo = FakeUserParkingRepository()
        updates.clear()
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun startController(corePermissions: Boolean = true) {
        if (corePermissions) permissions.emit(FakePermissionManager.allGranted())
        val controller = HomeTripController(
            observeDetectionReadiness = { readiness },
            locationDataSource = location,
            roadNetworkDataSource = null,
            vehicleRepository = vehicleRepo,
            permissionManager = permissions,
            userParkingRepository = parkingRepo,
        )
        scope.launch { controller.updates.collect { updates.add(it) } }
    }

    private fun monitoring(
        phase: DetectionPhase = DetectionPhase.Driving,
        departurePoint: GpsPoint? = null,
        departingVehicleId: String? = "veh-1",
    ) = DetectionReadiness.Monitoring(
        strategy = ParkingStrategy.COORDINATOR,
        departurePoint = departurePoint,
        departingVehicleId = departingVehicleId,
        phase = phase,
    )

    private fun uiLoc(lat: Double, lon: Double, bearing: Float? = 0f) =
        UserLocationUi(latitude = lat, longitude = lon, accuracy = 5f, speed = 1f, bearingDegrees = bearing)

    private fun gps(lat: Double, lon: Double) = GpsPoint(lat, lon, 0f, 0L, 0f)

    // ── Driving puck ──────────────────────────────────────────────────────────

    @Test
    fun `should_emit_driving_puck_at_the_fix_tagged_with_the_departing_vehicle_when_monitoring`() = runTest {
        startController()

        readiness.emit(monitoring(phase = DetectionPhase.Driving))
        location.emitUi(uiLoc(40.0, -3.0, bearing = 90f))

        val update = updates.last()
        val puck = update.puck
        assertNotNull(puck)
        assertEquals(40.0, puck.latitude)
        assertEquals(-3.0, puck.longitude)
        assertEquals(DetectionPhase.Driving, puck.phase)
        assertEquals("veh-1", puck.vehicleId)
        assertEquals(CarbodyType.SEDAN, puck.carbodyType)
        assertEquals(1, update.trail.size)
        assertEquals(40.0, update.trail.first().latitude)
    }

    @Test
    fun `should_grow_the_trail_with_each_fix_while_driving`() = runTest {
        startController()

        readiness.emit(monitoring())
        location.emitUi(uiLoc(40.0, -3.0))
        location.emitUi(uiLoc(40.001, -3.001))
        location.emitUi(uiLoc(40.002, -3.002))

        assertEquals(3, updates.last().trail.size)
    }

    @Test
    fun `should_not_emit_a_puck_when_core_permissions_are_missing`() = runTest {
        startController(corePermissions = false)

        readiness.emit(monitoring())
        location.emitUi(uiLoc(40.0, -3.0))

        assertNull(updates.last().puck)
    }

    // ── Candidate freeze [DET-PHASE-001] ────────────────────────────────────────

    @Test
    fun `should_freeze_the_puck_at_the_last_driving_fix_and_stop_growing_the_trail_in_candidate`() = runTest {
        startController()

        readiness.emit(monitoring(phase = DetectionPhase.Driving))
        location.emitUi(uiLoc(40.0, -3.0))
        location.emitUi(uiLoc(40.001, -3.001)) // last driving fix — the spot the car stopped at
        assertEquals(2, updates.last().trail.size)

        // User has parked and is walking away: phase flips to Candidate and the pedestrian GPS drifts off.
        readiness.emit(monitoring(phase = DetectionPhase.Candidate))
        location.emitUi(uiLoc(41.0, -4.0)) // far pedestrian fix — must be ignored for the puck position

        val update = updates.last()
        val puck = update.puck
        assertNotNull(puck)
        assertEquals(DetectionPhase.Candidate, puck.phase)
        // Frozen at the last driving fix, NOT chasing the pedestrian.
        assertEquals(40.001, puck.latitude)
        assertEquals(-3.001, puck.longitude)
        // A frozen car contributes no new breadcrumb points.
        assertEquals(2, update.trail.size)
    }

    // ── Trip end — atomic reset (puck + trail + matchedTrail together) ──────────

    @Test
    fun `should_reset_to_idle_in_a_single_atomic_emission_when_the_trip_ends`() = runTest {
        startController()

        readiness.emit(monitoring())
        location.emitUi(uiLoc(40.0, -3.0))
        location.emitUi(uiLoc(40.001, -3.001))
        assertEquals(2, updates.last().trail.size)

        // Detection drops out of Monitoring → the trip is over. ONE emission carries the full reset,
        // matchedTrail included — no debounced pipeline can leave a snapped-line ghost. [ROUTE-SNAP-001]
        readiness.emit(DetectionReadiness.Ready(ParkingStrategy.COORDINATOR))

        assertEquals(TripUpdate.IDLE, updates.last())
    }

    @Test
    fun `should_publish_idle_when_detection_is_not_monitoring`() = runTest {
        startController()

        readiness.emit(DetectionReadiness.Ready(ParkingStrategy.COORDINATOR))

        assertEquals(listOf(TripUpdate.IDLE), updates)
    }

    // ── Departure origin resolution [DEPART-CONSISTENCY-001] ─────────────────────

    @Test
    fun `should_fall_back_to_the_active_session_location_as_departure_when_the_trip_has_none`() = runTest {
        // The controller observes active sessions itself — the parked location it sees becomes the
        // departure fallback once the trip starts (replaces the old rememberParkingLocation command).
        parkingRepo = FakeUserParkingRepository(
            initialSession = UserParking(id = "p1", location = gps(10.0, 20.0), isActive = true),
        )
        startController()

        // departingVehicleId null also exercises monitoredVehicle() strategy resolution.
        readiness.emit(monitoring(departurePoint = null, departingVehicleId = null))
        location.emitUi(uiLoc(40.0, -3.0))

        val update = updates.last()
        assertEquals(gps(10.0, 20.0), update.departurePoint)
        assertEquals("veh-1", update.puck?.vehicleId) // resolved via monitoredVehicle (active vehicle)
    }

    @Test
    fun `should_prefer_the_trip_departure_point_over_the_last_session_location`() = runTest {
        parkingRepo = FakeUserParkingRepository(
            initialSession = UserParking(id = "p1", location = gps(10.0, 20.0), isActive = true),
        )
        startController()
        val tripDeparture = gps(50.0, 60.0)

        readiness.emit(monitoring(departurePoint = tripDeparture))
        location.emitUi(uiLoc(40.0, -3.0))

        assertEquals(tripDeparture, updates.last().departurePoint)
    }
}
