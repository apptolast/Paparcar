@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.ParkingStrategy
import com.rndeveloper.paparcar.domain.location.UserLocationUi
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.DetectionReadiness
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.fakes.FakeDrivingRouteStore
import com.rndeveloper.paparcar.fakes.FakeLocationDataSource
import com.rndeveloper.paparcar.fakes.FakePermissionManager
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
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
import kotlin.test.assertTrue

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
    private var routeStore: FakeDrivingRouteStore? = null
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
        routeStore = null
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
            userParkingRepository = parkingRepo,
            drivingRouteStore = routeStore,
            permissionManager = permissions,
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

    /** A recorded fix stamped NOW so it passes the freshness guard (production fixes carry a real
     *  wall-clock timestamp; a route older than the guard is treated as a leftover). */
    private fun freshGps(lat: Double, lon: Double) =
        GpsPoint(lat, lon, 0f, kotlin.time.Clock.System.now().toEpochMilliseconds(), 0f)

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

    // ── Backdated trip origin — route born at the parked spot [DET-ROUTE-ORIGIN-001] ──

    @Test
    fun `should_use_the_first_driving_fix_as_the_departure_origin_when_no_parked_spot_is_known`() = runTest {
        startController()

        // departingVehicleId null also exercises monitoredVehicle() strategy resolution.
        readiness.emit(monitoring(departurePoint = null, departingVehicleId = null))
        location.emitUi(uiLoc(40.0, -3.0)) // first fix — this is the origin
        location.emitUi(uiLoc(41.0, -2.0)) // trip extends; origin must not move

        val update = updates.last()
        assertEquals(40.0, update.departurePoint?.latitude)
        assertEquals(-3.0, update.departurePoint?.longitude)
        assertEquals("veh-1", update.puck?.vehicleId) // resolved via monitoredVehicle (active vehicle)
    }

    @Test
    fun `should_seed_the_trail_with_the_parked_spot_and_use_it_as_the_departure_origin`() = runTest {
        startController()
        // The departing session's parked location, ~1.1 km from where detection woke mid-trip.
        val parkedSpot = gps(40.01, -3.0)

        readiness.emit(monitoring(departurePoint = parkedSpot))
        location.emitUi(uiLoc(40.0, -3.0)) // first LIVE fix — detection woke late
        location.emitUi(uiLoc(40.001, -3.001))

        // The route is born at the parking spot: seed + the measured fixes, origin = the spot.
        val update = updates.last()
        assertEquals(40.01, update.departurePoint?.latitude)
        assertEquals(-3.0, update.departurePoint?.longitude)
        assertEquals(3, update.trail.size)
        assertEquals(40.01, update.trail.first().latitude)
        // The seed lives only in the assembled TripUpdate — the puck stays on the live fix.
        assertEquals(40.001, update.puck?.latitude)
    }

    // ── Real recorded route restored on cold restart [DET-ROUTE-TRACK-001] ──

    @Test
    fun `should_restore_the_recorded_driving_route_on_a_fresh_trail_and_prepend_the_parked_origin`() = runTest {
        // Cold restart mid-trip: the service recorded the real driven path to disk before the app
        // reopened. The line must be that real route (not a reconstruction), starting at the parked
        // spot the car left.
        val recorded = listOf(freshGps(40.011, -3.0), freshGps(40.012, -3.0), freshGps(40.013, -3.0))
        routeStore = FakeDrivingRouteStore(initial = recorded)
        startController()

        readiness.emit(monitoring(departurePoint = gps(40.010, -3.0)))
        location.emitUi(uiLoc(40.014, -3.0)) // first live fix after reopening

        val update = updates.last()
        // Origin = the parked spot, then the real recorded route, then the live fix.
        assertEquals(40.010, update.departurePoint?.latitude)
        assertEquals(40.010, update.trail.first().latitude)
        recorded.forEach { p -> assertTrue(update.trail.any { it.latitude == p.latitude }, "recorded point ${p.latitude} must be in the drawn route") }
        assertEquals(40.014, update.trail.last().latitude)
        assertEquals(5, update.trail.size) // origin + 3 recorded + live fix
    }

    @Test
    fun `should_restore_the_recorded_route_even_without_a_known_parked_origin`() = runTest {
        // Manual / AR arm: no parked session, no departure point. The recorded route alone still
        // draws the real trip.
        val recorded = listOf(freshGps(40.011, -3.0), freshGps(40.012, -3.0))
        routeStore = FakeDrivingRouteStore(initial = recorded)
        startController()

        readiness.emit(monitoring(departurePoint = null, departingVehicleId = null))
        location.emitUi(uiLoc(40.013, -3.0))

        val update = updates.last()
        assertEquals(40.011, update.trail.first().latitude) // route starts at the first recorded fix
        assertEquals(3, update.trail.size) // 2 recorded + live fix, no synthetic origin
    }

    // ── Origin survives a process death mid-trip — re-resolved from Room [DET-ROUTE-ORIGIN-002] ──

    @Test
    fun `should_seed_the_origin_from_the_parked_session_in_Room_when_the_service_lost_the_departure_point`() = runTest {
        // Cold restart mid-trip: the OEM killed the process, so Monitoring carries NO departure point,
        // but the vehicle's parked session is still in Room ~1.1 km back. The route must be born there,
        // not at wherever the app reopened.
        parkingRepo = FakeUserParkingRepository(
            initialSession = UserParking(id = "p1", vehicleId = "veh-1", location = gps(40.01, -3.0)),
        )
        startController()

        readiness.emit(monitoring(departurePoint = null))
        location.emitUi(uiLoc(40.0, -3.0)) // first LIVE fix after reopening the app
        location.emitUi(uiLoc(40.001, -3.001))

        val update = updates.last()
        assertEquals(40.01, update.departurePoint?.latitude)
        assertEquals(-3.0, update.departurePoint?.longitude)
        assertEquals(3, update.trail.size)
        assertEquals(40.01, update.trail.first().latitude)
    }

    @Test
    fun `should_not_seed_from_another_vehicles_parked_session`() = runTest {
        // Multi-car: the parked session in Room belongs to a DIFFERENT vehicle than the one driving.
        // Never seed from another car's spot — better the honest first-fix origin. [DET-ROUTE-ORIGIN-002]
        parkingRepo = FakeUserParkingRepository(
            initialSession = UserParking(id = "p2", vehicleId = "veh-OTHER", location = gps(40.01, -3.0)),
        )
        startController()

        readiness.emit(monitoring(departurePoint = null, departingVehicleId = "veh-1"))
        location.emitUi(uiLoc(40.0, -3.0))

        val update = updates.last()
        assertEquals(40.0, update.departurePoint?.latitude) // first fix, NOT the other car's spot
        assertEquals(1, update.trail.size)
    }

    @Test
    fun `should_reject_a_parked_spot_beyond_the_plausibility_ceiling_and_fall_back_to_the_first_fix`() = runTest {
        startController()
        val staleSession = gps(50.0, 60.0) // thousands of km away — a stale/unreleased session

        readiness.emit(monitoring(departurePoint = staleSession))
        location.emitUi(uiLoc(40.0, -3.0))

        // Better a short route than an invented one: no seed, origin = first measured fix.
        val update = updates.last()
        assertEquals(40.0, update.departurePoint?.latitude)
        assertEquals(-3.0, update.departurePoint?.longitude)
        assertEquals(1, update.trail.size)
    }

    @Test
    fun `should_seed_the_origin_only_once_per_trip_even_if_the_trip_context_changes_mid_trip`() = runTest {
        startController()

        readiness.emit(monitoring(departurePoint = gps(40.01, -3.0)))
        location.emitUi(uiLoc(40.0, -3.0))
        // A superseding session mid-trip republishes Monitoring with a different origin — the
        // already-drawn route must not be rewritten.
        readiness.emit(monitoring(departurePoint = gps(40.02, -3.02)))
        location.emitUi(uiLoc(40.001, -3.001))

        val update = updates.last()
        assertEquals(40.01, update.trail.first().latitude)
        assertEquals(40.01, update.departurePoint?.latitude)
        assertEquals(3, update.trail.size) // original seed + 2 measured fixes, no second seed
    }
}
