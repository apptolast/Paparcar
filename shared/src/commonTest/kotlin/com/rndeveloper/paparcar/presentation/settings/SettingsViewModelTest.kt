package com.rndeveloper.paparcar.presentation.settings

import app.cash.turbine.test
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.permissions.AppPermissionState
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission
import com.rndeveloper.paparcar.presentation.permissions.PermissionsFocus
import com.rndeveloper.paparcar.domain.detection.ParkingStrategyResolver
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateDetectionReliabilityUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReliabilityUseCase
import com.rndeveloper.paparcar.domain.diagnostics.UnknownDeviceInfoProvider
import com.rndeveloper.paparcar.domain.usecase.diagnostics.SendDiagnosticsReportUseCase
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.domain.usecase.user.DeleteAccountUseCase
import com.rndeveloper.paparcar.domain.usecase.user.SetFirstStepsDismissedUseCase
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeBluetoothScanner
import com.rndeveloper.paparcar.fakes.FakeDiagnosticsReportUploader
import com.rndeveloper.paparcar.fakes.FakeLocalDiagnosticsLog
import com.rndeveloper.paparcar.fakes.FakeOemBackgroundReliabilityManager
import com.rndeveloper.paparcar.fakes.FakePermissionManager
import com.rndeveloper.paparcar.fakes.FakeSpotRepository
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeUserProfileRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
    private lateinit var reportUploader: FakeDiagnosticsReportUploader
    private lateinit var entitlementRepo: com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
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
        reportUploader = FakeDiagnosticsReportUploader()
        entitlementRepo = com.rndeveloper.paparcar.fakes.FakeEntitlementRepository()
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
        widgetPinRequester: com.rndeveloper.paparcar.domain.service.WidgetPinRequester? = null,
    ): SettingsViewModel {
        val useCase = DeleteAccountUseCase(
            authRepository = auth,
            userScopedRepos = listOf(parking, customVehicles, profile, FakeZoneRepository()),
            spotRepository = spots,
        )
        val observeReliability = ObserveDetectionReliabilityUseCase(
            vehicleRepository = customVehicles,
            permissionManager = customPermissions,
            oemBackgroundReliabilityManager = FakeOemBackgroundReliabilityManager(),
            strategyResolver = ParkingStrategyResolver(customVehicles, FakeBluetoothScanner()),
            evaluateDetectionReliability = EvaluateDetectionReliabilityUseCase(),
        )
        val sendDiagnostics = SendDiagnosticsReportUseCase(
            authRepository = auth,
            uploader = reportUploader,
            localLog = FakeLocalDiagnosticsLog(),
            deviceInfo = UnknownDeviceInfoProvider,
        )
        return SettingsViewModel(
            customPrefs,
            auth,
            profile,
            useCase,
            SetFirstStepsDismissedUseCase(customPrefs, auth, profile),
            customPermissions,
            customVehicles,
            observeReliability,
            sendDiagnostics,
            com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase(entitlementRepo),
            widgetPinRequester,
        )
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
    fun `should_notEmitEffect_when_autoDetect_toggled_on_and_detectionPermitted`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        prefs.setAutoDetectParking(false)
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ToggleAutoDetect(true))
            expectNoEvents()
        }
        assertTrue(vm.state.value.autoDetectParking)
        assertTrue(vm.state.value.autoDetectActive)
    }

    // ── The switch reports what detection CAN do, not what was stored ──────────
    // [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001]

    @Test
    fun `should_readOff_when_autoDetectPreferred_but_permissionsMissing`() = runTest {
        permissions.emit(FakePermissionManager.allDenied())
        // The stored intent is untouched — only what the screen CLAIMS changes.
        assertTrue(vm.state.value.autoDetectParking)
        assertFalse(vm.state.value.autoDetectActive)
    }

    @Test
    fun `should_readOff_when_autoDetectPreferred_but_gpsOff`() = runTest {
        permissions.emit(FakePermissionManager.permissionsOnlyNoGps())
        assertTrue(vm.state.value.autoDetectParking)
        assertFalse(vm.state.value.autoDetectActive)
    }

    @Test
    fun `should_readOn_when_autoDetectPreferred_and_detectionPermitted`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        assertTrue(vm.state.value.autoDetectActive)
    }

    @Test
    fun `should_readOff_when_detectionPermitted_but_autoDetectTurnedOff`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        vm.handleIntent(SettingsIntent.ToggleAutoDetect(false))
        assertFalse(vm.state.value.autoDetectActive)
    }

    @Test
    fun `should_bankIntent_and_openCorePermissions_when_toggledOn_while_blocked`() = runTest {
        permissions.emit(FakePermissionManager.allDenied()) // foreground location missing → CORE
        prefs.setAutoDetectParking(false)
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ToggleAutoDetect(true))
            val effect = awaitItem()
            assertIs<SettingsEffect.NavigateToPermissions>(effect)
            assertEquals(PermissionsFocus.Core, effect.focus)
            cancelAndIgnoreRemainingEvents()
        }
        // The intent IS banked, so granting the permission is enough — no second tap.
        assertTrue(prefs.autoDetectParking)
        assertTrue(vm.state.value.autoDetectParking)
    }

    @Test
    fun `should_openProducerPermissions_when_toggledOn_and_onlyProducerMissing`() = runTest {
        permissions.emit(AppPermissionState(hasLocationPermission = true, isLocationServicesEnabled = true))
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ToggleAutoDetect(true))
            val effect = awaitItem()
            assertIs<SettingsEffect.NavigateToPermissions>(effect)
            assertEquals(PermissionsFocus.Producer, effect.focus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_openCorePermissions_when_toggledOn_and_onlyGpsOff`() = runTest {
        // GPS master switch off is a CORE problem: the "Enable GPS" row lives in that section.
        permissions.emit(FakePermissionManager.permissionsOnlyNoGps())
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ToggleAutoDetect(true))
            val effect = awaitItem()
            assertIs<SettingsEffect.NavigateToPermissions>(effect)
            assertEquals(PermissionsFocus.Core, effect.focus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emitDetectionTurnedOff_not_permissions_when_toggled_off_while_blocked`() = runTest {
        // Turning OFF is never an ask for permissions, whatever is missing.
        permissions.emit(FakePermissionManager.allDenied())
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ToggleAutoDetect(false))
            assertIs<SettingsEffect.DetectionTurnedOff>(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `should_updateNotifyParking_state_and_prefs`() = runTest {
        vm.handleIntent(SettingsIntent.ToggleParkingDetectedNotif(false))
        assertFalse(vm.state.value.notifyParkingDetected)
        assertFalse(prefs.notifyParkingDetected)
    }

    // ── Refresh from prefs ────────────────────────────────────────────────────

    @Test
    fun `should_reflectNewPrefs_after_refreshFromPreferences`() = runTest {
        prefs.setAutoDetectParking(false)
        vm.refreshFromPreferences()
        assertFalse(vm.state.value.autoDetectParking)
    }

    // ── Send diagnostics flow [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001] ────────

    @Test
    fun `should_showConsentDialog_on_requestSendDiagnostics`() = runTest {
        vm.handleIntent(SettingsIntent.RequestSendDiagnostics)
        assertTrue(vm.state.value.showSendDiagnosticsConfirmation)
    }

    @Test
    fun `should_notUploadAnything_on_requestSendDiagnostics`() = runTest {
        // Consent first: opening the dialog must not ship the log by itself.
        vm.handleIntent(SettingsIntent.RequestSendDiagnostics)
        assertEquals(0, reportUploader.uploadCallCount)
    }

    @Test
    fun `should_hideConsentDialog_on_dismissSendDiagnostics`() = runTest {
        vm.handleIntent(SettingsIntent.RequestSendDiagnostics)
        vm.handleIntent(SettingsIntent.DismissSendDiagnostics)
        assertFalse(vm.state.value.showSendDiagnosticsConfirmation)
    }

    @Test
    fun `should_uploadAndEmitSent_on_confirmSendDiagnostics`() = runTest {
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ConfirmSendDiagnostics)
            assertIs<SettingsEffect.DiagnosticsSent>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, reportUploader.uploadCallCount)
        assertFalse(vm.state.value.showSendDiagnosticsConfirmation)
        assertFalse(vm.state.value.isSendingDiagnostics)
    }

    @Test
    fun `should_emitError_when_diagnosticsUploadFails`() = runTest {
        reportUploader.uploadResult = Result.failure(Exception("network error"))
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ConfirmSendDiagnostics)
            assertIs<SettingsEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.state.value.isSendingDiagnostics)
    }

    // ── The report's description [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001] ──

    @Test
    fun `should_holdTheDraft_on_updateDiagnosticsMessage`() = runTest {
        vm.handleIntent(SettingsIntent.UpdateDiagnosticsMessage("the pin was wrong"))
        assertEquals("the pin was wrong", vm.state.value.diagnosticsMessage)
    }

    @Test
    fun `should_shipTheDraft_when_confirmSendDiagnostics`() = runTest {
        vm.handleIntent(SettingsIntent.UpdateDiagnosticsMessage("the pin was wrong"))
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ConfirmSendDiagnostics)
            assertIs<SettingsEffect.DiagnosticsSent>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("the pin was wrong", reportUploader.lastReport?.message)
    }

    @Test
    fun `should_clearTheDraft_when_theReportIsSent`() = runTest {
        vm.handleIntent(SettingsIntent.UpdateDiagnosticsMessage("the pin was wrong"))
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ConfirmSendDiagnostics)
            assertIs<SettingsEffect.DiagnosticsSent>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("", vm.state.value.diagnosticsMessage)
    }

    // Losing four typed sentences to the network is what stops someone from trying again.
    @Test
    fun `should_keepTheDraft_when_theUploadFails`() = runTest {
        reportUploader.uploadResult = Result.failure(Exception("network error"))
        vm.handleIntent(SettingsIntent.UpdateDiagnosticsMessage("the pin was wrong"))
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ConfirmSendDiagnostics)
            assertIs<SettingsEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("the pin was wrong", vm.state.value.diagnosticsMessage)
    }

    // BasicAlertDialog dismisses on an outside tap; that must not cost the user what they wrote.
    @Test
    fun `should_keepTheDraft_when_theDialogIsDismissed`() = runTest {
        vm.handleIntent(SettingsIntent.RequestSendDiagnostics)
        vm.handleIntent(SettingsIntent.UpdateDiagnosticsMessage("the pin was wrong"))
        vm.handleIntent(SettingsIntent.DismissSendDiagnostics)
        assertEquals("the pin was wrong", vm.state.value.diagnosticsMessage)
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
    fun `should_keep_the_confirmation_open_while_the_deletion_is_in_flight`() = runTest {
        // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001] The dialog used to be dismissed
        // in the very update that raised the flag, which left its own spinner unreachable and made
        // the only visible one belong to the danger-zone button BEHIND it — a button that had not
        // been pressed. It now stays up with its progress until the deletion resolves, the same
        // contract as the diagnostics upload and the vehicle deletion.
        // A StandardTestDispatcher on purpose: the suite's Unconfined one finishes the deletion
        // before there is any in-flight window left to look at.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        vm = buildVm()
        advanceUntilIdle()

        vm.handleIntent(SettingsIntent.RequestDeleteAccount)
        vm.handleIntent(SettingsIntent.ConfirmDeleteAccount)

        assertTrue(vm.state.value.isDeletingAccount)
        assertTrue(vm.state.value.showDeleteAccountConfirmation)
    }

    @Test
    fun `should_keep_the_confirmation_open_for_retry_on_deleteAccount_failure`() = runTest {
        // A failed deletion has to leave the retry one tap away, on the same surface.
        auth.deleteAccountResult = Result.failure(Exception("network error"))
        vm.handleIntent(SettingsIntent.RequestDeleteAccount)
        vm.handleIntent(SettingsIntent.ConfirmDeleteAccount)
        assertFalse(vm.state.value.isDeletingAccount)
        assertTrue(vm.state.value.showDeleteAccountConfirmation)
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

    // ── Paparcar Plus [MONEY-001] ─────────────────────────────────────────────

    @Test
    fun `should_routeToThePaywallAndLeaveThePrefAlone_when_freeEnablesZoneAlerts`() = runTest {
        prefs.setNotifyZoneSpots(false)
        vm.refreshFromPreferences()

        vm.effect.test {
            vm.handleIntent(SettingsIntent.ToggleZoneAlertsNotif(enabled = true))
            assertEquals(
                SettingsEffect.NavigateToPaywall(com.rndeveloper.paparcar.domain.entitlement.PaywallReason.ZONE_ALERTS),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(prefs.notifyZoneSpots, "the attempt opens the paywall; it must not flip the pref")
    }

    @Test
    fun `should_persistTheMasterSwitch_when_plusTogglesIt`() = runTest {
        entitlementRepo.emitEntitlement(com.rndeveloper.paparcar.domain.model.Entitlement.PLUS)

        vm.handleIntent(SettingsIntent.ToggleZoneAlertsNotif(enabled = false))
        assertFalse(prefs.notifyZoneSpots)

        vm.handleIntent(SettingsIntent.ToggleZoneAlertsNotif(enabled = true))
        assertTrue(prefs.notifyZoneSpots)
    }

    @Test
    fun `should_alwaysAllowSilencing_when_free`() = runTest {
        // Silencing a channel needs no entitlement — only ENABLING is the gated attempt.
        vm.handleIntent(SettingsIntent.ToggleZoneAlertsNotif(enabled = false))
        assertFalse(prefs.notifyZoneSpots)
    }

    @Test
    fun `should_openThePaywall_when_thePlusRowIsTapped`() = runTest {
        vm.effect.test {
            vm.handleIntent(SettingsIntent.OpenPlusPaywall)
            // The Plus row is an offer, not a gate — GENERAL keeps the funnel honest. [FREE-LIMITS-001]
            assertEquals(
                SettingsEffect.NavigateToPaywall(com.rndeveloper.paparcar.domain.entitlement.PaywallReason.GENERAL),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Replaying the guided tutorial ─────────────────────────────────────────
    // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]

    /**
     * All THREE sets, and the deferred one is why this test exists: the replay cleared `done` and
     * `dismissed` only, so a step the user had once answered with "not yet" stayed skipped in every
     * replay afterwards — silently, since a deferred step is simply not asked for. The contract in
     * `AppPreferences` had promised the opposite since the day it was written.
     */
    @Test
    fun `should_clearAllThreeFirstStepsSets_when_restartingTheTutorial`() = runTest {
        prefs.setFirstStepsDone(setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH))
        prefs.setFirstStepsDeferred(setOf(FirstStep.FORTIFY_WATCH))
        prefs.setFirstStepsDismissed(true)

        vm.handleIntent(SettingsIntent.RestartFirstSteps)

        assertEquals(emptySet(), prefs.firstStepsDone.value)
        assertEquals(emptySet(), prefs.firstStepsDeferred.value, "a declined step must be asked again")
        assertFalse(prefs.firstStepsDismissed.value)
    }

    /**
     * ⛔ And exactly THREE sets: what the user was told about where the complements' data comes from
     * is NOT one of them. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
     *
     * A replay does not un-park the car and it does not un-tell somebody something. Clearing this
     * would make both complement lanes introduce themselves all over again — a lecture nobody asked
     * for by pressing "replay the tutorial" — and would tick the fifth step back to open while the
     * knowledge it measures is still true.
     */
    @Test
    fun `should_keepWhatTheUserWasAlreadyTold_when_restartingTheTutorial`() = runTest {
        prefs.setIntroducedComplements(setOf(NearbyComplement.CAR_PARKS))

        vm.handleIntent(SettingsIntent.RestartFirstSteps)

        assertEquals(setOf(NearbyComplement.CAR_PARKS), prefs.introducedComplements.value)
    }

    /**
     * And the account has to forget it too: the dismissal now outlives the install, so a replay
     * that only cleared the device would be undone by the very next cold start, which restores
     * whatever the account still remembers.
     */
    @Test
    fun `should_clearTheAccountDismissal_when_restartingTheTutorial`() = runTest {
        prefs.setFirstStepsDismissed(true)

        vm.handleIntent(SettingsIntent.RestartFirstSteps)

        assertEquals(listOf<Long?>(null), profile.firstStepsDismissedWrites.toList())
    }

    // ── Widget pin row [WIDGET-ACTIONS-002] ──────────────────────────────────

    private class FakeWidgetPinRequester(
        override val isPinSupported: Boolean = true,
    ) : com.rndeveloper.paparcar.domain.service.WidgetPinRequester {
        var pinRequests = 0
        override fun requestPin() {
            pinRequests++
        }
    }

    @Test
    fun `should_showTheRowAndForwardThePin_when_theLauncherSupportsIt`() = runTest {
        val requester = FakeWidgetPinRequester()
        val vm = buildVm(widgetPinRequester = requester)

        assertTrue(vm.state.value.canPinWidget)
        vm.handleIntent(SettingsIntent.PinWidget)
        assertEquals(1, requester.pinRequests)
    }

    @Test
    fun `should_hideTheRow_when_thereIsNoPinDoor`() = runTest {
        // No requester (iOS, tests) or a launcher that answers no — the row never exists.
        assertFalse(buildVm().state.value.canPinWidget)
        assertFalse(
            buildVm(widgetPinRequester = FakeWidgetPinRequester(isPinSupported = false)).state.value.canPinWidget,
        )
    }
}
