@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.vehicleregistration

import app.cash.turbine.test
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleSize
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
    private lateinit var vm: VehicleRegistrationViewModel

    private fun vehicle(id: String) = Vehicle(
        id = id,
        userId = "user-1",
        brand = "Toyota",
        model = "Corolla",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        isActive = true,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vehicleRepo = FakeVehicleRepository()
        auth = FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession("user-1"))
        vm = VehicleRegistrationViewModel(vehicleRepo, auth)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Field intents ─────────────────────────────────────────────────────────

    @Test
    fun `should_updateBrand_on_SelectBrand`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("Ford"))
        assertEquals("Ford", vm.state.value.brand)
    }

    @Test
    fun `should_updateModel_on_SelectModel`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectModel("Focus"))
        assertEquals("Focus", vm.state.value.model)
    }

    @Test
    fun `should_updateSize_on_SetSize`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.VAN_LIGHT))
        assertEquals(VehicleSize.VAN_HIGH, vm.state.value.sizeCategory)
    }

    @Test
    fun `should_updateShowOnSpot_on_SetShowOnSpot`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetShowOnSpot(true))
        assertTrue(vm.state.value.showBrandModelOnSpot)
    }

    // ── Save — success ────────────────────────────────────────────────────────

    @Test
    fun `should_emit_SavedSuccessfully_on_valid_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("Seat"))
        vm.handleIntent(VehicleRegistrationIntent.SelectModel("Ibiza"))
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_SMALL))

        vm.effect.test {
            vm.handleIntent(VehicleRegistrationIntent.Save)
            assertIs<VehicleRegistrationEffect.SavedSuccessfully>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_persist_vehicle_to_repo_on_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("Seat"))
        vm.handleIntent(VehicleRegistrationIntent.SelectModel("Ibiza"))
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_SMALL))
        vm.handleIntent(VehicleRegistrationIntent.Save)

        val saved = vehicleRepo.observeActiveVehicle().first()
        assertEquals("Ibiza", saved?.model)
    }

    @Test
    fun `should_trim_blank_brand_to_null_on_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("   "))
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_MEDIUM))
        vm.handleIntent(VehicleRegistrationIntent.Save)

        assertNull(vehicleRepo.observeActiveVehicle().first()?.brand)
    }

    // ── Save — validation failure ─────────────────────────────────────────────

    @Test
    fun `should_emit_ShowError_when_size_is_null_on_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("Ford"))

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
        assertEquals(VehicleSize.MEDIUM_SUV, vm.state.value.sizeCategory)
        assertEquals("v-edit", vm.state.value.editingVehicleId)
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

    // ── VEHICLES-002: no duplicate save on rapid taps or after failure ────────

    @Test
    fun `should_call_saveVehicle_only_once_when_Save_intent_arrives_twice_in_a_row`() = runTest {
        // Hold the first save in-flight via a CompletableDeferred so the second intent
        // arrives while state.isSaving is still true and hits the guard.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        vehicleRepo.saveVehicleAwait = gate
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_SMALL))

        vm.handleIntent(VehicleRegistrationIntent.Save)  // launches coroutine, suspends on gate
        vm.handleIntent(VehicleRegistrationIntent.Save)  // state.isSaving=true → blocked by guard
        gate.complete(Unit)                              // let the first save finish

        assertEquals(1, vehicleRepo.saveVehicleCallCount)
    }

    @Test
    fun `should_reuse_same_vehicle_id_when_retrying_after_save_failure`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_SMALL))

        // First attempt fails.
        vehicleRepo.saveVehicleThrows = RuntimeException("network error")
        vm.handleIntent(VehicleRegistrationIntent.Save)

        // Second attempt succeeds. The id MUST match the first — otherwise we leak orphans
        // in Firestore (each `.set()` creates a new doc with whatever UUID is passed).
        vm.handleIntent(VehicleRegistrationIntent.Save)

        assertEquals(2, vehicleRepo.saveVehicleCallCount)
        assertEquals(
            vehicleRepo.savedVehicleIds[0],
            vehicleRepo.savedVehicleIds[1],
            "Retry must reuse the memoized pendingNewVehicleId — generating a new UUID corrupts Firestore.",
        )
    }
}
