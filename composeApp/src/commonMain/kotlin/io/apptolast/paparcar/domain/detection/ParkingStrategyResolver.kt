package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.first

/**
 * Which automatic-detection pipeline (if any) should own the current driving session.
 *
 * Resolution honours the **BT-supersedes** invariant: any vehicle paired with a BT
 * device routes through the deterministic BT-disconnect pipeline, regardless of
 * which vehicle the user marked as primary (`isActive`). This decouples "primary
 * vehicle for identity fallbacks" from "vehicle the Coordinator monitors".
 *
 * Resolution order (first match wins):
 * | Condition                                                      | Resolved   |
 * |----------------------------------------------------------------|------------|
 * | Primary vehicle type ∈ {SCOOTER, BIKE}                         | NONE       |
 * | Any vehicle has bluetoothDeviceId AND BT enabled               | BLUETOOTH  |
 * | Primary vehicle exists (and is not SCOOTER/BIKE)               | COORDINATOR|
 * | No primary vehicle                                              | COORDINATOR|
 *
 * Both BLUETOOTH and COORDINATOR converge on [ConfirmParkingUseCase]. NONE means
 * we skip parking detection entirely — scooters and bikes are dismounted on the
 * sidewalk and never liberate a parking spot. [BUG-SCOOTER-001]
 *
 * Note: when BLUETOOTH wins, Coordinator is suppressed even if the primary vehicle
 * has no BT pairing. Rationale: the BT receiver already covers the BT-paired
 * vehicle(s) independently, and running Coordinator in parallel risks attributing
 * a BT-vehicle trip to the (non-BT) primary. [ARCH-MONITORING-002]
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
     * Resolves the strategy by inspecting **all** registered vehicles. Reads BT state
     * at call time so toggling Bluetooth between sessions flips ownership cleanly.
     */
    suspend fun resolve(): ParkingStrategy = strategyFor(vehicleRepository.observeVehicles().first())

    /**
     * Pure decision over an already-fetched fleet — single source of truth for both the suspend
     * [resolve] and reactive callers that combine the vehicle stream themselves (e.g.
     * [io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase]). Reads BT
     * adapter state at call time so toggling Bluetooth flips ownership cleanly. [DET-READY-001b]
     */
    fun strategyFor(vehicles: List<Vehicle>): ParkingStrategy {
        // BT wins first and independently of which vehicle is primary: a BT-paired
        // car in the fleet is detected by the receiver regardless of `isActive`.
        // SCOOTER/BIKE never count even if they somehow have a BT pairing.
        val hasAnyBtPaired = vehicles.any { it.isBtPairedAndParks() }
        if (hasAnyBtPaired && bluetoothScanner.isBluetoothEnabled()) {
            return ParkingStrategy.BLUETOOTH
        }

        // No BT path active. Coordinator monitors the primary; if the primary is a
        // type that never parks, suppress detection entirely. With no primary at
        // all, fall through to COORDINATOR (legacy "no vehicle" behaviour).
        val primary = vehicles.firstOrNull { it.isActive } ?: vehicles.firstOrNull()
        if (primary != null && primary.vehicleType in NON_PARKING_TYPES) {
            return ParkingStrategy.NONE
        }
        return ParkingStrategy.COORDINATOR
    }

    /**
     * Backwards-compatible boolean facade. Returns true only when the Coordinator
     * should run — false covers BOTH the BT strategy and the NONE case (caller can
     * call [resolve] directly to tell them apart for diagnostics).
     */
    suspend fun shouldUseCoordinator(): Boolean = resolve() == ParkingStrategy.COORDINATOR

    private fun Vehicle.isBtPairedAndParks(): Boolean =
        bluetoothDeviceId != null && vehicleType !in NON_PARKING_TYPES

    private companion object {
        val NON_PARKING_TYPES = setOf(VehicleType.SCOOTER, VehicleType.BIKE)
    }
}
