package io.apptolast.paparcar.presentation.app

import io.apptolast.paparcar.fakes.FakeAppPreferences
import io.apptolast.paparcar.fakes.FakeConnectivityObserver
import io.apptolast.paparcar.fakes.FakePermissionManager
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakePermissions: FakePermissionManager
    private lateinit var fakePrefs: FakeAppPreferences
    private lateinit var fakeConnectivity: FakeConnectivityObserver

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePermissions = FakePermissionManager()
        fakePrefs = FakeAppPreferences()
        fakeConnectivity = FakeConnectivityObserver()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Test
    fun `initial state is all false when no permissions granted`() {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity)

        assertFalse(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.locationServicesEnabled)
        assertFalse(vm.state.value.isFullyOperational)
    }

    @Test
    fun `initial state reflects current permission state synchronously`() {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity)

        assertTrue(vm.state.value.permissionsGranted)
        assertTrue(vm.state.value.locationServicesEnabled)
        assertTrue(vm.state.value.isFullyOperational)
    }

    @Test
    fun `initial state reflects permissions-only no GPS`() {
        fakePermissions.emit(FakePermissionManager.permissionsOnlyNoGps())
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity)

        assertTrue(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.locationServicesEnabled)
        assertFalse(vm.state.value.isFullyOperational)
    }

    // ── State updates ─────────────────────────────────────────────────────────

    @Test
    fun `state updates when permissions are granted after creation`() = runTest {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity)
        assertFalse(vm.state.value.isFullyOperational)

        fakePermissions.emit(FakePermissionManager.allGranted())

        assertTrue(vm.state.value.permissionsGranted)
        assertTrue(vm.state.value.locationServicesEnabled)
        assertTrue(vm.state.value.isFullyOperational)
    }

    @Test
    fun `state updates when permissions are revoked mid-session`() = runTest {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity)
        assertTrue(vm.state.value.isFullyOperational)

        fakePermissions.emit(FakePermissionManager.allDenied())

        assertFalse(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.isFullyOperational)
    }

    @Test
    fun `state updates when GPS is toggled off with permissions kept`() = runTest {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity)
        assertTrue(vm.state.value.isFullyOperational)

        fakePermissions.emit(FakePermissionManager.permissionsOnlyNoGps())

        assertTrue(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.locationServicesEnabled)
        assertFalse(vm.state.value.isFullyOperational)
    }

    // ── Intents ───────────────────────────────────────────────────────────────

    @Test
    fun `MarkOnboardingCompleted calls setOnboardingCompleted once`() {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity)
        assertEquals(0, fakePrefs.setOnboardingCompletedCount)

        vm.handleIntent(AppIntent.MarkOnboardingCompleted)

        assertEquals(1, fakePrefs.setOnboardingCompletedCount)
        assertTrue(fakePrefs.isOnboardingCompleted)
    }

    @Test
    fun `MarkOnboardingCompleted is idempotent`() {
        val vm = AppViewModel(fakePermissions, fakePrefs, fakeConnectivity)

        vm.handleIntent(AppIntent.MarkOnboardingCompleted)
        vm.handleIntent(AppIntent.MarkOnboardingCompleted)

        assertEquals(2, fakePrefs.setOnboardingCompletedCount)
        assertTrue(fakePrefs.isOnboardingCompleted)
    }
}
