package io.apptolast.paparcar.domain.detection

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
     */
    fun resolveSessionVehicleId(nominatingVehicleId: String?, activeVehicleId: String?): String? =
        nominatingVehicleId ?: activeVehicleId
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
