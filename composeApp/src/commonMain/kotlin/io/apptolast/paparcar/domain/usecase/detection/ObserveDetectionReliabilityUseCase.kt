package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.model.DetectionReliabilityReport
import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Reactive wrapper of [EvaluateDetectionReliabilityUseCase]: binds the evaluator's three inputs
 * to their live sources so every surface observes the SAME report. [DET-RELIABILITY-001]
 *
 * - BT pairing: the vehicle stream through [ParkingStrategyResolver.hasBtPairedParkingVehicle]
 *   (the setup fact — deliberately not gated on the adapter's momentary on/off state).
 * - Battery exemption: [PermissionManager.permissionState] (refreshed on resume by the screens).
 * - OEM environment: static per device — aggressive when the manufacturer ships either
 *   proprietary gate (autostart whitelist / Hans-style freezer).
 */
class ObserveDetectionReliabilityUseCase(
    private val vehicleRepository: VehicleRepository,
    private val permissionManager: PermissionManager,
    private val oemBackgroundReliabilityManager: OemBackgroundReliabilityManager,
    private val strategyResolver: ParkingStrategyResolver,
    private val evaluateDetectionReliability: EvaluateDetectionReliabilityUseCase,
) {
    operator fun invoke(): Flow<DetectionReliabilityReport> = combine(
        vehicleRepository.observeVehicles(),
        permissionManager.permissionState,
    ) { vehicles, permissions ->
        evaluateDetectionReliability(
            hasBluetoothPairedVehicle = strategyResolver.hasBtPairedParkingVehicle(vehicles),
            isBatteryExemptionGranted = permissions.isBatteryOptimizationExempt,
            isAggressiveOem = oemBackgroundReliabilityManager.requiresAutostartWhitelist ||
                oemBackgroundReliabilityManager.requiresOemBatterySettings,
        )
    }
}
