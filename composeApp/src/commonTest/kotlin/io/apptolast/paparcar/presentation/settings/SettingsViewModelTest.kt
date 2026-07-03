package io.apptolast.paparcar.presentation.settings

import app.cash.turbine.test
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.permissions.AppPermissionState
import io.apptolast.paparcar.domain.permissions.RequiredPermission
import io.apptolast.paparcar.presentation.permissions.PermissionsFocus
import io.apptolast.paparcar.domain.usecase.user.DeleteAccountUseCase
import io.apptolast.paparcar.fakes.FakeAppPreferences
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakePermissionManager
import io.apptolast.paparcar.fakes.FakeSpotRepository
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeUserProfileRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import io.apptolast.paparcar.fakes.FakeZoneRepository
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
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val session = FakeAuthRepository.authenticatedSession()
    private lateinit var auth: FakeAuthRepository
    private lateinit var parking: FakeUserParkingRepository
    private lateinit var vehicles: FakeVehicleRepository
    private lateinit var profile: FakeUserProfileRepository
    private lateinit var spots: FakeSpotRepository
    private lateinit var prefs: FakeAppPreferences
    private lateinit var permissions: FakePermissionManager
    private lateinit var vm: SettingsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        auth = FakeAuthRepository(initialSession = session)
        parking = FakeUserParkingRepository()
        vehicles = FakeVehicleRepository()
        profile = FakeUserProfileRepository()
        spots = FakeSpotRepository()
        prefs = FakeAppPreferences()
        permissions = FakePermissionManager()
        vm = buildVm()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(
        customPrefs: FakeAppPreferences = prefs,
        customVehicles: FakeVehicleRepository = vehicles,
        customPermissions: FakePermissionManager = permissions,
    ): SettingsViewModel {
        val useCase = DeleteAccountUseCase(
            authRepository = auth,
            userScopedRepos = listOf(parking, customVehicles, profile, FakeZoneRepository()),
            spotRepository = spots,
        )
        return SettingsViewModel(customPrefs, auth, profile, useCase, customPermissions, customVehicles)
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Test
    fun `should_loadAutoDetect_from_prefs_on_init`() = runTest {
        val customPrefs = FakeAppPreferences(initialAutoDetect = false)
        val vm = buildVm(customPrefs)
        assertFalse(vm.state.value.autoDetectParking)
    }

    @Test
    fun `should_loadNotifyParkingDetected_from_prefs_on_init`() = runTest {
        val customPrefs = FakeAppPreferences(initialNotifyParking = false)
        val vm = buildVm(customPrefs)
        assertFalse(vm.state.value.notifyParkingDetected)
    }

    @Test
    fun `should_loadNotifySpotFreed_from_prefs_on_init`() = runTest {
        val customPrefs = FakeAppPreferences(initialNotifySpot = false)
        val vm = buildVm(customPrefs)
        assertFalse(vm.state.value.notifySpotFreed)
    }

    // ── Profile observation ───────────────────────────────────────────────────

    @Test
    fun `should_updateState_when_profile_emits`() = runTest {
        vm.state.test {
            awaitItem() // initial state (profile = null)

            profile.getOrCreateProfile(session)

            val updated = awaitItem()
            assertEquals(session.userId, updated.userProfile?.userId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Toggle intents ────────────────────────────────────────────────────────

    @Test
    fun `should_updateAutoDetect_state_and_prefs`() = runTest {
        vm.handleIntent(SettingsIntent.ToggleAutoDetect(false))
        assertFalse(vm.state.value.autoDetectParking)
        assertFalse(prefs.autoDetectParking)
    }

    @Test
    fun `should_emitDetectionTurnedOff_when_autoDetect_toggled_off`() = runTest {
        // [DET-TOGGLE-002] Turning OFF confirms at the point of action with an undo snackbar.
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ToggleAutoDetect(false))
            assertIs<SettingsEffect.DetectionTurnedOff>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_notEmitEffect_when_autoDetect_toggled_on`() = runTest {
        prefs.setAutoDetectParking(false)
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ToggleAutoDetect(true))
            expectNoEvents()
        }
        assertTrue(vm.state.value.autoDetectParking)
    }

    @Test
    fun `should_updateNotifyParking_state_and_prefs`() = runTest {
        vm.handleIntent(SettingsIntent.ToggleParkingDetectedNotif(false))
        assertFalse(vm.state.value.notifyParkingDetected)
        assertFalse(prefs.notifyParkingDetected)
    }

    @Test
    fun `should_updateNotifySpot_state_and_prefs`() = runTest {
        vm.handleIntent(SettingsIntent.ToggleSpotFreedNotif(false))
        assertFalse(vm.state.value.notifySpotFreed)
        assertFalse(prefs.notifySpotFreed)
    }

    // ── Master notifications ───────────────────────────────────────────────────

    @Test
    fun `should_disableBothNotifications_when_masterToggleOff`() = runTest {
        vm.handleIntent(SettingsIntent.ToggleMasterNotifications(false))
        assertFalse(vm.state.value.notifyParkingDetected)
        assertFalse(vm.state.value.notifySpotFreed)
        assertFalse(prefs.notifyParkingDetected)
        assertFalse(prefs.notifySpotFreed)
    }

    @Test
    fun `should_enableBothNotifications_when_masterToggleOn`() = runTest {
        prefs.setNotifyParkingDetected(false)
        prefs.setNotifySpotFreed(false)
        vm.handleIntent(SettingsIntent.ToggleMasterNotifications(true))
        assertTrue(vm.state.value.notifyParkingDetected)
        assertTrue(vm.state.value.notifySpotFreed)
    }

    // ── Refresh from prefs ────────────────────────────────────────────────────

    @Test
    fun `should_reflectNewPrefs_after_refreshFromPreferences`() = runTest {
        prefs.setAutoDetectParking(false)
        vm.refreshFromPreferences()
        assertFalse(vm.state.value.autoDetectParking)
    }

    // ── Delete account flow ───────────────────────────────────────────────────

    @Test
    fun `should_showDeleteConfirmation_on_requestDeleteAccount`() = runTest {
        vm.handleIntent(SettingsIntent.RequestDeleteAccount)
        assertTrue(vm.state.value.showDeleteAccountConfirmation)
    }

    @Test
    fun `should_hideDeleteConfirmation_on_dismissDeleteAccount`() = runTest {
        vm.handleIntent(SettingsIntent.RequestDeleteAccount)
        vm.handleIntent(SettingsIntent.DismissDeleteAccount)
        assertFalse(vm.state.value.showDeleteAccountConfirmation)
    }

    @Test
    fun `should_setIsDeletingAccount_true_and_hide_dialog_on_confirm`() = runTest {
        // The use case runs synchronously with UnconfinedTestDispatcher so
        // isDeletingAccount snaps from true → false in the same runTest block.
        // We verify the final effect instead of the transient state.
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ConfirmDeleteAccount)
            assertIs<SettingsEffect.NavigateToAuth>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.state.value.showDeleteAccountConfirmation)
    }

    @Test
    fun `should_emitNavigateToAuth_on_deleteAccount_success`() = runTest {
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ConfirmDeleteAccount)
            assertIs<SettingsEffect.NavigateToAuth>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_resetIsDeletingAccount_on_deleteAccount_failure`() = runTest {
        auth.deleteAccountResult = Result.failure(Exception("network error"))
        vm.handleIntent(SettingsIntent.ConfirmDeleteAccount)
        assertFalse(vm.state.value.isDeletingAccount)
    }

    @Test
    fun `should_emitShowError_on_deleteAccount_failure`() = runTest {
        auth.deleteAccountResult = Result.failure(Exception("network error"))
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ConfirmDeleteAccount)
            assertIs<SettingsEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Navigation intents ────────────────────────────────────────────────────

    @Test
    fun `should_emitNavigateToVehicles_on_navigateToVehicles`() = runTest {
        vm.effect.test {
            vm.handleIntent(SettingsIntent.NavigateToVehicles)
            assertIs<SettingsEffect.NavigateToVehicles>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Detection health (permissions) [SETTINGS-REMODEL-001] ──────────────────

    @Test
    fun `should_beHealthy_when_allPermissionsGranted_and_gpsOn`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        assertTrue(vm.state.value.missingDetectionPermissions.isEmpty())
        assertTrue(vm.state.value.isLocationServicesEnabled)
        assertTrue(vm.state.value.detectionHealthy)
    }

    @Test
    fun `should_reflectMissingPermissions_when_denied`() = runTest {
        permissions.emit(FakePermissionManager.allDenied())
        assertTrue(vm.state.value.missingDetectionPermissions.isNotEmpty())
        assertFalse(vm.state.value.detectionHealthy)
    }

    @Test
    fun `should_beUnhealthy_when_gpsOff_even_if_permissionsGranted`() = runTest {
        permissions.emit(FakePermissionManager.permissionsOnlyNoGps())
        assertTrue(vm.state.value.missingDetectionPermissions.isEmpty())
        assertFalse(vm.state.value.isLocationServicesEnabled)
        assertFalse(vm.state.value.detectionHealthy)
    }

    @Test
    fun `should_reflectBatteryExemption_from_permissionState`() = runTest {
        permissions.emit(FakePermissionManager.allGranted().copy(isBatteryOptimizationExempt = true))
        assertTrue(vm.state.value.isBatteryOptimizationExempt)
    }

    @Test
    fun `should_refreshPermissions_on_refreshFromPreferences`() = runTest {
        val before = permissions.refreshCount
        vm.refreshFromPreferences()
        assertEquals(before + 1, permissions.refreshCount)
    }

    // ── Fix / configure navigation ─────────────────────────────────────────────

    @Test
    fun `should_focusCore_when_fix_and_foregroundLocationMissing`() = runTest {
        permissions.emit(FakePermissionManager.allDenied()) // foreground location missing → CORE
        vm.effect.test {
            vm.handleIntent(SettingsIntent.FixDetectionPermissions)
            val effect = awaitItem()
            assertIs<SettingsEffect.NavigateToPermissions>(effect)
            assertEquals(PermissionsFocus.Core, effect.focus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_focusProducer_when_fix_and_onlyProducerMissing`() = runTest {
        // Foreground (CORE) granted, a PRODUCER permission missing → PRODUCER section.
        permissions.emit(AppPermissionState(hasLocationPermission = true, isLocationServicesEnabled = true))
        vm.effect.test {
            vm.handleIntent(SettingsIntent.FixDetectionPermissions)
            val effect = awaitItem()
            assertIs<SettingsEffect.NavigateToPermissions>(effect)
            assertEquals(PermissionsFocus.Producer, effect.focus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_focusCore_when_fix_and_gpsOff_even_if_permissionsGranted`() = runTest {
        // GPS master off → the "Enable GPS" row lives in the essential/CORE section.
        permissions.emit(FakePermissionManager.permissionsOnlyNoGps())
        vm.effect.test {
            vm.handleIntent(SettingsIntent.FixDetectionPermissions)
            val effect = awaitItem()
            assertIs<SettingsEffect.NavigateToPermissions>(effect)
            assertEquals(PermissionsFocus.Core, effect.focus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_focusProducer_on_configureBattery`() = runTest {
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ConfigureBattery)
            val effect = awaitItem()
            assertIs<SettingsEffect.NavigateToPermissions>(effect)
            assertEquals(PermissionsFocus.Producer, effect.focus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Bluetooth improvement row ──────────────────────────────────────────────

    @Test
    fun `should_reportBtConfigured_when_activeVehicle_hasBtDevice`() = runTest {
        val btVehicle = Vehicle(id = "v1", userId = "u", sizeCategory = VehicleSize.MEDIUM_SUV, bluetoothDeviceId = "AA:BB:CC")
        val vmWithBt = buildVm(customVehicles = FakeVehicleRepository(defaultVehicle = btVehicle))
        assertTrue(vmWithBt.state.value.btDeviceConfigured)
        assertEquals("v1", vmWithBt.state.value.activeVehicleId)
    }

    @Test
    fun `should_emitBluetoothConfig_when_configureBt_withActiveVehicle`() = runTest {
        val vehicle = Vehicle(id = "v1", userId = "u", sizeCategory = VehicleSize.MEDIUM_SUV)
        val vmWithVehicle = buildVm(customVehicles = FakeVehicleRepository(defaultVehicle = vehicle))
        vmWithVehicle.effect.test {
            vmWithVehicle.handleIntent(SettingsIntent.ConfigureBluetooth)
            val effect = awaitItem()
            assertIs<SettingsEffect.NavigateToBluetoothConfig>(effect)
            assertEquals("v1", effect.vehicleId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emitNavigateToVehicles_when_configureBt_withoutVehicle`() = runTest {
        // Default setUp has no vehicle → send the user to add one first.
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ConfigureBluetooth)
            assertIs<SettingsEffect.NavigateToVehicles>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    fun `should_callSignOut_on_logout`() = runTest {
        vm.handleIntent(SettingsIntent.Logout)
        assertEquals(1, auth.signOutCount)
    }
}
