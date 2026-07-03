@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.location.UserLocationUi
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.DrivingPuck
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.fakes.FakeLocationDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * HomeViewModel with zero coverage. Detection readiness is fed directly through the controller's
 * provider lambda; UI location fixes through [FakeLocationDataSource]. The callbacks feed the captured
 * [trail] / [matchedTrail] that the providers read back, mirroring the VM's single-sink loop.
 *
 * Map-matching is out of scope here (roadNetworkDataSource = null), so these tests isolate the puck +
 * trail + departure logic — including the [DET-PHASE-001] Candidate freeze and the immediate
 * matchedTrail clear on trip-end (R1) [ROUTE-SNAP-001].
 */
class HomeTripControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var scope: CoroutineScope

    private lateinit var location: FakeLocationDataSource
    private val readiness = MutableSharedFlow<DetectionReadiness>(extraBufferCapacity = 64)

    // Captured callback outputs. `trail` / `matchedTrail` are both written by the callbacks AND read by
    // the providers, exactly like the VM writes them into HomeState and reads them via state.value.
    private var lastPuck: DrivingPuck? = null
    private var trail: List<GpsPoint> = emptyList()
    private var lastDeparture: GpsPoint? = null
    private var tripCallCount = 0
    private var matchedTrail: List<GpsPoint> = emptyList()
    private var matchedCallCount = 0

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
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun buildController(
        vehicles: List<Vehicle> = listOf(vehicle),
        hasCorePermissions: Boolean = true,
    ): HomeTripController = HomeTripController(
        scope = scope,
        observeDetectionReadiness = { readiness },
        locationDataSource = location,
        roadNetworkDataSource = null,
        vehicles = { vehicles },
        hasCorePermissions = { hasCorePermissions },
        currentTrail = { trail },
        currentMatchedTrail = { matchedTrail },
        onTrip = { puck, newTrail, departure ->
            lastPuck = puck
            trail = newTrail
            lastDeparture = departure
            tripCallCount++
        },
        onMatchedTrail = { matched ->
            matchedTrail = matched
            matchedCallCount++
        },
    ).also { it.start() }

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
        buildController()

        readiness.emit(monitoring(phase = DetectionPhase.Driving))
        location.emitUi(uiLoc(40.0, -3.0, bearing = 90f))

        val puck = lastPuck
        assertNotNull(puck)
        assertEquals(40.0, puck.latitude)
        assertEquals(-3.0, puck.longitude)
        assertEquals(DetectionPhase.Driving, puck.phase)
        assertEquals("veh-1", puck.vehicleId)
        assertEquals(CarbodyType.SEDAN, puck.carbodyType)
        assertEquals(1, trail.size)
        assertEquals(40.0, trail.first().latitude)
    }

    @Test
    fun `should_grow_the_trail_with_each_fix_while_driving`() = runTest {
        buildController()

        readiness.emit(monitoring())
        location.emitUi(uiLoc(40.0, -3.0))
        location.emitUi(uiLoc(40.001, -3.001))
        location.emitUi(uiLoc(40.002, -3.002))

        assertEquals(3, trail.size)
    }

    @Test
    fun `should_not_emit_a_puck_when_core_permissions_are_missing`() = runTest {
        buildController(hasCorePermissions = false)

        readiness.emit(monitoring())
        location.emitUi(uiLoc(40.0, -3.0))

        assertNull(lastPuck)
    }

    // ── Candidate freeze [DET-PHASE-001] ────────────────────────────────────────

    @Test
    fun `should_freeze_the_puck_at_the_last_driving_fix_and_stop_growing_the_trail_in_candidate`() = runTest {
        buildController()

        readiness.emit(monitoring(phase = DetectionPhase.Driving))
        location.emitUi(uiLoc(40.0, -3.0))
        location.emitUi(uiLoc(40.001, -3.001)) // last driving fix — the spot the car stopped at
        assertEquals(2, trail.size)

        // User has parked and is walking away: phase flips to Candidate and the pedestrian GPS drifts off.
        readiness.emit(monitoring(phase = DetectionPhase.Candidate))
        location.emitUi(uiLoc(41.0, -4.0)) // far pedestrian fix — must be ignored for the puck position

        val puck = lastPuck
        assertNotNull(puck)
        assertEquals(DetectionPhase.Candidate, puck.phase)
        // Frozen at the last driving fix, NOT chasing the pedestrian.
        assertEquals(40.001, puck.latitude)
        assertEquals(-3.001, puck.longitude)
        // A frozen car contributes no new breadcrumb points.
        assertEquals(2, trail.size)
    }

    // ── Trip end (R1: matchedTrail cleared immediately) ─────────────────────────

    @Test
    fun `should_reset_puck_trail_and_matchedTrail_immediately_when_the_trip_ends`() = runTest {
        buildController()

        readiness.emit(monitoring())
        location.emitUi(uiLoc(40.0, -3.0))
        location.emitUi(uiLoc(40.001, -3.001))
        // Pretend map-matching had produced a snapped line during the trip.
        matchedTrail = listOf(gps(1.0, 1.0))
        val matchedCallsBefore = matchedCallCount

        // Detection drops out of Monitoring → the trip is over.
        readiness.emit(DetectionReadiness.Ready(ParkingStrategy.COORDINATOR))

        assertNull(lastPuck)
        assertEquals(emptyList(), trail)
        // R1: matchedTrail is cleared in the same beat as the rest, not left to the debounced pipeline.
        assertEquals(emptyList(), matchedTrail)
        assertEquals(matchedCallsBefore + 1, matchedCallCount)
    }

    @Test
    fun `should_publish_a_null_puck_when_detection_is_not_monitoring`() = runTest {
        buildController()

        readiness.emit(DetectionReadiness.Ready(ParkingStrategy.COORDINATOR))

        assertNull(lastPuck)
        assertEquals(emptyList(), trail)
        assertEquals(1, tripCallCount)
    }

    // ── Departure origin resolution [DEPART-CONSISTENCY-001] ─────────────────────

    @Test
    fun `should_fall_back_to_the_remembered_parking_location_as_departure_when_the_trip_has_none`() = runTest {
        val controller = buildController()
        controller.rememberParkingLocation(gps(10.0, 20.0))

        // departingVehicleId null also exercises monitoredVehicle() strategy resolution.
        readiness.emit(monitoring(departurePoint = null, departingVehicleId = null))
        location.emitUi(uiLoc(40.0, -3.0))

        assertEquals(gps(10.0, 20.0), lastDeparture)
        assertEquals("veh-1", lastPuck?.vehicleId) // resolved via monitoredVehicle (active vehicle)
    }

    @Test
    fun `should_prefer_the_trip_departure_point_over_the_remembered_parking_location`() = runTest {
        val controller = buildController()
        controller.rememberParkingLocation(gps(10.0, 20.0))
        val tripDeparture = gps(50.0, 60.0)

        readiness.emit(monitoring(departurePoint = tripDeparture))
        location.emitUi(uiLoc(40.0, -3.0))

        assertEquals(tripDeparture, lastDeparture)
    }
}
