@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.vehicle

import app.cash.turbine.test
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.fakes.FakeAppPreferences
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VehicleRegistrationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var vehicleRepo: FakeVehicleRepository
    private lateinit var auth: FakeAuthRepository
    private lateinit var prefs: FakeAppPreferences
    private lateinit var vm: VehicleRegistrationViewModel

    private fun vehicle(id: String) = Vehicle(
        id = id,
        userId = "user-1",
        brand = "Toyota",
        model = "Corolla",
        sizeCategory = VehicleSize.MEDIUM,
        isDefault = true,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vehicleRepo = FakeVehicleRepository()
        auth = FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession("user-1"))
        prefs = FakeAppPreferences()
        vm = VehicleRegistrationViewModel(vehicleRepo, auth, prefs)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Field intents ─────────────────────────────────────────────────────────

    @Test
    fun `should_updateBrand_on_SetBrand`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetBrand("Ford"))
        assertEquals("Ford", vm.state.value.brand)
    }

    @Test
    fun `should_updateModel_on_SetModel`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetModel("Focus"))
        assertEquals("Focus", vm.state.value.model)
    }

    @Test
    fun `should_updateSize_on_SetSize`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetSize(VehicleSize.VAN))
        assertEquals(VehicleSize.VAN, vm.state.value.sizeCategory)
    }

    @Test
    fun `should_updateShowOnSpot_on_SetShowOnSpot`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetShowOnSpot(true))
        assertTrue(vm.state.value.showBrandModelOnSpot)
    }

    // ── Save — success ────────────────────────────────────────────────────────

    @Test
    fun `should_emit_SavedSuccessfully_on_valid_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetBrand("Seat"))
        vm.handleIntent(VehicleRegistrationIntent.SetModel("Ibiza"))
        vm.handleIntent(VehicleRegistrationIntent.SetSize(VehicleSize.SMALL))

        vm.effect.test {
            vm.handleIntent(VehicleRegistrationIntent.Save)
            assertIs<VehicleRegistrationEffect.SavedSuccessfully>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_persist_vehicle_to_repo_on_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetBrand("Seat"))
        vm.handleIntent(VehicleRegistrationIntent.SetModel("Ibiza"))
        vm.handleIntent(VehicleRegistrationIntent.SetSize(VehicleSize.SMALL))
        vm.handleIntent(VehicleRegistrationIntent.Save)

        val saved = vehicleRepo.observeDefaultVehicle().first()
        assertEquals("Ibiza", saved?.model)
    }

    @Test
    fun `should_mark_vehicle_registered_in_prefs_on_new_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetSize(VehicleSize.MEDIUM))
        vm.handleIntent(VehicleRegistrationIntent.Save)

        assertTrue(prefs.hasVehicleRegistered)
    }

    @Test
    fun `should_trim_blank_brand_to_null_on_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetBrand("   "))
        vm.handleIntent(VehicleRegistrationIntent.SetSize(VehicleSize.MEDIUM))
        vm.handleIntent(VehicleRegistrationIntent.Save)

        assertNull(vehicleRepo.observeDefaultVehicle().first()?.brand)
    }

    // ── Save — validation failure ─────────────────────────────────────────────

    @Test
    fun `should_emit_ShowError_when_size_is_null_on_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetBrand("Ford"))

        vm.effect.test {
            vm.handleIntent(VehicleRegistrationIntent.Save)
            assertIs<VehicleRegistrationEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Load (edit mode) ──────────────────────────────────────────────────────

    @Test
    fun `should_populate_state_when_loading_existing_vehicle`() = runTest {
        val v = vehicle("v-edit")
        vehicleRepo.saveVehicle(v)

        vm.handleIntent(VehicleRegistrationIntent.LoadVehicle("v-edit"))

        assertEquals("Toyota", vm.state.value.brand)
        assertEquals("Corolla", vm.state.value.model)
        assertEquals(VehicleSize.MEDIUM, vm.state.value.sizeCategory)
        assertEquals("v-edit", vm.state.value.editingVehicleId)
    }

    @Test
    fun `should_not_mark_vehicle_registered_when_editing_existing_vehicle`() = runTest {
        val v = vehicle("v-edit")
        vehicleRepo.saveVehicle(v)
        vm.handleIntent(VehicleRegistrationIntent.LoadVehicle("v-edit"))
        vm.handleIntent(VehicleRegistrationIntent.Save)

        assertFalse(prefs.hasVehicleRegistered)
    }

    // ── NavigateBack ──────────────────────────────────────────────────────────

    @Test
    fun `should_emit_NavigateBack_on_NavigateBack`() = runTest {
        vm.effect.test {
            vm.handleIntent(VehicleRegistrationIntent.NavigateBack)
            assertIs<VehicleRegistrationEffect.NavigateBack>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
