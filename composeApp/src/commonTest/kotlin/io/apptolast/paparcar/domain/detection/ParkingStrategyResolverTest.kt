package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.fakes.FakeBluetoothScanner
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParkingStrategyResolverTest {

    // ── Coordinator cases ─────────────────────────────────────────────────────

    @Test
    fun `should use coordinator when no default vehicle is registered`() = runTest {
        val resolver = buildResolver(defaultVehicle = null, btEnabled = true)
        assertTrue(resolver.shouldUseCoordinator())
    }

    @Test
    fun `should use coordinator when vehicle has no BT device configured`() = runTest {
        val vehicle = vehicleWithBt(bluetoothDeviceId = null)
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = true)
        assertTrue(resolver.shouldUseCoordinator())
    }

    @Test
    fun `should use coordinator when BT is disabled even if vehicle has BT config`() = runTest {
        val vehicle = vehicleWithBt(bluetoothDeviceId = "AA:BB:CC:DD:EE:FF")
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = false)
        assertTrue(resolver.shouldUseCoordinator())
    }

    // ── BT strategy case ──────────────────────────────────────────────────────

    @Test
    fun `should use BT strategy when vehicle has BT config and BT is enabled`() = runTest {
        val vehicle = vehicleWithBt(bluetoothDeviceId = "AA:BB:CC:DD:EE:FF")
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = true)
        assertFalse(resolver.shouldUseCoordinator())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun vehicleWithBt(bluetoothDeviceId: String?) = Vehicle(
        id = "v-1",
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM,
        bluetoothDeviceId = bluetoothDeviceId,
    )

    private fun buildResolver(defaultVehicle: Vehicle?, btEnabled: Boolean) =
        ParkingStrategyResolver(
            vehicleRepository = FakeVehicleRepository(defaultVehicle = defaultVehicle),
            bluetoothScanner = FakeBluetoothScanner(bluetoothEnabled = btEnabled),
        )
}
