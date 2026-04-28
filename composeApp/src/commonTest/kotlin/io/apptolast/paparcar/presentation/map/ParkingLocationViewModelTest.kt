@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.map

import app.cash.turbine.test
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.fakes.FakeLocationDataSource
import io.apptolast.paparcar.fakes.FakeSpotRepository
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
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

class ParkingLocationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val location = GpsPoint(40.416775, -3.703790, 10f, 0L, 0f)
    private val session = UserParking(id = "s1", location = location)

    private lateinit var locationDataSource: FakeLocationDataSource
    private lateinit var parkingRepo: FakeUserParkingRepository
    private lateinit var spotRepo: FakeSpotRepository
    private lateinit var vm: ParkingLocationViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        locationDataSource = FakeLocationDataSource()
        parkingRepo = FakeUserParkingRepository()
        spotRepo = FakeSpotRepository()
        vm = buildVm()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(
        initialSession: UserParking? = null,
    ): ParkingLocationViewModel {
        val repo = FakeUserParkingRepository(initialSession = initialSession)
        parkingRepo = repo
        return ParkingLocationViewModel(
            observeAdaptiveLocation = ObserveAdaptiveLocationUseCase(locationDataSource),
            observeNearbySpots = ObserveNearbySpotsUseCase(spotRepo),
            userParkingRepository = repo,
        )
    }

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
}
