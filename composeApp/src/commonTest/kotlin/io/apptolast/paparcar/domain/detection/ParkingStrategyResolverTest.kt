package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.fakes.FakeBluetoothScanner
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParkingStrategyResolverTest {

    // ── Coordinator cases ─────────────────────────────────────────────────────

    @Test
    fun `resolves to coordinator when no default vehicle is registered`() = runTest {
        val resolver = buildResolver(defaultVehicle = null, btEnabled = true)
        assertEquals(ParkingStrategy.COORDINATOR, resolver.resolve())
        assertTrue(resolver.shouldUseCoordinator())
    }

    @Test
    fun `resolves to coordinator when vehicle has no BT device configured`() = runTest {
        val vehicle = vehicleWith(bluetoothDeviceId = null)
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = true)
        assertEquals(ParkingStrategy.COORDINATOR, resolver.resolve())
    }

    @Test
    fun `resolves to coordinator when BT is disabled even if vehicle has BT config`() = runTest {
        val vehicle = vehicleWith(bluetoothDeviceId = "AA:BB:CC:DD:EE:FF")
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = false)
        assertEquals(ParkingStrategy.COORDINATOR, resolver.resolve())
    }

    // ── BT strategy case ──────────────────────────────────────────────────────

    @Test
    fun `resolves to BT strategy when vehicle has BT config and BT is enabled`() = runTest {
        val vehicle = vehicleWith(bluetoothDeviceId = "AA:BB:CC:DD:EE:FF")
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = true)
        assertEquals(ParkingStrategy.BLUETOOTH, resolver.resolve())
        assertFalse(resolver.shouldUseCoordinator())
    }

    // ── NONE — vehicle types that never park ──────────────────────────────────

    @Test
    fun `resolves to NONE for SCOOTER even with BT and full config`() = runTest {
        val vehicle = vehicleWith(
            bluetoothDeviceId = "AA:BB:CC:DD:EE:FF",
            type = VehicleType.SCOOTER,
        )
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = true)
        assertEquals(ParkingStrategy.NONE, resolver.resolve())
        assertFalse(resolver.shouldUseCoordinator())
    }

    @Test
    fun `resolves to NONE for BIKE even without BT`() = runTest {
        val vehicle = vehicleWith(
            bluetoothDeviceId = null,
            type = VehicleType.BIKE,
        )
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = true)
        assertEquals(ParkingStrategy.NONE, resolver.resolve())
    }

    @Test
    fun `MOTORCYCLE still resolves to coordinator (parks like a car)`() = runTest {
        val vehicle = vehicleWith(
            bluetoothDeviceId = null,
            type = VehicleType.MOTORCYCLE,
        )
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = false)
        assertEquals(ParkingStrategy.COORDINATOR, resolver.resolve())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun vehicleWith(
        bluetoothDeviceId: String?,
        type: VehicleType = VehicleType.CAR,
    ) = Vehicle(
        id = "v-1",
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM,
        vehicleType = type,
        bluetoothDeviceId = bluetoothDeviceId,
    )

    private fun buildResolver(defaultVehicle: Vehicle?, btEnabled: Boolean) =
        ParkingStrategyResolver(
            vehicleRepository = FakeVehicleRepository(defaultVehicle = defaultVehicle),
            bluetoothScanner = FakeBluetoothScanner(bluetoothEnabled = btEnabled),
        )
}
