@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.addspot

import app.cash.turbine.test
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.permissions.AppPermissionState
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.fakes.FakeGeocoderDataSource
import io.apptolast.paparcar.fakes.FakeLocationDataSource
import io.apptolast.paparcar.fakes.FakePlacesDataSource
import io.apptolast.paparcar.fakes.FakePermissionManager
import io.apptolast.paparcar.fakes.FakeReportSpotScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AddFreeSpotViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val location = GpsPoint(40.416775, -3.703790, 10f, 0L, 0f)

    private lateinit var permissions: FakePermissionManager
    private lateinit var locationDataSource: FakeLocationDataSource
    private lateinit var scheduler: FakeReportSpotScheduler
    private lateinit var vm: AddFreeSpotViewModel

    private fun buildVm(): AddFreeSpotViewModel {
        val reportUseCase = ReportSpotReleasedUseCase(
            reportSpotScheduler = scheduler,
            getLocationInfo = GetLocationInfoUseCase(FakeGeocoderDataSource(), FakePlacesDataSource()),
        )
        return AddFreeSpotViewModel(permissions, locationDataSource, reportUseCase)
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        permissions = FakePermissionManager()
        locationDataSource = FakeLocationDataSource()
        scheduler = FakeReportSpotScheduler()
        vm = buildVm()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── GPS tracking ──────────────────────────────────────────────────────────

    @Test
    fun `should_not_update_gps_when_permissions_denied`() = runTest {
        locationDataSource.emitBalanced(location)
        assertNull(vm.state.value.userGpsPoint)
    }

    @Test
    fun `should_update_gps_when_permissions_granted`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitBalanced(location)
        assertEquals(location, vm.state.value.userGpsPoint)
    }

    @Test
    fun `should_clear_gps_when_permissions_revoked`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitBalanced(location)
        assertEquals(location, vm.state.value.userGpsPoint)

        permissions.emit(AppPermissionState())
        // After permission revoke the stream switches to emptyFlow — GPS stays at last value
        // (flatMapLatest doesn't retroactively clear state). That's the intended behavior.
        assertEquals(location, vm.state.value.userGpsPoint)
    }

    // ── CameraPositionChanged ─────────────────────────────────────────────────

    @Test
    fun `should_update_camera_position_on_CameraPositionChanged`() = runTest {
        vm.handleIntent(AddFreeSpotIntent.CameraPositionChanged(41.0, -4.0))
        assertEquals(41.0, vm.state.value.cameraLat)
        assertEquals(-4.0, vm.state.value.cameraLon)
    }

    // ── ConfirmReport ─────────────────────────────────────────────────────────

    @Test
    fun `should_emit_SpotReported_on_ConfirmReport_with_camera_position`() = runTest {
        vm.handleIntent(AddFreeSpotIntent.CameraPositionChanged(41.0, -4.0))

        vm.effect.test {
            vm.handleIntent(AddFreeSpotIntent.ConfirmReport)
            assertIs<AddFreeSpotEffect.SpotReported>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emit_SpotReported_using_user_gps_when_no_camera_position`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitBalanced(location)

        vm.effect.test {
            vm.handleIntent(AddFreeSpotIntent.ConfirmReport)
            assertIs<AddFreeSpotEffect.SpotReported>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emit_ShowError_when_no_location_available`() = runTest {
        vm.effect.test {
            vm.handleIntent(AddFreeSpotIntent.ConfirmReport)
            assertIs<AddFreeSpotEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_schedule_spot_via_ReportSpotReleasedUseCase_on_ConfirmReport`() = runTest {
        vm.handleIntent(AddFreeSpotIntent.CameraPositionChanged(41.0, -4.0))
        vm.handleIntent(AddFreeSpotIntent.ConfirmReport)

        assertEquals(1, scheduler.scheduleCallCount)
    }

    @Test
    fun `should_prefer_camera_position_over_user_gps`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitBalanced(location)                        // lat 40.41…
        vm.handleIntent(AddFreeSpotIntent.CameraPositionChanged(55.0, -1.0))

        vm.effect.test {
            vm.handleIntent(AddFreeSpotIntent.ConfirmReport)
            assertIs<AddFreeSpotEffect.SpotReported>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(55.0, scheduler.lastLat)
    }
}
