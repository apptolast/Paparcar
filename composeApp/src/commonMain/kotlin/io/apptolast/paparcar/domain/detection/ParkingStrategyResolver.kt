package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.first

/**
 * Decides which parking-detection strategy should own a driving session.
 *
 * Resolution logic (per architecture spec):
 * - Default vehicle has a paired BT device ID **AND** BT is enabled →
 *   [BluetoothParkingDetector] is the primary owner (deterministic BT strategy).
 * - Otherwise → [ParkingDetectionCoordinator] is the owner (probabilistic AR + GPS strategy).
 *
 * Both strategies converge on [ConfirmParkingUseCase] → Room + Firestore + geofence + enrichment.
 * Running both in parallel for the same session would produce a double confirmation.
 */
class ParkingStrategyResolver(
    private val vehicleRepository: VehicleRepository,
    private val bluetoothScanner: BluetoothScanner,
) {
    /**
     * Returns `true` when the probabilistic [ParkingDetectionCoordinator] should handle
     * the session. Returns `false` when [BluetoothParkingDetector] has primary ownership,
     * i.e. the default vehicle has a BT device paired **and** BT is currently enabled.
     */
    suspend fun shouldUseCoordinator(): Boolean {
        val vehicle = vehicleRepository.observeDefaultVehicle().first()
        val hasBtConfig = vehicle?.bluetoothDeviceId != null
        return !hasBtConfig || !bluetoothScanner.isBluetoothEnabled()
    }
}
