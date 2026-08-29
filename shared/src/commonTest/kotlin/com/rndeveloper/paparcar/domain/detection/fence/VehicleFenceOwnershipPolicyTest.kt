package com.rndeveloper.paparcar.domain.detection.fence

import com.rndeveloper.paparcar.domain.model.ParkingReleaseReason
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
        // [VEH-ACTIVE-FENCE-001] A non-BT nominator still wins over the active vehicle.
        assertEquals(
            "veh-nominator",
            VehicleFenceOwnershipPolicy.resolveSessionVehicleId(
                nominatingVehicleId = "veh-nominator",
                nominatingVehicleIsBtPaired = false,
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
                nominatingVehicleIsBtPaired = false,
                activeVehicleId = "veh-active",
            ),
        )
    }

    @Test
    fun `attribution is null when neither a nominator nor an active vehicle exists`() {
        assertEquals(
            null,
            VehicleFenceOwnershipPolicy.resolveSessionVehicleId(
                nominatingVehicleId = null,
                nominatingVehicleIsBtPaired = false,
                activeVehicleId = null,
            ),
        )
    }

    @Test
    fun should_veto_a_bt_paired_nominator_and_attribute_to_the_active_vehicle() {
        // [DET-BT-OWNERSHIP-001] Field 2026-08-11: the parked Kamiq's fence (BT-paired, inactive)
        // nominated it for every Focus trip. A BT-paired vehicle's identity is the MAC, never a
        // fence — the coordinator must attribute to the ACTIVE vehicle instead.
        assertEquals(
            "veh-active",
            VehicleFenceOwnershipPolicy.resolveSessionVehicleId(
                nominatingVehicleId = "veh-bt-kamiq",
                nominatingVehicleIsBtPaired = true,
                activeVehicleId = "veh-active",
            ),
        )
    }

    @Test
    fun should_return_null_when_bt_paired_nominator_is_vetoed_and_no_active_vehicle_exists() {
        // [DET-BT-OWNERSHIP-001] With the BT nominator vetoed and nobody active there is no honest
        // owner — the caller aborts the session rather than guessing.
        assertEquals(
            null,
            VehicleFenceOwnershipPolicy.resolveSessionVehicleId(
                nominatingVehicleId = "veh-bt-kamiq",
                nominatingVehicleIsBtPaired = true,
                activeVehicleId = null,
            ),
        )
    }

    @Test
    fun should_keep_attribution_when_the_bt_paired_nominator_is_itself_the_active_vehicle() {
        // [DET-BT-OWNERSHIP-001] Deliberate decision: the veto falls back to the active vehicle,
        // so an active BT-paired car keeps its own attribution (explicit user declaration; a
        // possibly-redundant pin beats a lost parking).
        assertEquals(
            "veh-bt-active",
            VehicleFenceOwnershipPolicy.resolveSessionVehicleId(
                nominatingVehicleId = "veh-bt-active",
                nominatingVehicleIsBtPaired = true,
                activeVehicleId = "veh-bt-active",
            ),
        )
    }

    // ── [PARK-DELETE-NO-DECLARE-001] Releasing declares identity only for cars without one ──

    @Test
    fun should_declare_active_when_a_non_bt_car_departs() {
        // The coordinator's car has no identity but the active flag — leaving in it IS the claim.
        assertTrue(
            VehicleFenceOwnershipPolicy.shouldDeclareActiveOnRelease(
                reason = ParkingReleaseReason.DEPARTURE_PUBLISHED,
                releasedVehicleIsBtPaired = false,
            ),
        )
        assertTrue(
            VehicleFenceOwnershipPolicy.shouldDeclareActiveOnRelease(
                reason = ParkingReleaseReason.DEPARTURE_UNPUBLISHED,
                releasedVehicleIsBtPaired = false,
            ),
        )
    }

    @Test
    fun should_not_declare_active_when_the_departing_car_is_bt_paired() {
        // Field 2026-08-14: leaving in the BT Kamiq stole the active flag (and the fences) from the
        // Focus. The MAC already identifies the Kamiq; the flag is the Focus's only identity.
        assertFalse(
            VehicleFenceOwnershipPolicy.shouldDeclareActiveOnRelease(
                reason = ParkingReleaseReason.DEPARTURE_PUBLISHED,
                releasedVehicleIsBtPaired = true,
            ),
        )
    }

    @Test
    fun should_not_declare_active_when_a_record_is_deleted() {
        // Deleting a wrong record says the parking never happened — never who is driving.
        assertFalse(
            VehicleFenceOwnershipPolicy.shouldDeclareActiveOnRelease(
                reason = ParkingReleaseReason.RECORD_DELETED,
                releasedVehicleIsBtPaired = false,
            ),
        )
    }

    // ── mayNominateDetection ───────────────────────────────────────────────────
    // [DET-BT-CAR-CANNOT-NOMINATE-A-COORDINATOR-SESSION-001]

    private fun mayNominate(
        sessionVehicleId: String?,
        activeVehicleId: String?,
        sessionVehicleIsBtPaired: Boolean = false,
        isOnlyActiveSession: Boolean = true,
    ) = VehicleFenceOwnershipPolicy.mayNominateDetection(
        sessionVehicleId = sessionVehicleId,
        activeVehicleId = activeVehicleId,
        sessionVehicleIsBtPaired = sessionVehicleIsBtPaired,
        isOnlyActiveSession = isOnlyActiveSession,
    )

    @Test
    fun should_nominate_when_the_session_belongs_to_the_active_vehicle() {
        assertTrue(mayNominate(sessionVehicleId = FOCUS, activeVehicleId = FOCUS))
    }

    @Test
    fun should_not_nominate_a_bt_paired_car_session_when_the_active_car_has_none() {
        // The field case (2026-08-25): the active Focus had no parked session, so a five-day-old
        // manual pin of the BT-paired Kamiq armed a Focus trip — 6 214 m away, which then superseded
        // 23 min of measured driving and left the phone watching the Kamiq's fence all night.
        assertFalse(
            mayNominate(
                sessionVehicleId = KAMIQ,
                activeVehicleId = FOCUS,
                sessionVehicleIsBtPaired = true,
            ),
        )
    }

    @Test
    fun should_not_nominate_another_non_bt_car_session_when_the_active_car_has_none() {
        // The veto is not about Bluetooth: the phone cannot tell which non-paired car you took, so
        // ANY other car's session is a guess. "None" is the answer, and it costs a nudge at most.
        assertFalse(mayNominate(sessionVehicleId = KAMIQ, activeVehicleId = FOCUS))
    }

    @Test
    fun should_nominate_the_active_vehicle_session_even_when_it_is_bt_paired() {
        // Deliberate, and decided here rather than inherited from resolveSessionVehicleId: pairing
        // does not erase the user's declaration, and with Bluetooth OFF on the phone the coordinator
        // IS that car's strategy — vetoing it would be a silent false negative.
        assertTrue(
            mayNominate(
                sessionVehicleId = KAMIQ,
                activeVehicleId = KAMIQ,
                sessionVehicleIsBtPaired = true,
            ),
        )
    }

    @Test
    fun should_not_nominate_an_unattributed_session_when_a_vehicle_is_declared() {
        // A session whose vehicleId is null is "we don't know whose car this is" — the very guess
        // this closes, so it is not a candidate either.
        assertFalse(mayNominate(sessionVehicleId = null, activeVehicleId = FOCUS))
    }

    @Test
    fun should_nominate_the_lone_session_when_no_vehicle_is_declared() {
        // Nothing to guess among: one car, one session. Same shape as HomeTripController.
        assertTrue(mayNominate(sessionVehicleId = FOCUS, activeVehicleId = null))
    }

    @Test
    fun should_not_nominate_the_lone_session_when_no_vehicle_is_declared_and_it_is_bt_paired() {
        // With no declaration to lean on, a BT-paired car belongs to the Bluetooth strategy alone.
        assertFalse(
            mayNominate(
                sessionVehicleId = KAMIQ,
                activeVehicleId = null,
                sessionVehicleIsBtPaired = true,
            ),
        )
    }

    @Test
    fun should_not_nominate_any_session_when_no_vehicle_is_declared_and_several_are_parked() {
        assertFalse(
            mayNominate(
                sessionVehicleId = FOCUS,
                activeVehicleId = null,
                isOnlyActiveSession = false,
            ),
        )
    }

    private companion object {
        const val FOCUS = "addbe660"
        const val KAMIQ = "abf6c516"
    }
}
