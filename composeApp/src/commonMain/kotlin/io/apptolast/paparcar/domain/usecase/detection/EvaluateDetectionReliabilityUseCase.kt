package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.DetectionReliabilityIssue
import io.apptolast.paparcar.domain.model.DetectionReliabilityLevel
import io.apptolast.paparcar.domain.model.DetectionReliabilityReport
import io.apptolast.paparcar.domain.model.DetectionTier

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
 *
 * The report also carries the product-facing [DetectionTier] (AUTOMATIC / ASSISTED_PLUS /
 * ASSISTED) — derived from BT pairing and the battery exemption only, independent of the OEM
 * axis. [DET-TIERS-001]
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
        // Tier is the product promise, on a different axis from level: BT pairing is the only jump
        // to AUTOMATIC; the battery exemption lifts ASSISTED to ASSISTED_PLUS. OEM aggressiveness
        // never changes the tier — only the level's sturdiness. [DET-TIERS-001]
        val tier = when {
            hasBluetoothPairedVehicle -> DetectionTier.AUTOMATIC
            isBatteryExemptionGranted -> DetectionTier.ASSISTED_PLUS
            else -> DetectionTier.ASSISTED
        }
        return DetectionReliabilityReport(level = level, tier = tier, issues = issues)
    }
}
