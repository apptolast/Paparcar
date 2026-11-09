package com.rndeveloper.paparcar.presentation.app

import app.cash.turbine.test
import com.apptolast.baselogin.domain.model.AuthState
import com.rndeveloper.paparcar.Routes
import com.rndeveloper.paparcar.presentation.app.BootstrapFailure
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.testSignOutUseCase
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeConnectivityObserver
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeLocalSessionCache
import com.rndeveloper.paparcar.fakes.FakePermissionManager
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeUserProfileRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import com.rndeveloper.paparcar.domain.usecase.user.BootstrapUserDataUseCase
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
import com.rndeveloper.paparcar.domain.usecase.user.SetFirstStepsDismissedUseCase
import kotlinx.coroutines.CompletableDeferred
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
    private lateinit var fakeGeofenceManager: FakeGeofenceManager
    private lateinit var fakeEntitlementRepo: FakeEntitlementRepository

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
        fakeGeofenceManager = FakeGeofenceManager()
        fakeEntitlementRepo = FakeEntitlementRepository()
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
            signOut = testSignOutUseCase(fakeAuth),
            pushTokens = com.rndeveloper.paparcar.fakes.data.repository.FakeNoopPushTokenProvider(),
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
            localSessionCache = fakeSessionCache,
            connectivityObserver = fakeConnectivity,
            geofenceManager = fakeGeofenceManager,
            entitlementRepository = fakeEntitlementRepo,
            setFirstStepsDismissed = SetFirstStepsDismissedUseCase(
                appPreferences = fakePrefs,
                authRepository = fakeAuth,
                userProfileRepository = fakeProfileRepo,
            ),
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

    /**
     * The branch this pins is `isReady`'s middle row: **Authenticated, no startRoute, no failure**
     * — the window where the user is signed in but the bootstrap has not decided where to send
     * them, and the native splash must stay up rather than flash a blank screen.
     *
     * It needs the latch to exist at all. Under the unconfined dispatcher `emitState` runs profile
     * sync, user-data bootstrap and route resolution to completion before it returns, so the window
     * closes before any assert can see it. That is how this test previously ended up building a
     * plain view model and asserting `assertFalse(vm.isReady)` on the LOADING state — a body byte
     * for byte identical to `isReady is false while auth state is Loading` above, passing under a
     * name that promised the authenticated case. [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    @Test
    fun `isReady stays false for Authenticated until startRoute is resolved`() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeProfileRepo.profileGate = gate
        val vm = buildViewModel()

        fakeAuth.emitState(AuthState.Authenticated(session))

        // Held mid-bootstrap: auth IS resolved, so this is not the Loading row.
        assertEquals(1, fakeProfileRepo.getOrCreateCallCount, "bootstrap must have started")
        assertNull(vm.state.value.startRoute, "the route cannot be resolved yet")
        assertNull(vm.state.value.bootstrapFailure, "and nothing has failed — this is the in-flight row")
        assertFalse(vm.isReady, "authenticated without a route and without a failure is NOT ready")

        // Releasing the latch is what flips it — the assert above is about the route, not the clock.
        gate.complete(Unit)
        assertEquals(Routes.ONBOARDING, vm.state.value.startRoute)
        assertTrue(vm.isReady)
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
    fun `OS geofences are drained when auth transitions to Unauthenticated`() = runTest {
        buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))
        fakeAuth.emitState(AuthState.Unauthenticated)

        // Geofences live in the OS, not in Room: signing out must deregister them so the
        // previous user's parking geofence cannot fire under the next account. [SESSION-ISOLATION-001]
        assertEquals(1, fakeGeofenceManager.removeAllCallCount)
    }

    /**
     * The third local store. Room and the geofences were already covered above; the preferences
     * were the one `wipeLocalUserData` never touched, which is how the outgoing user's guided
     * tutorial — and their unanswered "where did you leave your car?" — greeted the next account on
     * the same phone. [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
     *
     * Asserted on the CALL and not only on the emptiness of the fake: a fake that starts empty
     * would let a view model that never calls it pass.
     * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    @Test
    fun `user-scoped preferences are wiped when auth transitions to Unauthenticated`() = runTest {
        buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))
        fakePrefs.firstStepsDone.value = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH)
        fakePrefs.firstStepsDeferred.value = setOf(FirstStep.FORTIFY_WATCH)
        fakePrefs.firstStepsDismissed.value = true

        fakeAuth.emitState(AuthState.Unauthenticated)

        assertEquals(1, fakePrefs.clearUserScopedStateCount, "the sign-out must reach the preferences")
        assertEquals(emptySet(), fakePrefs.firstStepsDone.value)
        assertEquals(emptySet(), fakePrefs.firstStepsDeferred.value)
        assertFalse(fakePrefs.firstStepsDismissed.value)
    }

    /**
     * The other half of the same rule: what describes this INSTALL survives the sign-out. Without
     * this witness, "wipe the preferences" could quietly become `clear()` and send a user who
     * merely signed out back through onboarding and the ad-measurement question on their own phone.
     * [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
     */
    @Test
    fun `device-scoped preferences survive the sign-out wipe`() = runTest {
        fakePrefs = FakeAppPreferences(initialCompleted = true)
        fakePrefs.setAdMeasurementConsent(AdMeasurementConsent.DENIED)
        buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        fakeAuth.emitState(AuthState.Unauthenticated)

        assertTrue(fakePrefs.isOnboardingCompleted, "onboarding is a fact about this install")
        // A "no" given on this phone must not turn back into "never asked" because someone signed
        // out: that would re-ask, which is exactly the nagging the answer was meant to end.
        assertEquals(
            AdMeasurementConsent.DENIED,
            fakePrefs.adMeasurementConsent,
            "ad consent is per-install by MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001",
        )
    }

    // ── The tutorial's memory lives on the ACCOUNT ────────────────────────────
    // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]

    /**
     * The reported bug, in a test: reinstall (empty preferences) + sign in with an account that
     * already closed the checklist → it must not come back. It used to, halfway through, because
     * the live half of the projection ticks the steps the user's real parking already proves.
     */
    @Test
    fun `a dismissal stored on the account is restored on a fresh install`() = runTest {
        fakeProfileRepo.getOrCreateResult = Result.success(
            FakeUserProfileRepository.defaultProfile().copy(firstStepsDismissedAt = 1_700_000_000_000L),
        )
        buildViewModel()

        fakeAuth.emitState(AuthState.Authenticated(session))

        assertTrue(fakePrefs.firstStepsDismissed.value, "the account remembered it; this device must too")
        assertTrue(fakeProfileRepo.firstStepsDismissedWrites.isEmpty(), "restoring must not re-stamp the account")
    }

    /** An account that never dismissed it must not have the checklist hidden by the reconcile. */
    @Test
    fun `an account with no dismissal leaves the checklist on offer`() = runTest {
        buildViewModel()

        fakeAuth.emitState(AuthState.Authenticated(session))

        assertFalse(fakePrefs.firstStepsDismissed.value)
        assertTrue(fakeProfileRepo.firstStepsDismissedWrites.isEmpty())
    }

    /**
     * The repair direction: the local write landed but the remote one did not (offline when the
     * user closed it). The next online start mirrors it up by itself — otherwise the account would
     * only learn about it if the user closed the same checklist a second time.
     */
    @Test
    fun `a dismissal the account never learned is mirrored up at the next start`() = runTest {
        fakePrefs.firstStepsDismissed.value = true
        buildViewModel()

        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(1, fakeProfileRepo.firstStepsDismissedWrites.size)
        assertTrue(fakeProfileRepo.firstStepsDismissedWrites.single() != null, "it is stamped, not cleared")
        assertTrue(fakePrefs.firstStepsDismissed.value, "and the local answer does not change")
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
    fun `startRoute resolves to HOME even with no runtime permissions granted`() = runTest {
        // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001] No cold-start permission gate: Home
        // leads (BlockedCore surfaces the absence) and the fine-location ask happens on the map.
        fakePrefs.setOnboardingCompleted()
        setProfileWithVehicle()
        // permissions remain default (none granted)

        val vm = buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(Routes.HOME, vm.state.value.startRoute)
    }

    @Test
    fun `startRoute resolves to VEHICLE_SIZE_EXPLAINER when no vehicle and no perms`() = runTest {
        // Vehicle registration outranks permissions now: the garage is needed for the product,
        // the location ask waits for the map. [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
        fakePrefs.setOnboardingCompleted()
        // No vehicle, no permissions granted.

        val vm = buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(Routes.VEHICLE_SIZE_EXPLAINER, vm.state.value.startRoute)
    }

    @Test
    fun `startRoute resolves to HOME when permissions granted but GPS services disabled`() = runTest {
        // GPS off is Home's BlockedCore story to tell, not a gate. [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
        fakePrefs.setOnboardingCompleted()
        setProfileWithVehicle()
        fakePerms.emit(FakePermissionManager.permissionsOnlyNoGps())

        val vm = buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(Routes.HOME, vm.state.value.startRoute)
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
        // No cached vehicle (fresh fakes) → offline cold-start with nothing to render keeps the
        // blocking retry dialog. [RETRY-GATE-001]
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

    @Test
    fun `enters app offline-first resolving startRoute without retry when offline but data is cached`() = runTest {
        // Returning user: onboarding done, permissions granted, and a vehicle already cached in Room.
        fakePrefs.setOnboardingCompleted()
        setProfileWithVehicle()
        fakePerms.emit(FakePermissionManager.allGranted())

        val vm = buildViewModel(connectivityStatus = ConnectivityStatus.Offline)
        fakeAuth.emitState(AuthState.Authenticated(session))

        // Offline + cache → straight into the app; the connectivity banner surfaces the state.
        assertEquals(Routes.HOME, vm.state.value.startRoute)
        assertNull(vm.state.value.bootstrapFailure)
        // The remote bootstrap (profile sync) must be skipped entirely while offline. [RETRY-GATE-001]
        assertEquals(0, fakeProfileRepo.getOrCreateCallCount)
    }

    // ── Store identity (RevenueCat logIn/logOut) [MONEY-001] ─────────────────

    @Test
    fun `should_bindStoreIdentityToTheUid_when_authenticated`() = runTest {
        val vm = buildViewModel()

        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(1, fakeEntitlementRepo.logInCallCount)
        assertEquals(session.userId, fakeEntitlementRepo.lastLoggedInUserId)
        // And it never runs before the identity is known: no call on the Loading state.
        assertNull(vm.state.value.bootstrapFailure)
    }

    @Test
    fun `should_finishBootstrap_when_storeLogInFails`() = runTest {
        fakeEntitlementRepo.logInResult = Result.failure(RuntimeException("RC unreachable"))
        val vm = buildViewModel()

        fakeAuth.emitState(AuthState.Authenticated(session))

        // Non-fatal by contract: route resolved, nobody signed out, no failure surfaced.
        assertEquals(Routes.ONBOARDING, vm.state.value.startRoute)
        assertEquals(0, fakeAuth.signOutCount)
        assertNull(vm.state.value.bootstrapFailure)
    }

    @Test
    fun `should_unbindStoreIdentity_when_unauthenticated`() = runTest {
        buildViewModel()

        fakeAuth.emitState(AuthState.Unauthenticated)

        assertEquals(1, fakeEntitlementRepo.logOutCallCount)
    }

    @Test
    fun `should_stillWipeTheLocalCache_when_storeLogOutFails`() = runTest {
        fakeEntitlementRepo.logOutResult = Result.failure(RuntimeException("RC unreachable"))
        buildViewModel()

        fakeAuth.emitState(AuthState.Unauthenticated)

        assertEquals(1, fakeSessionCache.wipeCount)
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
