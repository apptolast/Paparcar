package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.model.DetectionReliabilityIssue
import com.rndeveloper.paparcar.domain.model.DetectionReliabilityLevel
import com.rndeveloper.paparcar.domain.model.DetectionTier
import com.rndeveloper.paparcar.domain.model.DeviceCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-RELIABILITY-001] Full matrix of the pure reliability evaluator — one test per row of the
 * table documented on the use case, plus the issue-list contract (missing legs listed whenever
 * the level is not OPTIMAL; OPTIMAL never nags). Each row also pins the [DetectionTier] the report
 * carries [DET-TIERS-001]; the dedicated tier tests below prove the tier axis is independent of
 * the OEM environment.
 */
class EvaluateDetectionReliabilityUseCaseTest {

    // [IOS-F0-03] The Android matrix runs with Android's capabilities — both remedies supported.
    private val useCase = EvaluateDetectionReliabilityUseCase(
        capabilities = DeviceCapabilities(supportsBtStrategy = true, supportsBatteryExemption = true),
    )

    @Test
    fun should_beOptimalWithoutIssues_when_btPairedAndExemptionGranted() {
        val report = useCase(hasBluetoothPairedVehicle = true, isBatteryExemptionGranted = true, isAggressiveOem = true)
        assertEquals(DetectionReliabilityLevel.OPTIMAL, report.level)
        assertEquals(DetectionTier.AUTOMATIC, report.tier)
        assertEquals(emptyList(), report.issues)
    }

    @Test
    fun should_beOptimal_when_btPairedOnBenignOemWithoutExemption() {
        val report = useCase(hasBluetoothPairedVehicle = true, isBatteryExemptionGranted = false, isAggressiveOem = false)
        assertEquals(DetectionReliabilityLevel.OPTIMAL, report.level)
        assertEquals(DetectionTier.AUTOMATIC, report.tier)
        assertEquals(emptyList(), report.issues)
    }

    @Test
    fun should_beGoodWithBatteryIssue_when_btPairedOnAggressiveOemWithoutExemption() {
        val report = useCase(hasBluetoothPairedVehicle = true, isBatteryExemptionGranted = false, isAggressiveOem = true)
        assertEquals(DetectionReliabilityLevel.GOOD, report.level)
        assertEquals(DetectionTier.AUTOMATIC, report.tier)
        assertEquals(listOf(DetectionReliabilityIssue.BATTERY_OPTIMIZATION_ACTIVE), report.issues)
    }

    @Test
    fun should_beGoodWithBtIssue_when_exemptionGrantedWithoutBtPairing() {
        val report = useCase(hasBluetoothPairedVehicle = false, isBatteryExemptionGranted = true, isAggressiveOem = true)
        assertEquals(DetectionReliabilityLevel.GOOD, report.level)
        assertEquals(DetectionTier.ASSISTED_PLUS, report.tier)
        assertEquals(listOf(DetectionReliabilityIssue.NO_BLUETOOTH_PAIRING), report.issues)
    }

    @Test
    fun should_beGoodWithBothIssues_when_benignOemWithNoSetup() {
        val report = useCase(hasBluetoothPairedVehicle = false, isBatteryExemptionGranted = false, isAggressiveOem = false)
        assertEquals(DetectionReliabilityLevel.GOOD, report.level)
        assertEquals(DetectionTier.ASSISTED, report.tier)
        assertEquals(
            listOf(
                DetectionReliabilityIssue.NO_BLUETOOTH_PAIRING,
                DetectionReliabilityIssue.BATTERY_OPTIMIZATION_ACTIVE,
            ),
            report.issues,
        )
    }

    @Test
    fun should_beReducedWithBothIssues_when_aggressiveOemWithNoSetup() {
        val report = useCase(hasBluetoothPairedVehicle = false, isBatteryExemptionGranted = false, isAggressiveOem = true)
        assertEquals(DetectionReliabilityLevel.REDUCED, report.level)
        assertEquals(DetectionTier.ASSISTED, report.tier)
        assertEquals(
            listOf(
                DetectionReliabilityIssue.NO_BLUETOOTH_PAIRING,
                DetectionReliabilityIssue.BATTERY_OPTIMIZATION_ACTIVE,
            ),
            report.issues,
        )
    }

    // ── Tier axis [DET-TIERS-001]: BT is the only jump to AUTOMATIC; the exemption lifts ASSISTED
    // to ASSISTED_PLUS; the OEM environment never moves the tier (only the level's sturdiness). ──

    @Test
    fun should_beAutomatic_whenBtPaired_regardlessOfExemptionOrOem() {
        for (exempt in listOf(true, false)) {
            for (oem in listOf(true, false)) {
                val report = useCase(hasBluetoothPairedVehicle = true, isBatteryExemptionGranted = exempt, isAggressiveOem = oem)
                assertEquals(DetectionTier.AUTOMATIC, report.tier, "BT paired must always be AUTOMATIC (exempt=$exempt, oem=$oem)")
            }
        }
    }

    @Test
    fun should_beAssistedPlus_whenNoBtButExempt_regardlessOfOem() {
        for (oem in listOf(true, false)) {
            val report = useCase(hasBluetoothPairedVehicle = false, isBatteryExemptionGranted = true, isAggressiveOem = oem)
            assertEquals(DetectionTier.ASSISTED_PLUS, report.tier, "No BT + exemption must be ASSISTED_PLUS (oem=$oem)")
        }
    }

    @Test
    fun should_beAssisted_whenNoBtAndNoExempt_regardlessOfOem() {
        for (oem in listOf(true, false)) {
            val report = useCase(hasBluetoothPairedVehicle = false, isBatteryExemptionGranted = false, isAggressiveOem = oem)
            assertEquals(DetectionTier.ASSISTED, report.tier, "No BT + no exemption must be ASSISTED (oem=$oem)")
        }
    }

    // ── [IOS-F0-03] Capability masking: a leg the platform cannot offer is N/A — never satisfied,
    // never an issue, never a CTA. iOS (no BT strategy, no exemption concept) reads OPTIMAL with
    // an empty issue list and a hard ASSISTED tier ceiling. ──────────────────────────────────────

    private val iosUseCase = EvaluateDetectionReliabilityUseCase(
        capabilities = DeviceCapabilities(supportsBtStrategy = false, supportsBatteryExemption = false),
    )

    @Test
    fun should_beOptimalWithoutIssues_when_platformSupportsNoRemedies() {
        // The strongest available setup for THIS device: nothing left worth asking for — the
        // report must never nag about fixes the user cannot complete on iOS.
        val report = iosUseCase(hasBluetoothPairedVehicle = false, isBatteryExemptionGranted = false, isAggressiveOem = false)
        assertEquals(DetectionReliabilityLevel.OPTIMAL, report.level)
        assertEquals(emptyList(), report.issues)
    }

    @Test
    fun should_capTierAtAssisted_when_platformSupportsNoRemedies_evenIfInputsClaimOtherwise() {
        // Defensive: even if a stale/foreign input reports a paired vehicle or an exemption,
        // an unsupported leg cannot promise a tier the platform cannot deliver.
        for (paired in listOf(true, false)) {
            for (exempt in listOf(true, false)) {
                val report = iosUseCase(hasBluetoothPairedVehicle = paired, isBatteryExemptionGranted = exempt, isAggressiveOem = false)
                assertEquals(
                    DetectionTier.ASSISTED, report.tier,
                    "iOS ceiling must be ASSISTED (paired=$paired, exempt=$exempt)",
                )
            }
        }
    }

    @Test
    fun should_notSurfaceIssueForUnsupportedLeg_when_onlyBtIsUnsupported() {
        // Hypothetical single-capability platform: the supported battery leg still degrades and
        // nags; the unsupported BT leg stays silent. Proves the masking is per leg, not global.
        val noBtPlatform = EvaluateDetectionReliabilityUseCase(
            capabilities = DeviceCapabilities(supportsBtStrategy = false, supportsBatteryExemption = true),
        )
        val report = noBtPlatform(hasBluetoothPairedVehicle = false, isBatteryExemptionGranted = false, isAggressiveOem = true)
        assertEquals(DetectionReliabilityLevel.REDUCED, report.level)
        assertEquals(listOf(DetectionReliabilityIssue.BATTERY_OPTIMIZATION_ACTIVE), report.issues)
        assertEquals(DetectionTier.ASSISTED, report.tier)
    }
}
