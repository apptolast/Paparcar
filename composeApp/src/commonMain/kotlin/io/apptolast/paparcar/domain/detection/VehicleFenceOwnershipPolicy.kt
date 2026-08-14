package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.ParkingReleaseReason

/**
 * [VEH-ACTIVE-FENCE-001 piece 1] Pure decisions for "only the active vehicle owns an OS geofence".
 *
 * The phone can't physically tell which non-Bluetooth car you took — two fences in the same garage
 * are concentric, so distance is noise. The only identity signals are the paired Bluetooth MAC and
 * the user's declaration, and the ACTIVE vehicle IS that declaration. So only the active vehicle
 * (or a Bluetooth-paired one, which brings its own identity) gets an OS geofence; an inactive
 * vehicle's session keeps its pin, TTL and safety-net but registers NO fence — killing the
 * spurious-FGS noise at the source instead of filtering it after the wakeup.
 *
 * Pure and unit-tested here. The geofence I/O (createGeofence / removeGeofence) is wired by the
 * confirm / active-swap / janitor / attribution paths that consult these verdicts — kept out of
 * this file so the invariant has one named, testable home. NOT yet wired (Phase 2 of the plan in
 * `docs/backlog/veh-active-fence-001-piece1-plan.md`). [feedback_systems_not_patches]
 */
object VehicleFenceOwnershipPolicy {

    /**
     * Whether a vehicle should own an OS geofence for its active session. Active vehicles own one
     * (the user declared they drive it); Bluetooth-paired vehicles own one regardless of the active
     * flag (the MAC is identity); inactive non-paired vehicles own none.
     */
    fun shouldOwnFence(vehicleIsActive: Boolean, isBluetoothPaired: Boolean): Boolean =
        vehicleIsActive || isBluetoothPaired

    /**
     * When the active vehicle changes, the fences to swap: drop the OUTGOING owner's fence and
     * register the INCOMING owner's fence. Each side contributes only if it has a parked session
     * with a geofenceId; a vehicle with no active session contributes nothing. Bluetooth-paired
     * vehicles are never swapped out (they own a fence regardless), so the caller passes them as
     * null on the outgoing side.
     */
    fun planActiveSwap(outgoing: FenceOwner?, incoming: FenceOwner?): FenceSwapPlan =
        FenceSwapPlan(
            removeGeofenceIds = listOfNotNull(outgoing?.geofenceId),
            registerSessionIds = listOfNotNull(incoming?.geofenceId),
        )

    /**
     * The vehicle a driving session belongs to: the NOMINATING fence's vehicle when the trip was
     * armed by a geofence exit (the fence that fired identifies the car), else the current active
     * vehicle. Fixes attribution planting the pin on whatever ranked active when the running trip
     * already knew its nominator. [CoordinatorParkingDetector:682]
     *
     * [DET-BT-OWNERSHIP-001] A Bluetooth-paired nominator is VETOED: a vehicle with a
     * `bluetoothDeviceId` belongs exclusively to the Bluetooth strategy — its identity is only ever
     * established by the MAC (ACL connect/disconnect), never by a fence. A geofence exit only
     * proves the PHONE left the area, not that THAT car moved (field 2026-08-11: a parked Kamiq's
     * fence nominated it for 8 Focus trips, each confirm re-fencing the Kamiq and re-arming the
     * chain). Arming is untouched — any fence may still wake the coordinator; only the ATTRIBUTION
     * falls back to the active vehicle (the coordinator IS the active vehicle's strategy). When the
     * active vehicle is itself the BT-paired nominator the fallback lands on the same id — a
     * deliberate decision: the user explicitly declared that car, and a possibly-redundant pin
     * beats a lost parking.
     */
    fun resolveSessionVehicleId(
        nominatingVehicleId: String?,
        nominatingVehicleIsBtPaired: Boolean,
        activeVehicleId: String?,
    ): String? =
        nominatingVehicleId.takeUnless { nominatingVehicleIsBtPaired } ?: activeVehicleId

    /**
     * Whether closing a parking session should be read as "I drive this car" and flip the active
     * flag. [PARK-DELETE-NO-DECLARE-001]
     *
     * Two conditions, and the vehicle is as decisive as the motive:
     *  1. It must be a DEPARTURE. Deleting a wrong record says the parking never happened, not who
     *     is driving.
     *  2. The car must NOT be Bluetooth-paired. The active flag is the identity declaration for cars
     *     that have no other one — the same asymmetry [shouldOwnFence] and [resolveSessionVehicleId]
     *     already encode. A BT-paired car's identity is its MAC, so declaring it active buys nothing
     *     and COSTS the coordinator's car its only identity signal (and its fences, via the swap):
     *     leaving in the BT Kamiq must not blind the watch on the Focus (field 2026-08-14).
     *
     * This vetoes only the INFERRED declaration. An explicit one — "make active" in Vehicles, "I'm
     * driving this car" — is the user speaking and stands for any car, BT included.
     */
    fun shouldDeclareActiveOnRelease(reason: ParkingReleaseReason, releasedVehicleIsBtPaired: Boolean): Boolean =
        reason.isDeparture && !releasedVehicleIsBtPaired
}

/** A vehicle that owns (or would own) a fence, paired with its session's geofenceId (== sessionId,
 *  null when the vehicle has no active parked session). */
data class FenceOwner(val vehicleId: String, val geofenceId: String?)

/** The fence I/O an active-vehicle swap implies: remove these registered fences, register these
 *  sessions' fences (geofenceId == sessionId, so the caller resolves each session's coordinates). */
data class FenceSwapPlan(
    val removeGeofenceIds: List<String>,
    val registerSessionIds: List<String>,
)
