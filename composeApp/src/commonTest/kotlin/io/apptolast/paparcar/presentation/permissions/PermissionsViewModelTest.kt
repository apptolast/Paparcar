package io.apptolast.paparcar.presentation.permissions

import app.cash.turbine.test
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.permissions.AppPermissionState
import io.apptolast.paparcar.domain.usecase.detection.EvaluateDetectionReliabilityUseCase
import io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReliabilityUseCase
import io.apptolast.paparcar.fakes.FakeBluetoothScanner
import io.apptolast.paparcar.fakes.FakeOemBackgroundReliabilityManager
import io.apptolast.paparcar.fakes.FakePermissionManager
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakePermissions: FakePermissionManager
    private lateinit var fakeOemManager: FakeOemBackgroundReliabilityManager

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePermissions = FakePermissionManager()
        fakeOemManager = FakeOemBackgroundReliabilityManager()
    }

    private fun viewModel(
        oem: FakeOemBackgroundReliabilityManager = fakeOemManager,
    ) = PermissionsViewModel(
        fakePermissions,
        oem,
        ObserveDetectionReliabilityUseCase(
            vehicleRepository = FakeVehicleRepository(),
            permissionManager = fakePermissions,
            oemBackgroundReliabilityManager = oem,
            strategyResolver = ParkingStrategyResolver(FakeVehicleRepository(), FakeBluetoothScanner()),
            evaluateDetectionReliability = EvaluateDetectionReliabilityUseCase(),
        ),
    )

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Init / state sync ─────────────────────────────────────────────────────

    @Test
    fun `initial state is all false when no permissions`() {
        val vm = viewModel()

        assertFalse(vm.state.value.hasFineLocation)
        assertFalse(vm.state.value.hasBackgroundLocation)
        assertFalse(vm.state.value.hasActivityRecognition)
        assertFalse(vm.state.value.hasNotifications)
        assertFalse(vm.state.value.isLocationServicesEnabled)
        assertFalse(vm.state.value.showRationale)
        assertFalse(vm.state.value.showSettingsPrompt)
    }

    @Test
    fun `initial state reflects current permissions synchronously`() {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = viewModel()

        assertTrue(vm.state.value.hasFineLocation)
        assertTrue(vm.state.value.hasBackgroundLocation)
        assertTrue(vm.state.value.hasActivityRecognition)
        assertTrue(vm.state.value.hasNotifications)
        assertTrue(vm.state.value.isLocationServicesEnabled)
    }

    @Test
    fun `state updates when permissions change after creation`() = runTest {
        val vm = viewModel()
        assertFalse(vm.state.value.hasFineLocation)

        fakePermissions.emit(FakePermissionManager.allGranted())

        assertTrue(vm.state.value.hasFineLocation)
        assertTrue(vm.state.value.hasBackgroundLocation)
    }

    // ── NavigateToHome effect ─────────────────────────────────────────────────

    @Test
    fun `NavigateToHome NOT auto-emitted even when all permissions and GPS are granted`() = runTest {
        // Departure is now an explicit tap (FinishSetup / ContinueWithCore) so the user gets the
        // chance to opt into the optional background-reliability toggles first. [PERM-FOOTER-001]
        val vm = viewModel()

        vm.effect.test {
            fakePermissions.emit(FakePermissionManager.allGranted())
            expectNoEvents()
        }
    }

    @Test
    fun `NavigateToHome emitted on FinishSetup`() = runTest {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = viewModel()

        vm.effect.test {
            vm.handleIntent(PermissionsIntent.FinishSetup)
            assertIs<PermissionsEffect.NavigateToHome>(awaitItem())
        }
    }

    @Test
    fun `NavigateToHome NOT emitted when permissions granted but GPS off`() = runTest {
        val vm = viewModel()

        vm.effect.test {
            fakePermissions.emit(FakePermissionManager.permissionsOnlyNoGps())
            expectNoEvents()
        }
    }

    // ── Rationale escalation ──────────────────────────────────────────────────

    @Test
    fun `showRationale shown on second tap when permissions still denied`() = runTest {
        val vm = viewModel()

        vm.handleIntent(PermissionsIntent.RequestPermissions) // tap 1: no escalation yet
        assertFalse(vm.state.value.showRationale)

        vm.handleIntent(PermissionsIntent.RequestPermissions) // tap 2: escalate → showRationale
        assertTrue(vm.state.value.showRationale)
        assertFalse(vm.state.value.showSettingsPrompt)
    }

    @Test
    fun `showSettingsPrompt escalated on third tap when rationale already shown`() = runTest {
        val vm = viewModel()

        vm.handleIntent(PermissionsIntent.RequestPermissions) // tap 1
        vm.handleIntent(PermissionsIntent.RequestPermissions) // tap 2: showRationale = true
        assertTrue(vm.state.value.showRationale)

        vm.handleIntent(PermissionsIntent.RequestPermissions) // tap 3: showSettingsPrompt = true
        assertTrue(vm.state.value.showSettingsPrompt)
    }

    @Test
    fun `showRationale NOT shown if requestCount is zero`() = runTest {
        val vm = viewModel()
        fakePermissions.emit(FakePermissionManager.allDenied())

        assertFalse(vm.state.value.showRationale)
        assertFalse(vm.state.value.showSettingsPrompt)
    }

    // ── RequestPermissions routing ────────────────────────────────────────────

    @Test
    fun `RequestPermissions emits RequestStep1 when step1 is pending`() = runTest {
        val vm = viewModel()

        vm.effect.test {
            vm.handleIntent(PermissionsIntent.RequestPermissions)
            assertIs<PermissionsEffect.RequestStep1>(awaitItem())
        }
    }

    @Test
    fun `RequestPermissions shows guide then emits RequestStep2 on confirm when step1 done but background missing`() = runTest {
        fakePermissions.emit(
            AppPermissionState(
                hasLocationPermission = true,
                hasBackgroundLocationPermission = false,
                hasActivityRecognitionPermission = true,
                hasNotificationPermission = true,
                isLocationServicesEnabled = true,
            ),
        )
        val vm = viewModel()

        vm.handleIntent(PermissionsIntent.RequestPermissions)
        // Step 2 goes through a user-facing guide ("Allow all the time + press Back") before
        // the OS dialog opens. The VM raises the guide flag, not the effect.
        assertTrue(vm.state.value.showBackgroundLocationGuide)

        vm.effect.test {
            vm.handleIntent(PermissionsIntent.ConfirmBackgroundLocationGuide)
            assertIs<PermissionsEffect.RequestStep2BackgroundLocation>(awaitItem())
        }
    }

    @Test
    fun `RequestPermissions emits OpenLocationSettings when all permissions granted but GPS off`() = runTest {
        fakePermissions.emit(FakePermissionManager.permissionsOnlyNoGps())
        val vm = viewModel()

        vm.effect.test {
            vm.handleIntent(PermissionsIntent.RequestPermissions)
            assertIs<PermissionsEffect.OpenLocationSettings>(awaitItem())
        }
    }

    @Test
    fun `RequestPermissions emits OpenAppSettings when showSettingsPrompt is true`() = runTest {
        val vm = viewModel()
        // Escalate to settings prompt via three taps
        vm.handleIntent(PermissionsIntent.RequestPermissions) // tap 1
        vm.handleIntent(PermissionsIntent.RequestPermissions) // tap 2: showRationale
        vm.handleIntent(PermissionsIntent.RequestPermissions) // tap 3: showSettingsPrompt
        assertTrue(vm.state.value.showSettingsPrompt)

        vm.effect.test {
            vm.handleIntent(PermissionsIntent.RequestPermissions) // tap 4: OpenAppSettings
            assertIs<PermissionsEffect.OpenAppSettings>(awaitItem())
        }
    }

    // ── RefreshPermissions ────────────────────────────────────────────────────

    @Test
    fun `RefreshPermissions calls permissionManager refreshPermissions`() {
        val vm = viewModel()
        val countBefore = fakePermissions.refreshCount

        vm.handleIntent(PermissionsIntent.RefreshPermissions)

        assertEquals(countBefore + 1, fakePermissions.refreshCount)
    }

    // ── OEM autostart card visibility ─────────────────────────────────────────

    @Test
    fun `showAutostartCard is false when manufacturer does not require whitelist`() {
        val vm = viewModel(FakeOemBackgroundReliabilityManager(requiresAutostartWhitelist = false))

        assertFalse(vm.state.value.showAutostartCard)
    }

    @Test
    fun `showAutostartCard is true when manufacturer requires whitelist`() {
        val vm = viewModel(FakeOemBackgroundReliabilityManager(requiresAutostartWhitelist = true))

        assertTrue(vm.state.value.showAutostartCard)
    }

    @Test
    fun `RequestOemAutostart emits LaunchOemAutostartSettings`() = runTest {
        val vm = viewModel(FakeOemBackgroundReliabilityManager(requiresAutostartWhitelist = true))

        vm.effect.test {
            vm.handleIntent(PermissionsIntent.RequestOemAutostart)
            assertIs<PermissionsEffect.LaunchOemAutostartSettings>(awaitItem())
        }
    }
}
