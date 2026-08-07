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

class ParkingLocationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val location = GpsPoint(40.416775, -3.703790, 10f, 0L, 0f)
    private val session = UserParking(id = "s1", location = location)

    private lateinit var locationDataSource: FakeLocationDataSource
    private lateinit var parkingRepo: FakeUserParkingRepository
    private lateinit var vm: ParkingLocationViewModel

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
    ): ParkingLocationViewModel {
        val repo = FakeUserParkingRepository(initialSession = initialSession, initialSessions = sessions)
        parkingRepo = repo
        return ParkingLocationViewModel(
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
            vm.handleIntent(ParkingLocationIntent.OnSpotSelected("spot-42"))
            val effect = awaitItem()
            assertIs<ParkingLocationEffect.NavigateToSpotDetails>(effect)
            assertEquals("spot-42", effect.spotId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Focus + prev/next stepper [HISTORY-DETAIL-001] ────────────────────────

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
        vm.handleIntent(ParkingLocationIntent.SetFocusedSession("middle"))
        assertEquals("middle", vm.state.value.focusedSession?.id)
    }

    @Test
    fun `should_expose_both_neighbours_when_focused_in_the_middle`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingLocationIntent.SetFocusedSession("middle"))
        assertTrue(vm.state.value.hasPrevious)
        assertTrue(vm.state.value.hasNext)
    }

    @Test
    fun `should_step_to_older_session_on_FocusNext`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingLocationIntent.SetFocusedSession("newest"))
        vm.handleIntent(ParkingLocationIntent.FocusNext)
        assertEquals("middle", vm.state.value.focusedSession?.id)
    }

    @Test
    fun `should_step_to_newer_session_on_FocusPrevious`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingLocationIntent.SetFocusedSession("middle"))
        vm.handleIntent(ParkingLocationIntent.FocusPrevious)
        assertEquals("newest", vm.state.value.focusedSession?.id)
    }

    @Test
    fun `should_report_no_previous_at_the_newest_end`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingLocationIntent.SetFocusedSession("newest"))
        assertFalse(vm.state.value.hasPrevious)
        assertTrue(vm.state.value.hasNext)
    }

    @Test
    fun `should_report_no_next_at_the_oldest_end`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingLocationIntent.SetFocusedSession("oldest"))
        assertTrue(vm.state.value.hasPrevious)
        assertFalse(vm.state.value.hasNext)
    }

    @Test
    fun `should_clamp_FocusNext_at_the_oldest_end`() = runTest {
        val vm = threeSessionVm()
        vm.handleIntent(ParkingLocationIntent.SetFocusedSession("oldest"))
        vm.handleIntent(ParkingLocationIntent.FocusNext)
        assertEquals("oldest", vm.state.value.focusedSession?.id)
    }

    @Test
    fun `should_resolve_focused_vehicle_for_the_icon`() = runTest {
        val vm = buildVm(
            sessions = listOf(sessionAt("s1", 1_000L, vehicleId = "v1")),
            vehicles = listOf(
                Vehicle(id = "v1", userId = "u1", sizeCategory = VehicleSize.LARGE_SEDAN),
            ),
        )
        vm.handleIntent(ParkingLocationIntent.SetFocusedSession("s1"))
        assertEquals("v1", vm.state.value.focusedVehicle?.id)
    }
}
