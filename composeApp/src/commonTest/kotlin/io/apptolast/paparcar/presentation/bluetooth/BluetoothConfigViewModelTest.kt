@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.bluetooth

import app.cash.turbine.test
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import io.apptolast.paparcar.fakes.FakeBluetoothScanner
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BluetoothConfigViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vehicleId = "v-bt-1"
    private val btDevice = BluetoothDeviceInfo(address = "AA:BB:CC:DD:EE:FF", name = "Car BT")

    private fun vehicle(btDeviceId: String? = null) = Vehicle(
        id = vehicleId,
        userId = "user-1",
        brand = "Toyota",
        model = "Yaris",
        sizeCategory = VehicleSize.SMALL,
        bluetoothDeviceId = btDeviceId,
        isDefault = true,
    )

    private lateinit var vehicleRepo: FakeVehicleRepository
    private lateinit var scanner: FakeBluetoothScanner
    private lateinit var vm: BluetoothConfigViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scanner = FakeBluetoothScanner(pairedDevices = listOf(btDevice))
        vehicleRepo = FakeVehicleRepository(defaultVehicle = vehicle())
        vm = BluetoothConfigViewModel(vehicleId, scanner, vehicleRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Test
    fun `should_load_vehicle_name_and_bonded_devices_on_init`() = runTest {
        assertEquals("Toyota Yaris", vm.state.value.vehicleName)
        assertEquals(listOf(btDevice), vm.state.value.bondedDevices)
    }

    @Test
    fun `should_set_isLoading_false_after_init`() = runTest {
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `should_set_currentDeviceAddress_from_vehicle_on_init`() = runTest {
        val repoWithBt = FakeVehicleRepository(defaultVehicle = vehicle(btDeviceId = btDevice.address))
        val vmWithBt = BluetoothConfigViewModel(vehicleId, scanner, repoWithBt)
        assertEquals(btDevice.address, vmWithBt.state.value.currentDeviceAddress)
    }

    @Test
    fun `should_use_vehicleId_as_name_when_brand_and_model_are_null`() = runTest {
        val noNameVehicle = Vehicle(
            id = vehicleId,
            userId = "user-1",
            sizeCategory = VehicleSize.MEDIUM,
        )
        val repo = FakeVehicleRepository(defaultVehicle = noNameVehicle)
        val vmNoName = BluetoothConfigViewModel(vehicleId, scanner, repo)
        assertEquals(vehicleId, vmNoName.state.value.vehicleName)
    }

    // ── SelectDevice ──────────────────────────────────────────────────────────

    @Test
    fun `should_update_selectedAddress_on_SelectDevice`() = runTest {
        vm.handleIntent(BluetoothConfigIntent.SelectDevice(btDevice.address))
        assertEquals(btDevice.address, vm.state.value.selectedAddress)
    }

    @Test
    fun `should_allow_clearing_selected_device`() = runTest {
        vm.handleIntent(BluetoothConfigIntent.SelectDevice(btDevice.address))
        vm.handleIntent(BluetoothConfigIntent.SelectDevice(null))
        assertNull(vm.state.value.selectedAddress)
    }

    @Test
    fun `hasChanges_true_when_selected_differs_from_current`() = runTest {
        vm.handleIntent(BluetoothConfigIntent.SelectDevice(btDevice.address))
        assertTrue(vm.state.value.hasChanges)
    }

    @Test
    fun `hasChanges_false_when_selected_equals_current`() = runTest {
        val repoWithBt = FakeVehicleRepository(defaultVehicle = vehicle(btDeviceId = btDevice.address))
        val vmWithBt = BluetoothConfigViewModel(vehicleId, scanner, repoWithBt)
        vmWithBt.handleIntent(BluetoothConfigIntent.SelectDevice(btDevice.address))
        assertFalse(vmWithBt.state.value.hasChanges)
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @Test
    fun `should_emit_SavedSuccessfully_on_save`() = runTest {
        vm.handleIntent(BluetoothConfigIntent.SelectDevice(btDevice.address))

        vm.effect.test {
            vm.handleIntent(BluetoothConfigIntent.Save)
            assertIs<BluetoothConfigEffect.SavedSuccessfully>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_update_currentDeviceAddress_after_save`() = runTest {
        vm.handleIntent(BluetoothConfigIntent.SelectDevice(btDevice.address))
        vm.handleIntent(BluetoothConfigIntent.Save)
        assertEquals(btDevice.address, vm.state.value.currentDeviceAddress)
    }

    @Test
    fun `hasChanges_false_after_successful_save`() = runTest {
        vm.handleIntent(BluetoothConfigIntent.SelectDevice(btDevice.address))
        vm.handleIntent(BluetoothConfigIntent.Save)
        assertFalse(vm.state.value.hasChanges)
    }

    // ── NavigateBack ──────────────────────────────────────────────────────────

    @Test
    fun `should_emit_NavigateBack_on_NavigateBack`() = runTest {
        vm.effect.test {
            vm.handleIntent(BluetoothConfigIntent.NavigateBack)
            assertIs<BluetoothConfigEffect.NavigateBack>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
