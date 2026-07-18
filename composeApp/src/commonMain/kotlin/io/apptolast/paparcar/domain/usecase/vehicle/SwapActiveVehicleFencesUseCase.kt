@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.vehicle

import io.apptolast.paparcar.domain.detection.FenceOwner
import io.apptolast.paparcar.domain.detection.VehicleFenceOwnershipPolicy
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.first

/**
 * [VEH-ACTIVE-FENCE-001 · 2c] Reconciles OS geofences when the active vehicle changes: drop the
 * outgoing car's fence and register the incoming car's — each only if it has a parked session, so
 * the "only the active vehicle owns a fence" invariant holds across the swap.
 *
 * A Bluetooth-paired outgoing vehicle is NEVER swapped out — the MAC is identity, so it owns a fence
 * regardless of the active flag. Best-effort: a failed geofence op is logged, not fatal — the
 * janitor's periodic sweep repairs any drift. The confirmed park's location/size come from the
 * incoming session itself (geofenceId == sessionId). Consults the pure [VehicleFenceOwnershipPolicy].
 */
class SwapActiveVehicleFencesUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
    private val geofenceService: GeofenceManager,
    private val config: ParkingDetectionConfig,
) {
    suspend operator fun invoke(outgoingVehicleId: String?, incomingVehicleId: String): Result<Unit> = runCatching {
        val incomingSession = userParkingRepository.getActiveSessionByVehicle(incomingVehicleId)
        val outgoingSession = outgoingVehicleId
            ?.takeIf { it != incomingVehicleId }
            ?.let { userParkingRepository.getActiveSessionByVehicle(it) }

        // A BT-paired outgoing keeps its fence (identity ≠ active flag) — never swap it out.
        val outgoingIsBt = outgoingVehicleId != null &&
            vehicleRepository.observeVehicles().first()
                .firstOrNull { it.id == outgoingVehicleId }?.bluetoothDeviceId != null

        val outgoingOwner = outgoingSession
            ?.takeUnless { outgoingIsBt }
            ?.let { FenceOwner(vehicleId = outgoingVehicleId!!, geofenceId = it.geofenceId) }
        val incomingOwner = incomingSession
            ?.let { FenceOwner(vehicleId = incomingVehicleId, geofenceId = it.geofenceId) }

        val plan = VehicleFenceOwnershipPolicy.planActiveSwap(outgoing = outgoingOwner, incoming = incomingOwner)

        plan.removeGeofenceIds.forEach { id ->
            geofenceService.removeGeofence(id)
                .onFailure { e -> PaparcarLogger.w(TAG, "swap: removeGeofence($id) failed — janitor will repair", e) }
        }
        // registerSessionIds is [incoming.geofenceId] when the incoming car is parked; register it
        // from the session we already resolved (no second lookup).
        if (incomingSession != null && plan.registerSessionIds.isNotEmpty()) {
            geofenceService.createGeofence(
                geofenceId = incomingSession.geofenceId ?: incomingSession.id,
                latitude = incomingSession.location.latitude,
                longitude = incomingSession.location.longitude,
                radiusMeters = config.geofenceRadiusFor(incomingSession.sizeCategory, incomingSession.location.accuracy),
            ).onFailure { e -> PaparcarLogger.w(TAG, "swap: createGeofence for incoming failed — janitor will repair", e) }
        }
    }

    private companion object {
        const val TAG = "SwapActiveVehicleFences"
    }
}
