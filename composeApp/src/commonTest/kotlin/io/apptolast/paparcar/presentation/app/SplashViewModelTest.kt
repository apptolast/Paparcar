package io.apptolast.paparcar.presentation.app

import app.cash.turbine.test
import com.apptolast.customlogin.domain.model.AuthState
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeUserProfileRepository
import io.apptolast.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
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
class SplashViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val session = FakeAuthRepository.authenticatedSession()

    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakeProfileRepo: FakeUserProfileRepository
    private lateinit var fakeParkingRepo: FakeUserParkingRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuth = FakeAuthRepository(initialState = AuthState.Loading)
        fakeProfileRepo = FakeUserProfileRepository()
        fakeParkingRepo = FakeUserParkingRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = SplashViewModel(
        authRepository = fakeAuth,
        getOrCreateUserProfile = GetOrCreateUserProfileUseCase(
            userProfileRepository = fakeProfileRepo,
            userParkingRepository = fakeParkingRepo,
            authRepository = fakeAuth,
        ),
    )

    // ── isReady ───────────────────────────────────────────────────────────────

    @Test
    fun `isReady is false while auth state is Loading`() {
        val vm = buildViewModel()
        assertFalse(vm.isReady)
    }

    @Test
    fun `isReady is true once auth state resolves to Authenticated`() = runTest {
        fakeAuth.emitState(AuthState.Authenticated(session))
        val vm = buildViewModel()
        assertTrue(vm.isReady)
    }

    @Test
    fun `isReady is true when auth state is Unauthenticated`() = runTest {
        fakeAuth.emitState(AuthState.Unauthenticated)
        val vm = buildViewModel()
        assertTrue(vm.isReady)
    }

    // ── Profile sync on auth ──────────────────────────────────────────────────

    @Test
    fun `getOrCreateProfile is called when user authenticates`() = runTest {
        buildViewModel()
        fakeAuth.emitState(AuthState.Authenticated(session))

        assertEquals(1, fakeProfileRepo.getOrCreateCallCount)
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
}
