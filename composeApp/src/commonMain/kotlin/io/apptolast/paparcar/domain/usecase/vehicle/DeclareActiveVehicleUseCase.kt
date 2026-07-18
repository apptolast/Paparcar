package io.apptolast.paparcar.domain.usecase.vehicle

import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.first

/**
 * [VEH-ACTIVE-FENCE-001] The single "declare this the vehicle I drive" path. Setting a vehicle
 * active IS the user's declaration of identity, so it must also swap the OS geofences to the new
 * owner — otherwise the newly-active parked car has no fence (its confirm skipped registration by
 * design, 2a) and the outgoing car's fence lingers as spurious-FGS noise.
 *
 * Idempotent: a no-op when the vehicle is already active. Callers (set-active in Vehicles, "I'm
 * driving", release-of-inactive) go through here instead of calling [VehicleRepository.setActiveVehicle]
 * directly, so the swap can never be forgotten at a call site.
 */
class DeclareActiveVehicleUseCase(
    private val vehicleRepository: VehicleRepository,
    private val swapFences: SwapActiveVehicleFencesUseCase,
) {
    suspend operator fun invoke(vehicleId: String): Result<Unit> = runCatching {
        val outgoing = vehicleRepository.observeActiveVehicle().first()?.id
        if (outgoing == vehicleId) return@runCatching // already the active car — nothing to declare
        vehicleRepository.setActiveVehicle(vehicleId).getOrThrow()
        // Best-effort inside: a failed geofence op is logged and left to the janitor, never fails the
        // declaration (the active flag is already flipped and is the source of truth).
        swapFences(outgoingVehicleId = outgoing, incomingVehicleId = vehicleId)
    }
}
