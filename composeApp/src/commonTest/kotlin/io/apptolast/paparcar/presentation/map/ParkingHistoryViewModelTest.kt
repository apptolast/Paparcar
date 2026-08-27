@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.map

import app.cash.turbine.test
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.fakes.FakeLocationDataSource
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParkingHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val location = GpsPoint(40.416775, -3.703790, 10f, 0L, 0f)
    private val session = UserParking(id = "s1", location = location)

    private lateinit var locationDataSource: FakeLocationDataSource
    private lateinit var parkingRepo: FakeUserParkingRepository
    private lateinit var vm: ParkingHistoryViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        locationDataSource = FakeLocationDataSource()
        parkingRepo = FakeUserParkingRepository()
        vm = buildVm()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(
        initialSession: UserParking? = null,
        sessions: List<UserParking> = emptyList(),
        vehicles: List<Vehicle> = emptyList(),
    ): ParkingHistoryViewModel {
        val repo = FakeUserParkingRepository(initialSession = initialSession, initialSessions = sessions)
        parkingRepo = repo
        return ParkingHistoryViewModel(
            observeAdaptiveLocation = ObserveAdaptiveLocationUseCase(locationDataSource),
            userParkingRepository = repo,
            vehicleRepository = FakeVehicleRepository(extraVehicles = vehicles),
        )
    }

    private fun sessionAt(id: String, timestamp: Long, vehicleId: String? = null) = UserParking(
        id = id,
        vehicleId = vehicleId,
        location = GpsPoint(40.0, -3.0, 10f, timestamp, 0f),
        isActive = false,
    )

    // ── Init ──────────────────────────────────────────────────────────────────

    @Test
    fun `should_start_with_isLoading_true_before_first_location`() = runTest {
        assertEquals(true, vm.state.value.isLoading)
    }

    @Test
    fun `should_set_isLoading_false_after_first_location_emission`() = runTest {
        locationDataSource.emitHighAccuracy(location)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `should_update_userLocation_on_location_emission`() = runTest {
        locationDataSource.emitHighAccuracy(location)
        assertEquals(location, vm.state.value.userLocation)
    }

    @Test
    fun `should_populate_userParking_from_active_session`() = runTest {
        val vmWithSession = buildVm(initialSession = session)
        assertEquals(session, vmWithSession.state.value.userParking)
    }

    @Test
    fun `should_start_with_null_userParking_when_no_active_session`() = runTest {
        assertNull(vm.state.value.userParking)
    }

    // ── handleIntent ──────────────────────────────────────────────────────────

    @Test
    fun `should_emit_NavigateToSpotDetails_on_OnSpotSelected`() = runTest {
        vm.effect.test {
            vm.handleIntent(ParkingHistoryIntent.OnSpotSelected("spot-42"))
            val effect = awaitItem()
            assertIs<ParkingHistoryEffect.NavigateToSpotDetails>(effect)
            assertEquals("spot-42", effect.spotId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Focus + timeline stepper (‹ older / › newer) [HISTORY-DETAIL-002] ─────

    // Ordered most-recent → oldest: [newest(3000), middle(2000), oldest(1000)]
    private fun threeSessionVm() = buildVm(
        sessions = listOf(
            sessionAt("oldest", 1_000L),
            sessionAt("newest", 3_000L),
            sessionAt("middle", 2_000L),
        ),
    )

    @Test
    fun `should_focus_session_by_id_on_SetFocusedSession`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("middle"))
        assertEquals("middle", vm.state.value.focusedSession?.id)
    }

    @Test
    fun `should_expose_both_neighbours_when_focused_in_the_middle`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("middle"))
        assertTrue(vm.state.value.hasOlder)
        assertTrue(vm.state.value.hasNewer)
    }

    @Test
    fun `should_step_back_in_time_on_FocusOlder`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("newest"))
        vm.handleIntent(ParkingHistoryIntent.FocusOlder)
        assertEquals("middle", vm.state.value.focusedSession?.id)
    }

    @Test
    fun `should_step_toward_today_on_FocusNewer`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("middle"))
        vm.handleIntent(ParkingHistoryIntent.FocusNewer)
        assertEquals("newest", vm.state.value.focusedSession?.id)
    }

    @Test
    fun `should_report_no_newer_at_the_most_recent_end`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("newest"))
        assertFalse(vm.state.value.hasNewer)
        assertTrue(vm.state.value.hasOlder)
    }

    @Test
    fun `should_report_no_older_at_the_oldest_end`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("oldest"))
        assertTrue(vm.state.value.hasNewer)
        assertFalse(vm.state.value.hasOlder)
    }

    @Test
    fun `should_clamp_FocusOlder_at_the_oldest_end`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("oldest"))
        vm.handleIntent(ParkingHistoryIntent.FocusOlder)
        assertEquals("oldest", vm.state.value.focusedSession?.id)
    }

    // ── Per-vehicle scope of the stepper [HISTORY-DETAIL-VEHICLE-SCOPE-001] ───

    /**
     * Two cars whose parkings interleave in time — the exact shape that used to leak one car's
     * history into the other's stepper:
     *   kamiq-new(4000) · focus-new(3000) · kamiq-old(2000) · focus-old(1000)
     */
    private fun twoVehicleVm() = buildVm(
        sessions = listOf(
            sessionAt("focus-old", 1_000L, vehicleId = "focus"),
            sessionAt("kamiq-old", 2_000L, vehicleId = "kamiq"),
            sessionAt("focus-new", 3_000L, vehicleId = "focus"),
            sessionAt("kamiq-new", 4_000L, vehicleId = "kamiq"),
        ),
    )

    @Test
    fun `should_scope_the_stepper_list_to_the_focused_vehicle`() = runTest {
        val vm = twoVehicleVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("kamiq-new"))
        assertEquals(listOf("kamiq-new", "kamiq-old"), vm.state.value.orderedSessions.map { it.id })
    }

    @Test
    fun `should_skip_the_other_vehicle_when_stepping_older`() = runTest {
        val vm = twoVehicleVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("kamiq-new"))
        vm.handleIntent(ParkingHistoryIntent.FocusOlder)
        // focus-new(3000) sits in between, but it belongs to the other car.
        assertEquals("kamiq-old", vm.state.value.focusedSession?.id)
    }

    @Test
    fun `should_skip_the_other_vehicle_when_stepping_newer`() = runTest {
        val vm = twoVehicleVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("focus-old"))
        vm.handleIntent(ParkingHistoryIntent.FocusNewer)
        // kamiq-old(2000) sits in between, but it belongs to the other car.
        assertEquals("focus-new", vm.state.value.focusedSession?.id)
    }

    @Test
    fun `should_report_no_older_at_the_focused_vehicle_oldest_entry`() = runTest {
        val vm = twoVehicleVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("kamiq-old"))
        // focus-old(1000) is older in the global history, but not this car's.
        assertFalse(vm.state.value.hasOlder)
        assertTrue(vm.state.value.hasNewer)
    }

    @Test
    fun `should_report_no_newer_at_the_focused_vehicle_most_recent_entry`() = runTest {
        val vm = twoVehicleVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("focus-new"))
        // kamiq-new(4000) is more recent in the global history, but not this car's.
        assertFalse(vm.state.value.hasNewer)
        assertTrue(vm.state.value.hasOlder)
    }

    @Test
    fun `should_rescope_the_stepper_when_focus_moves_to_another_vehicle`() = runTest {
        val vm = twoVehicleVm()
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("kamiq-new"))
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("focus-old"))
        assertEquals(listOf("focus-new", "focus-old"), vm.state.value.orderedSessions.map { it.id })
    }

    @Test
    fun `should_include_the_active_session_in_the_focused_vehicle_stepper`() = runTest {
        // The user's call (20-08): the car parked RIGHT NOW is one more entry of its own timeline.
        val vm = buildVm(
            initialSession = sessionAt("kamiq-now", 5_000L, vehicleId = "kamiq").copy(isActive = true),
            sessions = listOf(
                sessionAt("kamiq-old", 2_000L, vehicleId = "kamiq"),
                sessionAt("focus-new", 3_000L, vehicleId = "focus"),
            ),
        )
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("kamiq-old"))
        vm.handleIntent(ParkingHistoryIntent.FocusNewer)
        assertEquals("kamiq-now", vm.state.value.focusedSession?.id)
    }

    @Test
    fun `should_resolve_focused_vehicle_for_the_icon`() = runTest {
        val vm = buildVm(
            sessions = listOf(sessionAt("s1", 1_000L, vehicleId = "v1")),
            vehicles = listOf(
                Vehicle(id = "v1", userId = "u1", sizeCategory = VehicleSize.LARGE_SEDAN),
            ),
        )
        vm.handleIntent(ParkingHistoryIntent.SetFocusedSession("s1"))
        assertEquals("v1", vm.state.value.focusedVehicle?.id)
    }
}
