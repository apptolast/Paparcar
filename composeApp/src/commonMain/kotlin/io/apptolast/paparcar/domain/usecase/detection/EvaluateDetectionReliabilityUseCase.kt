package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.DetectionReliabilityIssue
import io.apptolast.paparcar.domain.model.DetectionReliabilityLevel
import io.apptolast.paparcar.domain.model.DetectionReliabilityReport

/**
 * Pure evaluator of the detection-reliability level — the SINGLE source of truth that every
 * surface (Settings health, onboarding callout, future post-harm nag) reads. [DET-RELIABILITY-001]
 *
 * Inputs are the three independent legs of background survivability:
 *  - **BT pairing** (strong): the manifest ACL receiver revives a dead process, has no
 *    registration to lose and no documented Doze deferral — the most kill-resistant trigger.
 *  - **Battery exemption** (medium): lifts AOSP Doze/App-Standby deferral of our wake-ups. It
 *    does NOT bind the proprietary OEM killers — which is why it can never be promised as a fix,
 *    only as an improvement, and why the exemption stays OPTIONAL everywhere.
 *  - **OEM environment**: aggressive manufacturers (MIUI/ColorOS/EMUI…) freeze background
 *    execution by policy regardless of battery level.
 *
 * | BT | Exemption | Aggressive OEM | Level   |
 * |----|-----------|----------------|---------|
 * | ✅ | ✅        | —              | OPTIMAL |
 * | ✅ | ❌        | ❌             | OPTIMAL |
 * | ✅ | ❌        | ✅             | GOOD    |
 * | ❌ | ✅        | —              | GOOD    |
 * | ❌ | ❌        | ❌             | GOOD    |
 * | ❌ | ❌        | ✅             | REDUCED |
 *
 * Issues list the missing legs whenever the level is not OPTIMAL, so every fix surface renders
 * from the same report instead of re-deriving its own conditions.
 */
class EvaluateDetectionReliabilityUseCase {

    operator fun invoke(
        hasBluetoothPairedVehicle: Boolean,
        isBatteryExemptionGranted: Boolean,
        isAggressiveOem: Boolean,
    ): DetectionReliabilityReport {
        val level = when {
            hasBluetoothPairedVehicle && (isBatteryExemptionGranted || !isAggressiveOem) ->
                DetectionReliabilityLevel.OPTIMAL
            hasBluetoothPairedVehicle || isBatteryExemptionGranted || !isAggressiveOem ->
                DetectionReliabilityLevel.GOOD
            else -> DetectionReliabilityLevel.REDUCED
        }
        val issues = if (level == DetectionReliabilityLevel.OPTIMAL) {
            emptyList()
        } else {
            buildList {
                if (!hasBluetoothPairedVehicle) add(DetectionReliabilityIssue.NO_BLUETOOTH_PAIRING)
                if (!isBatteryExemptionGranted) add(DetectionReliabilityIssue.BATTERY_OPTIMIZATION_ACTIVE)
            }
        }
        return DetectionReliabilityReport(level = level, issues = issues)
    }
}
