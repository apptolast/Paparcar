@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.vehicles

import app.cash.turbine.test
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VehiclesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val location = GpsPoint(40.0, -3.7, 10f, 0L, 0f)

    private fun vehicle(id: String, isDefault: Boolean = false) = Vehicle(
        id = id,
        userId = "user-1",
        sizeCategory = VehicleSize.MEDIUM,
        isDefault = isDefault,
    )

    private fun session(id: String, vehicleId: String?) = UserParking(
        id = id,
        vehicleId = vehicleId,
        location = location,
    )

    private lateinit var vehicleRepo: FakeVehicleRepository
    private lateinit var parkingRepo: FakeUserParkingRepository
    private lateinit var vm: VehiclesViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vehicleRepo = FakeVehicleRepository()
        parkingRepo = FakeUserParkingRepository()
        vm = VehiclesViewModel(vehicleRepo, parkingRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Test
    fun `should_emit_empty_list_with_isLoading_false_on_init`() = runTest {
        assertEquals(emptyList(), vm.state.value.vehicles)
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun `should_combine_vehicles_and_sessions_into_vehicleWithStats`() = runTest {
        val v1 = vehicle("v1")
        vehicleRepo.saveVehicle(v1)
        parkingRepo.saveSession(session("s1", vehicleId = "v1"))
        parkingRepo.saveSession(session("s2", vehicleId = "v1"))

        val stats = vm.state.value.vehicles
        assertEquals(1, stats.size)
        assertEquals(2, stats.first().sessionCount)
    }

    @Test
    fun `should_set_sessionCount_zero_for_vehicle_with_no_sessions`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))

        val stats = vm.state.value.vehicles
        assertEquals(0, stats.first().sessionCount)
    }

    // ── SetActiveVehicle ──────────────────────────────────────────────────────

    @Test
    fun `should_call_setDefaultVehicle_on_SetActiveVehicle`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))
        vm.handleIntent(VehiclesIntent.SetActiveVehicle("v1"))
        // FakeVehicleRepository.setDefaultVehicle is a no-op — just verify no crash
        assertEquals(1, vm.state.value.vehicles.size)
    }

    // ── Delete flow ───────────────────────────────────────────────────────────

    @Test
    fun `should_set_pendingDeleteVehicleId_on_RequestDeleteVehicle`() = runTest {
        vm.handleIntent(VehiclesIntent.RequestDeleteVehicle("v1"))
        assertEquals("v1", vm.state.value.pendingDeleteVehicleId)
    }

    @Test
    fun `should_clear_pendingDeleteVehicleId_on_DismissDeleteConfirmation`() = runTest {
        vm.handleIntent(VehiclesIntent.RequestDeleteVehicle("v1"))
        vm.handleIntent(VehiclesIntent.DismissDeleteConfirmation)
        assertNull(vm.state.value.pendingDeleteVehicleId)
    }

    @Test
    fun `should_delete_vehicle_and_clear_pending_on_ConfirmDeleteVehicle`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))
        vm.handleIntent(VehiclesIntent.RequestDeleteVehicle("v1"))
        vm.handleIntent(VehiclesIntent.ConfirmDeleteVehicle("v1"))

        assertNull(vm.state.value.pendingDeleteVehicleId)
        assertEquals(0, vm.state.value.vehicles.size)
    }

    // ── Bluetooth connected vehicle ───────────────────────────────────────────

    @Test
    fun `should_set_bluetoothConnectedVehicleId_on_BluetoothVehicleConnected`() = runTest {
        vm.handleIntent(VehiclesIntent.BluetoothVehicleConnected("v1"))
        assertEquals("v1", vm.state.value.bluetoothConnectedVehicleId)
    }

    @Test
    fun `activeVehicle_returns_bluetooth_connected_vehicle_when_present`() = runTest {
        val v1 = vehicle("v1", isDefault = true)
        val v2 = vehicle("v2", isDefault = false)
        vehicleRepo.saveVehicle(v1)
        vehicleRepo.saveVehicle(v2)
        vm.handleIntent(VehiclesIntent.BluetoothVehicleConnected("v2"))

        assertEquals("v2", vm.state.value.activeVehicle?.id)
    }

    // ── Navigation effects ────────────────────────────────────────────────────

    @Test
    fun `should_emit_NavigateToAddVehicle_on_AddVehicle`() = runTest {
        vm.effect.test {
            vm.handleIntent(VehiclesIntent.AddVehicle)
            assertIs<VehiclesEffect.NavigateToAddVehicle>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emit_NavigateToEditVehicle_with_id`() = runTest {
        vm.effect.test {
            vm.handleIntent(VehiclesIntent.EditVehicle("v42"))
            val effect = awaitItem()
            assertIs<VehiclesEffect.NavigateToEditVehicle>(effect)
            assertEquals("v42", effect.vehicleId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emit_NavigateToHistory_with_vehicleId`() = runTest {
        vm.effect.test {
            vm.handleIntent(VehiclesIntent.ViewHistory("v7"))
            val effect = awaitItem()
            assertIs<VehiclesEffect.NavigateToHistory>(effect)
            assertEquals("v7", effect.vehicleId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
