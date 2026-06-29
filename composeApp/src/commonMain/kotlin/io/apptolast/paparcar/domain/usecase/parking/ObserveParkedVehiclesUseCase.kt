package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkedVehicleSummary
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
 * [stableRank] is assigned by sorting vehicleIds lexicographically so the accent
 * colour is stable across restarts regardless of insertion order.
 */
class ObserveParkedVehiclesUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
) {
    operator fun invoke(): Flow<List<ParkedVehicleSummary>> =
        combine(
            userParkingRepository.observeActiveSessions(),
            vehicleRepository.observeVehicles(),
        ) { activeSessions, vehicles ->
            val sortedIds = vehicles.map { it.id }.sorted()
            activeSessions.mapNotNull { session ->
                val vehicleId = session.vehicleId ?: return@mapNotNull null
                val vehicle = vehicles.find { it.id == vehicleId } ?: return@mapNotNull null
                ParkedVehicleSummary(
                    sessionId = session.id,
                    vehicleId = vehicleId,
                    displayName = vehicle.displayName(),
                    location = session.location,
                    sizeCategory = session.sizeCategory ?: vehicle.sizeCategory,
                    carbodyType = session.carbodyType ?: vehicle.carbodyType,
                    stableRank = sortedIds.indexOf(vehicleId).coerceAtLeast(0),
                    privateZoneId = session.privateZoneId,
                    licensePlate = vehicle.licensePlate,
                    isBluetoothPaired = vehicle.bluetoothDeviceId != null,
                    color = vehicle.color,
                    isActive = vehicle.isActive,
                )
            }
        }
}
