package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.VehicleType
import com.rndeveloper.paparcar.fakes.FakeBluetoothScanner
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
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
    fun `resolves to BT strategy when vehicle has BT config with BT enabled AND connected`() = runTest {
        // [DET-BT-CONNECTED-NOT-PAIRED-001] Connected to the paired car → BLUETOOTH owns detection.
        val vehicle = vehicleWith(id = "v-1", bluetoothDeviceId = "AA:BB:CC:DD:EE:FF")
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = true, connectedVehicleIds = setOf("v-1"))
        assertEquals(ParkingStrategy.BLUETOOTH, resolver.resolve())
        assertFalse(resolver.shouldUseCoordinator())
    }

    // ── DET-BT-CONNECTED-NOT-PAIRED-001 — paired but NOT connected → Coordinator ──────────────

    @Test
    fun `resolves to coordinator when BT car is paired and enabled but NOT connected`() = runTest {
        // The core fix: a BT car sitting paired-at-home must NOT hijack the strategy. You're driving
        // it right now only if the phone is CONNECTED to it — otherwise the Coordinator runs.
        val vehicle = vehicleWith(id = "v-1", bluetoothDeviceId = "AA:BB:CC:DD:EE:FF")
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = true, connectedVehicleIds = emptySet())
        assertEquals(ParkingStrategy.COORDINATOR, resolver.resolve())
        assertTrue(resolver.shouldUseCoordinator())
    }

    @Test
    fun `resolves to coordinator for active non-BT car while BT car is paired but not connected`() = runTest {
        // The real field scenario (08-08): active Focus (no BT) + Kamiq paired (BT) but not connected
        // → drive the Focus → COORDINATOR must own it (was wrongly BLUETOOTH → total miss).
        val focus = vehicleWith(id = "v-focus", bluetoothDeviceId = null, isActive = true)
        val kamiq = vehicleWith(id = "v-kamiq", bluetoothDeviceId = "AA:BB:CC:DD:EE:FF", isActive = false)
        val resolver = buildResolver(
            defaultVehicle = focus,
            extras = listOf(kamiq),
            btEnabled = true,
            connectedVehicleIds = emptySet(),
        )
        assertEquals(ParkingStrategy.COORDINATOR, resolver.resolve())
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
        val resolver = buildResolver(
            defaultVehicle = primary,
            extras = listOf(secondary),
            btEnabled = true,
            connectedVehicleIds = setOf("v-secondary"),
        )
        assertEquals(ParkingStrategy.BLUETOOTH, resolver.resolve())
    }

    @Test
    fun `resolves to BT when single vehicle has BT but is not marked active`() = runTest {
        // Post BUG-NEW-VEHICLE-DEFAULT: new vehicles no longer auto-set isActive=true.
        // A user pairing BT on a non-primary vehicle still routes through BT when connected.
        val btOnly = vehicleWith(id = "v-1", bluetoothDeviceId = "AA:BB:CC:DD:EE:FF", isActive = false)
        val resolver = buildResolver(defaultVehicle = btOnly, btEnabled = true, connectedVehicleIds = setOf("v-1"))
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
        val resolver = buildResolver(
            defaultVehicle = scooterPrimary,
            extras = listOf(carBt),
            btEnabled = true,
            connectedVehicleIds = setOf("v-car"),
        )
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
    fun `MOTORCYCLE still resolves to coordinator - parks like a car`() = runTest {
        val vehicle = vehicleWith(
            bluetoothDeviceId = null,
            type = VehicleType.MOTORCYCLE,
        )
        val resolver = buildResolver(defaultVehicle = vehicle, btEnabled = false)
        assertEquals(ParkingStrategy.COORDINATOR, resolver.resolve())
    }

    // ── coordinatorMayArm [DET-STRATEGY-GATE-001] ─────────────────────────────

    @Test
    fun `admits every trigger when coordinator owns detection`() {
        DetectionTrigger.entries.forEach { trigger ->
            assertTrue(
                coordinatorMayArm(ParkingStrategy.COORDINATOR, trigger),
                "expected $trigger admitted under COORDINATOR",
            )
        }
    }

    @Test
    fun `refuses automatic triggers when bluetooth owns detection`() {
        DetectionTrigger.entries.filter { it != DetectionTrigger.MANUAL }.forEach { trigger ->
            assertFalse(
                coordinatorMayArm(ParkingStrategy.BLUETOOTH, trigger),
                "expected $trigger refused under BLUETOOTH",
            )
        }
    }

    @Test
    fun `refuses automatic triggers when strategy is none`() {
        DetectionTrigger.entries.filter { it != DetectionTrigger.MANUAL }.forEach { trigger ->
            assertFalse(
                coordinatorMayArm(ParkingStrategy.NONE, trigger),
                "expected $trigger refused under NONE",
            )
        }
    }

    @Test
    fun `admits manual trigger under any strategy`() {
        // Explicit user intent always wins; a BT-paired park is superseded later by the
        // disconnect arbitration.
        ParkingStrategy.entries.forEach { strategy ->
            assertTrue(
                coordinatorMayArm(strategy, DetectionTrigger.MANUAL),
                "expected MANUAL admitted under $strategy",
            )
        }
    }

    @Test
    fun `refuses the safety net arrival handoff unless the coordinator owns detection`() {
        // [DET-HANDOFF-NOT-MANUAL-001] The handoff used to arm as MANUAL — an automatic trigger
        // entering through the door reserved for human intent, so it inherited the exemption this
        // gate exists to deny (field 2026-08-19 22:32: `ARM:MANUAL` with nobody touching a button).
        // It is an automatic nominator like any other: only COORDINATOR may admit it.
        assertTrue(coordinatorMayArm(ParkingStrategy.COORDINATOR, DetectionTrigger.ARRIVAL_HANDOFF))
        assertFalse(coordinatorMayArm(ParkingStrategy.BLUETOOTH, DetectionTrigger.ARRIVAL_HANDOFF))
        assertFalse(coordinatorMayArm(ParkingStrategy.NONE, DetectionTrigger.ARRIVAL_HANDOFF))
    }

    @Test
    fun `manual is the only trigger exempt from the strategy gate`() {
        // The exemption is a hole by design: keep it a hole of exactly ONE. Any new trigger that
        // needs to bypass the resolved strategy must justify it here, not inherit it by reusing a
        // label. [DET-HANDOFF-NOT-MANUAL-001]
        val exempt = DetectionTrigger.entries.filter { trigger ->
            coordinatorMayArm(ParkingStrategy.BLUETOOTH, trigger)
        }
        assertEquals(listOf(DetectionTrigger.MANUAL), exempt)
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
        connectedVehicleIds: Set<String> = emptySet(),
    ) = ParkingStrategyResolver(
        vehicleRepository = FakeVehicleRepository(defaultVehicle = defaultVehicle, extraVehicles = extras),
        bluetoothScanner = FakeBluetoothScanner(
            bluetoothEnabled = btEnabled,
            connectedVehicleIds = connectedVehicleIds,
        ),
    )
}
