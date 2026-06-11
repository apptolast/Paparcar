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

    // ── ARCH-MONITORING-002 — BT supersedes regardless of isActive ────────────

    @Test
    fun `resolves to BT when secondary vehicle has BT even if primary does not`() = runTest {
        // [ARCH-MONITORING-002] Primary = non-BT car (isActive). Secondary = BT-paired
        // car (not isActive). The BT receiver fires for the secondary on disconnect, so
        // the resolver must report BLUETOOTH to suppress the Coordinator and prevent
        // double-confirm when the user actually drives the secondary.
        val primary = vehicleWith(id = "v-primary", bluetoothDeviceId = null, isActive = true)
        val secondary = vehicleWith(id = "v-secondary", bluetoothDeviceId = "AA:BB:CC:DD:EE:FF", isActive = false)
        val resolver = buildResolver(defaultVehicle = primary, extras = listOf(secondary), btEnabled = true)
        assertEquals(ParkingStrategy.BLUETOOTH, resolver.resolve())
    }

    @Test
    fun `resolves to BT when single vehicle has BT but is not marked active`() = runTest {
        // Post BUG-NEW-VEHICLE-DEFAULT: new vehicles no longer auto-set isActive=true.
        // A user pairing BT on a non-primary vehicle still routes through BT.
        val btOnly = vehicleWith(bluetoothDeviceId = "AA:BB:CC:DD:EE:FF", isActive = false)
        val resolver = buildResolver(defaultVehicle = btOnly, btEnabled = true)
        assertEquals(ParkingStrategy.BLUETOOTH, resolver.resolve())
    }

    @Test
    fun `BT wins over scooter-primary when fleet has a BT-paired car`() = runTest {
        // Primary scooter would otherwise resolve to NONE. But a BT-paired car in the
        // fleet needs its own deterministic detection — BT takes precedence.
        val scooterPrimary = vehicleWith(
            id = "v-scooter",
            bluetoothDeviceId = null,
            type = VehicleType.SCOOTER,
            isActive = true,
        )
        val carBt = vehicleWith(
            id = "v-car",
            bluetoothDeviceId = "AA:BB:CC:DD:EE:FF",
            type = VehicleType.CAR,
            isActive = false,
        )
        val resolver = buildResolver(defaultVehicle = scooterPrimary, extras = listOf(carBt), btEnabled = true)
        assertEquals(ParkingStrategy.BLUETOOTH, resolver.resolve())
    }

    // ── NONE — vehicle types that never park ──────────────────────────────────

    @Test
    fun `resolves to NONE for SCOOTER even with BT and full config`() = runTest {
        // Scooter with a BT pairing is still NONE: scooters don't park. BT pairing on a
        // non-parking type is ignored (isBtPairedAndParks filters it out).
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
        id: String = "v-1",
        bluetoothDeviceId: String?,
        type: VehicleType = VehicleType.CAR,
        isActive: Boolean = true,
    ) = Vehicle(
        id = id,
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        vehicleType = type,
        bluetoothDeviceId = bluetoothDeviceId,
        isActive = isActive,
    )

    private fun buildResolver(
        defaultVehicle: Vehicle?,
        extras: List<Vehicle> = emptyList(),
        btEnabled: Boolean,
    ) = ParkingStrategyResolver(
        vehicleRepository = FakeVehicleRepository(defaultVehicle = defaultVehicle, extraVehicles = extras),
        bluetoothScanner = FakeBluetoothScanner(bluetoothEnabled = btEnabled),
    )
}
