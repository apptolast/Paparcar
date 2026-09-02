package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.connectivity.ConnectivityBannerPhase
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeConnectivityObserver
import com.rndeveloper.paparcar.fakes.FakePermissionManager
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakePermissions: FakePermissionManager
    private lateinit var fakePrefs: FakeAppPreferences
    private lateinit var fakeConnectivity: FakeConnectivityObserver
    private lateinit var fakeVehicleRepo: FakeVehicleRepository
    private lateinit var fakeZoneRepo: FakeZoneRepository
    private lateinit var fakeParkingRepo: FakeUserParkingRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePermissions = FakePermissionManager()
        fakePrefs = FakeAppPreferences()
        fakeConnectivity = FakeConnectivityObserver()
        fakeVehicleRepo = FakeVehicleRepository()
        fakeZoneRepo = FakeZoneRepository()
        fakeParkingRepo = FakeUserParkingRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Test
    fun `initial state is all false when no permissions granted`() {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)

        assertFalse(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.locationServicesEnabled)
        assertFalse(vm.state.value.isFullyOperational)
    }

    @Test
    fun `initial state reflects current permission state synchronously`() {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)

        assertTrue(vm.state.value.permissionsGranted)
        assertTrue(vm.state.value.locationServicesEnabled)
        assertTrue(vm.state.value.isFullyOperational)
    }

    @Test
    fun `initial state reflects permissions-only no GPS`() {
        fakePermissions.emit(FakePermissionManager.permissionsOnlyNoGps())
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)

        assertTrue(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.locationServicesEnabled)
        assertFalse(vm.state.value.isFullyOperational)
    }

    // ── State updates ─────────────────────────────────────────────────────────

    @Test
    fun `state updates when permissions are granted after creation`() = runTest {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)
        assertFalse(vm.state.value.isFullyOperational)

        fakePermissions.emit(FakePermissionManager.allGranted())

        assertTrue(vm.state.value.permissionsGranted)
        assertTrue(vm.state.value.locationServicesEnabled)
        assertTrue(vm.state.value.isFullyOperational)
    }

    @Test
    fun `state updates when permissions are revoked mid-session`() = runTest {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)
        assertTrue(vm.state.value.isFullyOperational)

        fakePermissions.emit(FakePermissionManager.allDenied())

        assertFalse(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.isFullyOperational)
    }

    @Test
    fun `state updates when GPS is toggled off with permissions kept`() = runTest {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)
        assertTrue(vm.state.value.isFullyOperational)

        fakePermissions.emit(FakePermissionManager.permissionsOnlyNoGps())

        assertTrue(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.locationServicesEnabled)
        assertFalse(vm.state.value.isFullyOperational)
    }

    // ── Connectivity banner ─────────────────────────────────────────────────────

    @Test
    fun `cold start online keeps banner hidden`() {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)

        assertEquals(ConnectivityBannerPhase.Hidden, vm.state.value.connectivityBanner)
    }

    @Test
    fun `cold start offline shows offline banner synchronously`() {
        val offlineConnectivity = FakeConnectivityObserver(ConnectivityStatus.Offline)
        val vm = AppViewModel(fakePermissions, fakePrefs, offlineConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)

        assertEquals(ConnectivityBannerPhase.Offline, vm.state.value.connectivityBanner)
    }

    @Test
    fun `going offline shows offline banner`() = runTest(testDispatcher) {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)
        assertEquals(ConnectivityBannerPhase.Hidden, vm.state.value.connectivityBanner)

        fakeConnectivity.emit(ConnectivityStatus.Offline)

        assertEquals(ConnectivityBannerPhase.Offline, vm.state.value.connectivityBanner)
    }

    @Test
    fun `reconnect shows restored banner then auto-hides`() = runTest(testDispatcher) {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)
        fakeConnectivity.emit(ConnectivityStatus.Offline)

        fakeConnectivity.emit(ConnectivityStatus.Online)
        assertEquals(ConnectivityBannerPhase.Restored, vm.state.value.connectivityBanner)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(ConnectivityBannerPhase.Hidden, vm.state.value.connectivityBanner)
    }

    @Test
    fun `going offline again before auto-hide keeps offline banner`() = runTest(testDispatcher) {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)
        fakeConnectivity.emit(ConnectivityStatus.Offline)
        fakeConnectivity.emit(ConnectivityStatus.Online) // Restored, hide scheduled

        fakeConnectivity.emit(ConnectivityStatus.Offline) // back offline before the hide fires

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(ConnectivityBannerPhase.Offline, vm.state.value.connectivityBanner)
    }

    @Test
    fun `drains pending vehicles on online cold start`() {
        AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)

        assertEquals(1, fakeVehicleRepo.pushPendingCallCount) // online → outbox drained once at init
    }

    @Test
    fun `does not drain pending vehicles on offline cold start`() {
        val offline = FakeConnectivityObserver(ConnectivityStatus.Offline)
        AppViewModel(fakePermissions, fakePrefs, offline, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)

        assertEquals(0, fakeVehicleRepo.pushPendingCallCount) // offline → nothing to push yet
    }

    @Test
    fun `drains pending vehicles on reconnect`() = runTest(testDispatcher) {
        val offline = FakeConnectivityObserver(ConnectivityStatus.Offline)
        AppViewModel(fakePermissions, fakePrefs, offline, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)
        assertEquals(0, fakeVehicleRepo.pushPendingCallCount)

        offline.emit(ConnectivityStatus.Online) // reconnect → push the offline edits to the cloud

        assertEquals(1, fakeVehicleRepo.pushPendingCallCount)
        assertEquals(1, fakeZoneRepo.pushPendingCallCount) // zones drain on the same trigger
    }

    // ── Intents ───────────────────────────────────────────────────────────────

    @Test
    fun `MarkOnboardingCompleted calls setOnboardingCompleted once`() {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)
        assertEquals(0, fakePrefs.setOnboardingCompletedCount)

        vm.handleIntent(AppIntent.MarkOnboardingCompleted)

        assertEquals(1, fakePrefs.setOnboardingCompletedCount)
        assertTrue(fakePrefs.isOnboardingCompleted)
    }

    @Test
    fun `MarkOnboardingCompleted is idempotent`() {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)

        vm.handleIntent(AppIntent.MarkOnboardingCompleted)
        vm.handleIntent(AppIntent.MarkOnboardingCompleted)

        assertEquals(2, fakePrefs.setOnboardingCompletedCount)
        assertTrue(fakePrefs.isOnboardingCompleted)
    }

    // ── Legal consent [AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001] ────────────────

    @Test
    fun `should_startWithoutConsent_when_neverAccepted`() {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)

        // A fresh install must ASK: the auth screen reads this flag to decide whether to draw the
        // checkbox and gate its CTAs.
        assertFalse(vm.state.value.hasAcceptedLegalConsent)
    }

    @Test
    fun `should_persistConsent_when_accepted`() {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)

        vm.handleIntent(AppIntent.AcceptLegalConsent)

        // Both halves matter: the live state ungates the CTAs in THIS composition, and the
        // preference is what survives the process.
        assertTrue(vm.state.value.hasAcceptedLegalConsent)
        assertTrue(fakePrefs.hasAcceptedLegalConsent)
    }

    @Test
    fun `should_notAskAgain_when_consentWasAcceptedOnAPreviousRun`() {
        // Accept it, then throw the ViewModel away — this is a relaunch, and the only thing that
        // crosses over is the persisted preference.
        AppViewModel(fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo)
            .handleIntent(AppIntent.AcceptLegalConsent)

        val relaunched = AppViewModel(
            fakePermissions, fakePrefs, fakeConnectivity, fakeVehicleRepo, fakeZoneRepo, fakeParkingRepo,
        )

        // True at construction time (not after some later emission): the auth screen decides whether
        // to draw the consent row on its FIRST composition, so a late value would flash the checkbox
        // at someone who already accepted.
        assertTrue(relaunched.state.value.hasAcceptedLegalConsent)
    }
}
