package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.first

/**
 * Which automatic-detection pipeline (if any) should own the current driving session.
 *
 * Resolution table:
 * | Default vehicle             | bluetoothDeviceId | BT enabled | Resolved   |
 * |-----------------------------|-------------------|------------|------------|
 * | type ∈ {SCOOTER, BIKE}      | (any)             | (any)      | NONE       |
 * | type ∈ {CAR, MOTORCYCLE}    | set               | true       | BLUETOOTH  |
 * | type ∈ {CAR, MOTORCYCLE}    | null OR BT off    | —          | COORDINATOR|
 * | no default vehicle          | —                 | —          | COORDINATOR|
 *
 * Both BLUETOOTH and COORDINATOR converge on [ConfirmParkingUseCase]. NONE means
 * we skip parking detection entirely — scooters and bikes are dismounted on the
 * sidewalk and never liberate a parking spot. [BUG-SCOOTER-001]
 */
enum class ParkingStrategy {
    /** No detection — vehicle type doesn't occupy parking spots. */
    NONE,
    /** Deterministic BT-disconnect strategy. */
    BLUETOOTH,
    /** Probabilistic Activity Recognition + GPS strategy. */
    COORDINATOR,
}

class ParkingStrategyResolver(
    private val vehicleRepository: VehicleRepository,
    private val bluetoothScanner: BluetoothScanner,
) {
    /**
     * Resolves the strategy for the user's default vehicle. Reads BT state at call
     * time so toggling Bluetooth between sessions flips ownership cleanly.
     */
    suspend fun resolve(): ParkingStrategy {
        val vehicle = vehicleRepository.observeDefaultVehicle().first()
        if (vehicle != null && vehicle.vehicleType in NON_PARKING_TYPES) {
            return ParkingStrategy.NONE
        }
        val hasBtConfig = vehicle?.bluetoothDeviceId != null
        return if (hasBtConfig && bluetoothScanner.isBluetoothEnabled()) {
            ParkingStrategy.BLUETOOTH
        } else {
            ParkingStrategy.COORDINATOR
        }
    }

    /**
     * Backwards-compatible boolean facade. Returns true only when the Coordinator
     * should run — false covers BOTH the BT strategy and the NONE case (caller can
     * call [resolve] directly to tell them apart for diagnostics).
     */
    suspend fun shouldUseCoordinator(): Boolean = resolve() == ParkingStrategy.COORDINATOR

    private companion object {
        val NON_PARKING_TYPES = setOf(VehicleType.SCOOTER, VehicleType.BIKE)
    }
}
