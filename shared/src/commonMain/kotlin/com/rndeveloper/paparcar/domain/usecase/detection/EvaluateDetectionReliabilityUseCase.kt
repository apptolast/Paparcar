package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.model.DetectionReliabilityIssue
import com.rndeveloper.paparcar.domain.model.DetectionReliabilityLevel
import com.rndeveloper.paparcar.domain.model.DetectionReliabilityReport
import com.rndeveloper.paparcar.domain.model.DetectionTier
import com.rndeveloper.paparcar.domain.model.DeviceCapabilities

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
 * [IOS-F0-03] Each remedy leg is masked by [DeviceCapabilities]: a leg the platform does not
 * offer is N/A — never satisfied, never an issue, never a CTA. A device with NO missing
 * supported remedy is OPTIMAL by definition ("the strongest available setup for THIS device"),
 * which is how iOS (no BT strategy, no exemption concept, no OEM killers) reads OPTIMAL with an
 * empty issue list instead of nagging about impossible fixes.
 *
 * Android matrix (both capabilities supported) — unchanged:
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
 * Issues list the missing SUPPORTED legs whenever the level is not OPTIMAL, so every fix surface
 * renders from the same report instead of re-deriving its own conditions.
 *
 * The report also carries the product-facing [DetectionTier] (AUTOMATIC / ASSISTED_PLUS /
 * ASSISTED) — derived from BT pairing and the battery exemption only, independent of the OEM
 * axis. [DET-TIERS-001] Capability masking gives each platform its honest ceiling: without a BT
 * strategy AUTOMATIC is unreachable; without an exemption concept ASSISTED_PLUS is too (iOS
 * ceiling = ASSISTED; its with/without-Always sub-state is the port's F3, not this axis).
 */
class EvaluateDetectionReliabilityUseCase(
    private val capabilities: DeviceCapabilities,
) {

    operator fun invoke(
        hasBluetoothPairedVehicle: Boolean,
        isBatteryExemptionGranted: Boolean,
        isAggressiveOem: Boolean,
    ): DetectionReliabilityReport {
        // A leg the platform cannot offer is N/A: it neither satisfies nor complains.
        val btLegOk = capabilities.supportsBtStrategy && hasBluetoothPairedVehicle
        val btLegMissing = capabilities.supportsBtStrategy && !hasBluetoothPairedVehicle
        val exemptionLegOk = capabilities.supportsBatteryExemption && isBatteryExemptionGranted
        val exemptionLegMissing = capabilities.supportsBatteryExemption && !isBatteryExemptionGranted

        val level = when {
            // Nothing left worth asking for ON THIS DEVICE — OPTIMAL's literal definition.
            !btLegMissing && !exemptionLegMissing -> DetectionReliabilityLevel.OPTIMAL
            btLegOk && (exemptionLegOk || !isAggressiveOem) -> DetectionReliabilityLevel.OPTIMAL
            btLegOk || exemptionLegOk || !isAggressiveOem -> DetectionReliabilityLevel.GOOD
            else -> DetectionReliabilityLevel.REDUCED
        }
        val issues = if (level == DetectionReliabilityLevel.OPTIMAL) {
            emptyList()
        } else {
            buildList {
                if (btLegMissing) add(DetectionReliabilityIssue.NO_BLUETOOTH_PAIRING)
                if (exemptionLegMissing) add(DetectionReliabilityIssue.BATTERY_OPTIMIZATION_ACTIVE)
            }
        }
        // Tier is the product promise, on a different axis from level: BT pairing is the only jump
        // to AUTOMATIC; the battery exemption lifts ASSISTED to ASSISTED_PLUS. OEM aggressiveness
        // never changes the tier — only the level's sturdiness. Unsupported legs cap the ceiling:
        // a paired vehicle on a platform without the BT strategy still cannot promise AUTOMATIC.
        // [DET-TIERS-001][IOS-F0-03]
        val tier = when {
            btLegOk -> DetectionTier.AUTOMATIC
            exemptionLegOk -> DetectionTier.ASSISTED_PLUS
            else -> DetectionTier.ASSISTED
        }
        return DetectionReliabilityReport(level = level, tier = tier, issues = issues)
    }
}
