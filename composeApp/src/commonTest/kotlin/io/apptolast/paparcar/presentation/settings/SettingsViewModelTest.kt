package io.apptolast.paparcar.presentation.settings

import app.cash.turbine.test
import io.apptolast.paparcar.domain.usecase.user.DeleteAccountUseCase
import io.apptolast.paparcar.fakes.FakeAppPreferences
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeSpotRepository
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeUserProfileRepository
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
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val session = FakeAuthRepository.authenticatedSession()
    private lateinit var auth: FakeAuthRepository
    private lateinit var parking: FakeUserParkingRepository
    private lateinit var vehicles: FakeVehicleRepository
    private lateinit var profile: FakeUserProfileRepository
    private lateinit var spots: FakeSpotRepository
    private lateinit var prefs: FakeAppPreferences
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
        vm = buildVm()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(customPrefs: FakeAppPreferences = prefs): SettingsViewModel {
        val useCase = DeleteAccountUseCase(auth, parking, vehicles, profile, spots)
        return SettingsViewModel(customPrefs, auth, profile, useCase)
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
    fun `should_notEmitNavigateToAuth_on_deleteAccount_failure`() = runTest {
        auth.deleteAccountResult = Result.failure(Exception("network error"))
        vm.effect.test {
            vm.handleIntent(SettingsIntent.ConfirmDeleteAccount)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Navigation intents ────────────────────────────────────────────────────

    @Test
    fun `should_emitNavigateBack_on_navigateBack`() = runTest {
        vm.effect.test {
            vm.handleIntent(SettingsIntent.NavigateBack)
            assertIs<SettingsEffect.NavigateBack>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emitNavigateToVehicles_on_navigateToVehicles`() = runTest {
        vm.effect.test {
            vm.handleIntent(SettingsIntent.NavigateToVehicles)
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
