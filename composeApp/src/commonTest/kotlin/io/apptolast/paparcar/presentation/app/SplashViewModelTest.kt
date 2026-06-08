package io.apptolast.paparcar.presentation.app

import app.cash.turbine.test
import com.apptolast.customlogin.domain.model.AuthState
import io.apptolast.paparcar.Routes
import io.apptolast.paparcar.presentation.app.BootstrapFailure
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.fakes.FakeAppPreferences
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeConnectivityObserver
import io.apptolast.paparcar.fakes.FakeLocalSessionCache
import io.apptolast.paparcar.fakes.FakePermissionManager
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeUserProfileRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import io.apptolast.paparcar.fakes.FakeZoneRepository
import io.apptolast.paparcar.domain.usecase.user.BootstrapUserDataUseCase
import io.apptolast.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val session = FakeAuthRepository.authenticatedSession()

    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakeProfileRepo: FakeUserProfileRepository
    private lateinit var fakeParkingRepo: FakeUserParkingRepository
    private lateinit var fakeVehicleRepo: FakeVehicleRepository
    private lateinit var fakeZoneRepo: FakeZoneRepository
    private lateinit var fakePrefs: FakeAppPreferences
    private lateinit var fakePerms: FakePermissionManager
    private lateinit var fakeSessionCache: FakeLocalSessionCache
    private lateinit var fakeConnectivity: FakeConnectivityObserver

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuth = FakeAuthRepository(initialState = AuthState.Loading)
        fakeProfileRepo = FakeUserProfileRepository()
        fakeParkingRepo = FakeUserParkingRepository()
        fakeVehicleRepo = FakeVehicleRepository()
        fakeZoneRepo = FakeZoneRepository()
        fakePrefs = FakeAppPreferences()
        fakePerms = FakePermissionManager()
        fakeSessionCache = FakeLocalSessionCache()
        fakeConnectivity = FakeConnectivityObserver()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        connectivityStatus: ConnectivityStatus = ConnectivityStatus.Online,
    ): SplashViewModel {
        fakeConnectivity.emit(connectivityStatus)
        return SplashViewModel(
            authRepository = fakeAuth,
            getOrCreateUserProfile = GetOrCreateUserProfileUseCase(
                userProfileRepository = fakeProfileRepo,
                authRepository = fakeAuth,
            ),
            bootstrapUserData = BootstrapUserDataUseCase(
                vehicleRepository = fakeVehicleRepo,
                userParkingRepository = fakeParkingRepo,
                zoneRepository = fakeZoneRepo,
            ),
            vehicleRepository = fakeVehicleRepo,
            appPreferences = fakePrefs,
            permissionManager = fakePerms,
            localSessionCache = fakeSessionCache,
            connectivityObserver = fakeConnectivity,
        )
    }

    private fun vehicle() = Vehicle(
        id = "v-1",
        userId = "user-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        isActive = true,
    )

    // ── isReady ───────────────────────────────────────────────────────────────

    @Test
    fun `isReady is false while auth state is Loading`() {
        val vm = buildViewModel()
        assertFalse(vm.isReady)
    }

    @Test
    fun `isReady is true once auth state resolves to Unauthenticated`() = runTest {
        fakeAuth.emitState(AuthState.Unauthenticated)
        val vm = buildViewModel()
        assertTrue(vm.isReady)
    }

    @Test
    fun `isReady stays false for Authenticated until startRoute is resolved`() {
        // Profile use case is unset → succeeds, but resolveStartRoute requires explicit drive.
        val vm = buildViewModel()
        // Loading initial state → not ready.
        assertFalse(vm.isReady)
    }

    @Test
    fun `isReady becomes true for Authenticated once startRoute is set`() = runTest {
        // All defaults: no vehicle, onboarding not completed, no permissions.
        val vm = buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))
        // After resolveStartRoute runs, startRoute must be ONBOARDING (since not completed).
        assertEquals(Routes.ONBOARDING, vm.state.value.startRoute)
        assertTrue(vm.isReady)
    }

    // ── Profile sync on auth ──────────────────────────────────────────────────

    @Test
    fun `getOrCreateProfile is called when user authenticates`() = runTest {
        buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(1, fakeProfileRepo.getOrCreateCallCount)
    }

    // ── Session isolation: wipe on sign-out ───────────────────────────────────

    @Test
    fun `local session cache is wiped when auth transitions to Unauthenticated`() = runTest {
        buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))
        fakeAuth.emitState(AuthState.Unauthenticated)

        assertEquals(1, fakeSessionCache.wipeCount)
    }

    @Test
    fun `local session cache is wiped before next sign-in bootstraps`() = runTest {
        // Simulates user A signing out and user B signing in: the wipe between
        // them must run before the bootstrap for B, so the previous user's data
        // cannot leak into the new session.
        buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))
        val callsAfterA = fakeProfileRepo.getOrCreateCallCount

        fakeAuth.emitState(AuthState.Unauthenticated)
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(1, fakeSessionCache.wipeCount)
        assertEquals(callsAfterA + 1, fakeProfileRepo.getOrCreateCallCount)
    }

    @Test
    fun `getOrCreateProfile is not called when user is unauthenticated`() = runTest {
        buildViewModel()
        fakeAuth.emitState(AuthState.Unauthenticated)

        assertEquals(0, fakeProfileRepo.getOrCreateCallCount)
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    fun `emits ShowError effect and signs out when profile sync fails`() = runTest {
        fakeProfileRepo.getOrCreateResult = Result.failure(RuntimeException("network error"))

        val vm = buildViewModel()
        vm.effect.test {
            fakeAuth.emitState(AuthState.Authenticated(session))

            val effect = awaitItem()
            assertTrue(effect is SplashEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fakeAuth.signOutCount)
        // startRoute must remain null on sync failure — we never enter the app.
        assertNull(vm.state.value.startRoute)
    }

    @Test
    fun `does not emit effect when profile sync succeeds`() = runTest {
        val vm = buildViewModel()
        vm.effect.test {
            fakeAuth.emitState(AuthState.Authenticated(session))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits ShowError and signs out when vehicle sync fails inside bootstrapUserData`() = runTest {
        fakeVehicleRepo.syncFromRemoteResult = Result.failure(RuntimeException("vehicles down"))

        val vm = buildViewModel()
        vm.effect.test {
            fakeAuth.emitState(AuthState.Authenticated(session))

            val effect = awaitItem()
            assertTrue(effect is SplashEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fakeAuth.signOutCount)
        assertNull(vm.state.value.startRoute)
    }

    @Test
    fun `emits ShowError and signs out when parking history sync fails inside bootstrapUserData`() = runTest {
        fakeParkingRepo.syncResult = Result.failure(RuntimeException("parking down"))

        val vm = buildViewModel()
        vm.effect.test {
            fakeAuth.emitState(AuthState.Authenticated(session))

            val effect = awaitItem()
            assertTrue(effect is SplashEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fakeAuth.signOutCount)
        assertNull(vm.state.value.startRoute)
    }

    @Test
    fun `emits ShowError and signs out when zone sync fails inside bootstrapUserData`() = runTest {
        fakeZoneRepo.syncFromRemoteResult = Result.failure(RuntimeException("zones down"))

        val vm = buildViewModel()
        vm.effect.test {
            fakeAuth.emitState(AuthState.Authenticated(session))

            val effect = awaitItem()
            assertTrue(effect is SplashEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fakeAuth.signOutCount)
        assertNull(vm.state.value.startRoute)
    }

    // ── startRoute resolution ────────────────────────────────────────────────

    @Test
    fun `startRoute resolves to ONBOARDING when onboarding not completed`() = runTest {
        // Has vehicle and permissions but onboarding flag is false.
        fakeVehicleRepo.saveVehicle(vehicle())
        fakePerms.emit(FakePermissionManager.allGranted())

        val vm = buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(Routes.ONBOARDING, vm.state.value.startRoute)
    }

    @Test
    fun `startRoute resolves to VEHICLE_SIZE_EXPLAINER when all else is OK but no vehicle`() = runTest {
        fakePrefs.setOnboardingCompleted()
        fakePerms.emit(FakePermissionManager.allGranted())
        // Profile has no defaultVehicleId — the splash's hasVehicle signal.

        val vm = buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(Routes.VEHICLE_SIZE_EXPLAINER, vm.state.value.startRoute)
    }

    @Test
    fun `startRoute resolves to PERMISSIONS_RATIONALE when onboarding done but missing runtime perms (vehicle present)`() = runTest {
        fakePrefs.setOnboardingCompleted()
        setProfileWithVehicle()
        // permissions remain default (none granted)

        val vm = buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(Routes.PERMISSIONS_RATIONALE, vm.state.value.startRoute)
    }

    @Test
    fun `startRoute resolves to PERMISSIONS_RATIONALE when onboarding done and no perms (regardless of vehicle)`() = runTest {
        // First-run flow: permissions take priority over vehicle. The user lands on the
        // rationale before being asked for a vehicle.
        fakePrefs.setOnboardingCompleted()
        // No vehicle, no permissions granted.

        val vm = buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(Routes.PERMISSIONS_RATIONALE, vm.state.value.startRoute)
    }

    @Test
    fun `startRoute resolves to PERMISSIONS when permissions granted but GPS services disabled`() = runTest {
        fakePrefs.setOnboardingCompleted()
        setProfileWithVehicle()
        fakePerms.emit(FakePermissionManager.permissionsOnlyNoGps())

        val vm = buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(Routes.PERMISSIONS, vm.state.value.startRoute)
    }

    @Test
    fun `startRoute resolves to HOME when all invariants are satisfied`() = runTest {
        fakePrefs.setOnboardingCompleted()
        setProfileWithVehicle()
        fakePerms.emit(FakePermissionManager.allGranted())

        val vm = buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(Routes.HOME, vm.state.value.startRoute)
    }

    // ── Offline bootstrap ────────────────────────────────────────────────────

    @Test
    fun `emits ShowOfflineError and does NOT sign out when device is offline at login`() = runTest {
        val vm = buildViewModel(connectivityStatus = ConnectivityStatus.Offline)
        vm.effect.test {
            fakeAuth.emitState(AuthState.Authenticated(session))

            val effect = awaitItem()
            assertTrue(effect is SplashEffect.ShowOfflineError)
            cancelAndIgnoreRemainingEvents()
        }
        // User must NOT be signed out — they can retry when back online.
        assertEquals(0, fakeAuth.signOutCount)
        assertNull(vm.state.value.startRoute)
        assertEquals(BootstrapFailure.Offline, vm.state.value.bootstrapFailure)
    }

    @Test
    fun `retry clears failure and resolves startRoute when connectivity is restored`() = runTest {
        val vm = buildViewModel(connectivityStatus = ConnectivityStatus.Offline)
        fakeAuth.emitState(AuthState.Authenticated(session))
        // Confirm we are in the offline failure state.
        assertEquals(BootstrapFailure.Offline, vm.state.value.bootstrapFailure)

        // Restore connectivity and retry.
        fakeConnectivity.emit(ConnectivityStatus.Online)
        vm.retry()

        // startRoute should now resolve (defaults → ONBOARDING).
        assertEquals(Routes.ONBOARDING, vm.state.value.startRoute)
        assertNull(vm.state.value.bootstrapFailure)
    }

    /** Configures the fake profile repo to return a profile with a vehicle pointer set,
     *  AND seeds the vehicle repo so `hasVehicles(userId)` returns true — NAV-001 reads
     *  the Room count directly instead of the profile pointer. */
    private fun setProfileWithVehicle() {
        fakeProfileRepo.getOrCreateResult = Result.success(
            FakeUserProfileRepository.defaultProfile().copy(defaultVehicleId = "v-1"),
        )
        runBlocking {
            fakeVehicleRepo.saveVehicle(
                vehicle().copy(userId = session.userId),
            )
        }
    }
}
