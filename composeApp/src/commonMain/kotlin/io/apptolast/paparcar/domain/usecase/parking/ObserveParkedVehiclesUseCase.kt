package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkedVehicleView
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Emits the current set of active parking sessions enriched with vehicle display data.
 * Under multi-parking, the list can hold 0..N entries — one per vehicle with an active
 * session. Sessions whose [vehicleId] no longer resolves to a known vehicle are skipped
 * (e.g., user deleted the vehicle while it was parked). [MULTI-PARKING-001]
 *
 * paletteIndex is assigned by sorting vehicleIds lexicographically so the colour
 * is stable across restarts regardless of insertion order.
 */
class ObserveParkedVehiclesUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
) {
    operator fun invoke(): Flow<List<ParkedVehicleView>> =
        combine(
            userParkingRepository.observeActiveSessions(),
            vehicleRepository.observeVehicles(),
        ) { activeSessions, vehicles ->
            val sortedIds = vehicles.map { it.id }.sorted()
            activeSessions.mapNotNull { session ->
                val vehicleId = session.vehicleId ?: return@mapNotNull null
                val vehicle = vehicles.find { it.id == vehicleId } ?: return@mapNotNull null
                ParkedVehicleView(
                    sessionId = session.id,
                    vehicleId = vehicleId,
                    displayName = vehicle.displayName(),
                    location = session.location,
                    sizeCategory = session.sizeCategory,
                    paletteIndex = sortedIds.indexOf(vehicleId).coerceAtLeast(0),
                )
            }
        }
}
