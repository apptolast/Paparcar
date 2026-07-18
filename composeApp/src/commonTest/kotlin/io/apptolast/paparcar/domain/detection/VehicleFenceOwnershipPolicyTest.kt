package io.apptolast.paparcar.domain.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VehicleFenceOwnershipPolicyTest {

    // ── shouldOwnFence ─────────────────────────────────────────────────────────

    @Test
    fun `active vehicle owns a fence`() {
        assertTrue(VehicleFenceOwnershipPolicy.shouldOwnFence(vehicleIsActive = true, isBluetoothPaired = false))
    }

    @Test
    fun `inactive non-paired vehicle owns no fence`() {
        assertFalse(VehicleFenceOwnershipPolicy.shouldOwnFence(vehicleIsActive = false, isBluetoothPaired = false))
    }

    @Test
    fun `inactive but Bluetooth-paired vehicle still owns a fence`() {
        // The MAC is identity — a paired car is automatic regardless of the active flag. [DET-TIERS-001]
        assertTrue(VehicleFenceOwnershipPolicy.shouldOwnFence(vehicleIsActive = false, isBluetoothPaired = true))
    }

    // ── planActiveSwap ─────────────────────────────────────────────────────────

    @Test
    fun `swap drops the outgoing fence and registers the incoming one`() {
        val plan = VehicleFenceOwnershipPolicy.planActiveSwap(
            outgoing = FenceOwner(vehicleId = "veh-A", geofenceId = "sess-A"),
            incoming = FenceOwner(vehicleId = "veh-B", geofenceId = "sess-B"),
        )
        assertEquals(listOf("sess-A"), plan.removeGeofenceIds)
        assertEquals(listOf("sess-B"), plan.registerSessionIds)
    }

    @Test
    fun `swap registers only when the incoming vehicle has a parked session`() {
        val plan = VehicleFenceOwnershipPolicy.planActiveSwap(
            outgoing = FenceOwner(vehicleId = "veh-A", geofenceId = "sess-A"),
            incoming = FenceOwner(vehicleId = "veh-B", geofenceId = null),
        )
        assertEquals(listOf("sess-A"), plan.removeGeofenceIds)
        assertTrue(plan.registerSessionIds.isEmpty())
    }

    @Test
    fun `swap removes nothing when the outgoing vehicle owned no fence`() {
        val plan = VehicleFenceOwnershipPolicy.planActiveSwap(
            outgoing = null,
            incoming = FenceOwner(vehicleId = "veh-B", geofenceId = "sess-B"),
        )
        assertTrue(plan.removeGeofenceIds.isEmpty())
        assertEquals(listOf("sess-B"), plan.registerSessionIds)
    }

    @Test
    fun `swap between two carless vehicles is a no-op`() {
        val plan = VehicleFenceOwnershipPolicy.planActiveSwap(outgoing = null, incoming = null)
        assertTrue(plan.removeGeofenceIds.isEmpty())
        assertTrue(plan.registerSessionIds.isEmpty())
    }

    // ── resolveSessionVehicleId ────────────────────────────────────────────────

    @Test
    fun `attribution prefers the nominating fence's vehicle`() {
        assertEquals(
            "veh-nominator",
            VehicleFenceOwnershipPolicy.resolveSessionVehicleId(
                nominatingVehicleId = "veh-nominator",
                activeVehicleId = "veh-active",
            ),
        )
    }

    @Test
    fun `attribution falls back to the active vehicle when there is no nominator`() {
        assertEquals(
            "veh-active",
            VehicleFenceOwnershipPolicy.resolveSessionVehicleId(
                nominatingVehicleId = null,
                activeVehicleId = "veh-active",
            ),
        )
    }

    @Test
    fun `attribution is null when neither a nominator nor an active vehicle exists`() {
        assertEquals(
            null,
            VehicleFenceOwnershipPolicy.resolveSessionVehicleId(nominatingVehicleId = null, activeVehicleId = null),
        )
    }
}
