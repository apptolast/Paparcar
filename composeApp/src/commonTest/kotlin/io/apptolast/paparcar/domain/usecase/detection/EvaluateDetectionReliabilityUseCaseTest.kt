package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.DetectionReliabilityIssue
import io.apptolast.paparcar.domain.model.DetectionReliabilityLevel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-RELIABILITY-001] Full matrix of the pure reliability evaluator — one test per row of the
 * table documented on the use case, plus the issue-list contract (missing legs listed whenever
 * the level is not OPTIMAL; OPTIMAL never nags).
 */
class EvaluateDetectionReliabilityUseCaseTest {

    private val useCase = EvaluateDetectionReliabilityUseCase()

    @Test
    fun should_beOptimalWithoutIssues_when_btPairedAndExemptionGranted() {
        val report = useCase(hasBluetoothPairedVehicle = true, isBatteryExemptionGranted = true, isAggressiveOem = true)
        assertEquals(DetectionReliabilityLevel.OPTIMAL, report.level)
        assertEquals(emptyList(), report.issues)
    }

    @Test
    fun should_beOptimal_when_btPairedOnBenignOemWithoutExemption() {
        val report = useCase(hasBluetoothPairedVehicle = true, isBatteryExemptionGranted = false, isAggressiveOem = false)
        assertEquals(DetectionReliabilityLevel.OPTIMAL, report.level)
        assertEquals(emptyList(), report.issues)
    }

    @Test
    fun should_beGoodWithBatteryIssue_when_btPairedOnAggressiveOemWithoutExemption() {
        val report = useCase(hasBluetoothPairedVehicle = true, isBatteryExemptionGranted = false, isAggressiveOem = true)
        assertEquals(DetectionReliabilityLevel.GOOD, report.level)
        assertEquals(listOf(DetectionReliabilityIssue.BATTERY_OPTIMIZATION_ACTIVE), report.issues)
    }

    @Test
    fun should_beGoodWithBtIssue_when_exemptionGrantedWithoutBtPairing() {
        val report = useCase(hasBluetoothPairedVehicle = false, isBatteryExemptionGranted = true, isAggressiveOem = true)
        assertEquals(DetectionReliabilityLevel.GOOD, report.level)
        assertEquals(listOf(DetectionReliabilityIssue.NO_BLUETOOTH_PAIRING), report.issues)
    }

    @Test
    fun should_beGoodWithBothIssues_when_benignOemWithNoSetup() {
        val report = useCase(hasBluetoothPairedVehicle = false, isBatteryExemptionGranted = false, isAggressiveOem = false)
        assertEquals(DetectionReliabilityLevel.GOOD, report.level)
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
        assertEquals(
            listOf(
                DetectionReliabilityIssue.NO_BLUETOOTH_PAIRING,
                DetectionReliabilityIssue.BATTERY_OPTIMIZATION_ACTIVE,
            ),
            report.issues,
        )
    }
}
