package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkedVehicleView
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Emits the current set of active parking sessions enriched with vehicle display data.
 *
 * v1: only one session can be active at a time, so the list has 0 or 1 element.
 * The multi-vehicle path is ready for when concurrent sessions are supported.
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
            userParkingRepository.observeActiveSession(),
            vehicleRepository.observeVehicles(),
        ) { activeSession, vehicles ->
            if (activeSession == null) return@combine emptyList()

            val vehicleId = activeSession.vehicleId ?: return@combine emptyList()
            val vehicle = vehicles.find { it.id == vehicleId } ?: return@combine emptyList()

            val sortedIds = vehicles.map { it.id }.sorted()
            val paletteIndex = sortedIds.indexOf(vehicleId).coerceAtLeast(0)

            listOf(
                ParkedVehicleView(
                    sessionId = activeSession.id,
                    vehicleId = vehicleId,
                    displayName = vehicle.displayName(),
                    location = activeSession.location,
                    sizeCategory = activeSession.sizeCategory,
                    paletteIndex = paletteIndex,
                )
            )
        }
}
